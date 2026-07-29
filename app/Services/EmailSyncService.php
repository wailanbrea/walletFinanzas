<?php

namespace App\Services;

use App\Jobs\SyncEmailConnectionJob;
use App\Models\EmailConnection;
use App\Models\EmailMessage;
use App\Models\EmailSyncRun;
use App\Models\FinancialTransactionCandidate;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;
use Throwable;

class EmailSyncService
{
    public function __construct(
        private readonly EmailOAuthService $oauth,
        private readonly FinancialEmailClassifier $classifier,
        private readonly UsdDopExchangeRateService $exchangeRates,
        private readonly EmailClassificationLearningService $learning,
    ) {}

    /**
     * Valida y encola la sincronización, devolviendo el EmailSyncRun (estado 'queued')
     * para que el cliente sondee el resultado. Falla rápido (409) si ya hay una en curso.
     */
    public function queue(User $user, string $provider): EmailSyncRun
    {
        abort_unless($this->oauth->supports($provider), 404);
        $connection = $user->emailConnections()
            ->where('provider', $provider)
            ->where('status', 'connected')
            ->first();
        abort_unless($connection, 409, 'Conecta '.EmailOAuthService::PROVIDERS[$provider]['display_name'].' antes de sincronizar.');

        // Fail-fast si ya hay una sync en curso (el lock definitivo lo toma el job).
        $probe = Cache::lock('email-sync:'.$connection->id, 5);
        abort_unless($probe->get(), 409, 'Ya hay una sincronización en curso para esta cuenta.');
        $probe->release();

        $run = EmailSyncRun::create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'status' => 'queued',
            'started_at' => now(),
        ]);

        SyncEmailConnectionJob::dispatch($run->id);

        return $run->fresh();
    }

    /** Ejecutado por el job: toma el lock y sincroniza la conexión del run. */
    public function process(int $syncRunId): void
    {
        $run = EmailSyncRun::with('connection')->find($syncRunId);
        if (! $run || in_array($run->status, ['completed', 'failed'], true)) {
            return;
        }

        $connection = $run->connection;
        $user = $connection ? User::find($connection->user_id) : null;
        if (! $connection || ! $user) {
            $run->update(['status' => 'failed', 'error_code' => 'connection_missing', 'finished_at' => now()]);

            return;
        }

        $lock = Cache::lock('email-sync:'.$connection->id, 300);
        if (! $lock->get()) {
            $run->update(['status' => 'failed', 'error_code' => 'locked', 'finished_at' => now()]);

            return;
        }

        try {
            $this->syncConnection($user, $connection->provider, $connection, $run);
        } finally {
            $lock->release();
        }
    }

    /** @return array<string, int|string> */
    private function syncConnection(User $user, string $provider, EmailConnection $connection, EmailSyncRun $run): array
    {
        $run->update(['status' => 'running', 'started_at' => now()]);

        try {
            $token = $this->oauth->accessTokenFor($connection);
            $providerMessages = $provider === 'gmail'
                ? $this->gmailMessages($token)
                : $this->microsoftMessages($token);
            $backfilledConversions = $this->backfillUsdConversions($user);
            $createdMessages = 0;
            $createdCandidates = 0;
            $classificationRules = $this->learning->rulesFor($user);

            foreach ($providerMessages as $providerMessage) {
                $message = EmailMessage::firstOrCreate([
                    'email_connection_id' => $connection->id,
                    'provider_message_id' => $providerMessage['provider_message_id'],
                ], [
                    'user_id' => $user->id,
                    'internet_message_id' => $providerMessage['internet_message_id'],
                    'sender_email' => $providerMessage['sender_email'],
                    'sender_name' => $providerMessage['sender_name'],
                    'subject' => Str::limit($providerMessage['subject'], 512, ''),
                    'received_at' => $providerMessage['received_at'],
                    'body_excerpt' => Str::limit($this->plainText($providerMessage['body']), 5000, ''),
                    'content_hash' => hash('sha256', $providerMessage['subject'].'\n'.$providerMessage['body']),
                    'status' => 'processed',
                ]);

                if (! $message->wasRecentlyCreated && $message->candidate()->exists()) {
                    continue;
                }
                if ($message->wasRecentlyCreated) {
                    $createdMessages++;
                }

                $classification = $this->classifier->classify($providerMessage);
                $classification = $this->learning->apply($classificationRules, [
                    'provider' => $provider,
                    'sender_email' => $providerMessage['sender_email'],
                    'subject' => $providerMessage['subject'],
                ], $classification);
                if (! $classification) {
                    continue;
                }

                if ($classification['currency'] === 'USD') {
                    try {
                        $classification = [
                            ...$classification,
                            ...$this->exchangeRates->convertMinor(
                                $classification['amount'],
                                $classification['occurred_at'],
                            ),
                        ];
                    } catch (Throwable $exception) {
                        report($exception);
                        $classification['reasons'][] = 'fx_unavailable';
                    }
                }

                $message->candidate()->create([
                    'user_id' => $user->id,
                    'provider' => $provider,
                    ...$classification,
                ]);
                $createdCandidates++;
            }

            $result = [
                'sync_run_id' => $run->id,
                'messages_discovered' => count($providerMessages),
                'messages_created' => $createdMessages,
                'candidates_created' => $createdCandidates,
                'conversions_backfilled' => $backfilledConversions,
            ];
            $run->update([...$result, 'status' => 'completed', 'finished_at' => now()]);
            $connection->update(['last_sync_at' => now(), 'last_sync_status' => 'completed']);

            return $result;
        } catch (Throwable $exception) {
            $run->update([
                'status' => 'failed',
                'error_code' => class_basename($exception),
                'finished_at' => now(),
            ]);
            $connection->update(['last_sync_status' => 'failed']);
            throw $exception;
        }
    }

    private function backfillUsdConversions(User $user): int
    {
        // Gate barato: si no hay ningún candidato USD pendiente de conversión, no
        // hacemos nada (evita trabajo y llamadas FX en la mayoría de syncs).
        $pending = FinancialTransactionCandidate::query()
            ->where('user_id', $user->id)
            ->where('currency', 'USD')
            ->where(function ($query): void {
                $query->whereNull('converted_amount')
                    ->orWhereNull('exchange_rate_source')
                    ->orWhere('exchange_rate_source', '!=', UsdDopExchangeRateService::SOURCE);
            });

        if (! $pending->exists()) {
            return 0;
        }

        $backfilled = 0;
        $pending
            ->orderBy('occurred_at')
            ->limit(100)
            ->get()
            ->each(function (FinancialTransactionCandidate $candidate) use (&$backfilled): void {
                try {
                    $conversion = $this->exchangeRates->convertMinor(
                        $candidate->amount,
                        $candidate->occurred_at,
                    );
                    $updated = FinancialTransactionCandidate::query()
                        ->whereKey($candidate->id)
                        ->where(function ($query): void {
                            $query->whereNull('converted_amount')
                                ->orWhereNull('exchange_rate_source')
                                ->orWhere('exchange_rate_source', '!=', UsdDopExchangeRateService::SOURCE);
                        })
                        ->update($conversion);
                    $backfilled += $updated;
                } catch (Throwable $exception) {
                    report($exception);
                }
            });

        return $backfilled;
    }

    /** @return list<array<string, mixed>> */
    private function gmailMessages(string $token): array
    {
        $client = $this->client($token);
        $listed = $client->get('https://gmail.googleapis.com/gmail/v1/users/me/messages', [
            'maxResults' => 50,
            'q' => 'newer_than:30d',
        ])->throw()->json('messages', []);

        return collect($listed)->map(function (array $reference) use ($client): array {
            $message = $client->get('https://gmail.googleapis.com/gmail/v1/users/me/messages/'.$reference['id'], [
                'format' => 'full',
            ])->throw()->json();
            $headers = collect(data_get($message, 'payload.headers', []))
                ->mapWithKeys(fn (array $header): array => [Str::lower($header['name']) => $header['value']]);
            [$senderName, $senderEmail] = $this->parseSender($headers->get('from'));
            $milliseconds = (int) ($message['internalDate'] ?? now()->valueOf());

            return [
                'provider_message_id' => $message['id'],
                'internet_message_id' => $headers->get('message-id'),
                'sender_email' => $senderEmail,
                'sender_name' => $senderName,
                'subject' => $headers->get('subject', ''),
                'received_at' => CarbonImmutable::createFromTimestampUTC(intdiv($milliseconds, 1000)),
                'body' => $this->gmailBody($message['payload'] ?? []),
            ];
        })->values()->all();
    }

    /** @return list<array<string, mixed>> */
    private function microsoftMessages(string $token): array
    {
        $messages = $this->client($token)->get('https://graph.microsoft.com/v1.0/me/messages', [
            '$top' => 50,
            '$select' => 'id,internetMessageId,subject,from,receivedDateTime,body',
            '$orderby' => 'receivedDateTime desc',
            '$filter' => 'receivedDateTime ge '.now()->subDays(30)->utc()->format('Y-m-d\TH:i:s\Z'),
        ])->throw()->json('value', []);

        return collect($messages)->map(function (array $message): array {
            return [
                'provider_message_id' => $message['id'],
                'internet_message_id' => $message['internetMessageId'] ?? null,
                'sender_email' => data_get($message, 'from.emailAddress.address'),
                'sender_name' => data_get($message, 'from.emailAddress.name'),
                'subject' => $message['subject'] ?? '',
                'received_at' => CarbonImmutable::parse($message['receivedDateTime']),
                'body' => data_get($message, 'body.content', ''),
            ];
        })->values()->all();
    }

    private function client(string $token): PendingRequest
    {
        return Http::withToken($token)->acceptJson()->timeout(20)->retry(2, 250);
    }

    /** @return array{0:?string,1:?string} */
    private function parseSender(?string $from): array
    {
        if (! $from) {
            return [null, null];
        }
        if (preg_match('/^\s*(.*?)\s*<([^>]+)>\s*$/', $from, $match)) {
            return [trim($match[1], " \t\n\r\0\x0B\""), trim($match[2])];
        }

        return [null, filter_var(trim($from), FILTER_VALIDATE_EMAIL) ? trim($from) : null];
    }

    private function gmailBody(array $payload): string
    {
        $mime = Str::lower($payload['mimeType'] ?? '');
        $data = data_get($payload, 'body.data');
        if ($data && in_array($mime, ['text/plain', 'text/html'], true)) {
            return $this->decodeGmailBase64($data);
        }

        $plain = '';
        $html = '';
        foreach ($payload['parts'] ?? [] as $part) {
            $value = $this->gmailBody($part);
            if (Str::lower($part['mimeType'] ?? '') === 'text/plain' && $value !== '') {
                $plain .= ' '.$value;
            } elseif ($value !== '') {
                $html .= ' '.$value;
            }
        }

        return trim($plain !== '' ? $plain : $html);
    }

    private function decodeGmailBase64(string $data): string
    {
        $normalized = strtr($data, '-_', '+/');
        $normalized .= str_repeat('=', (4 - strlen($normalized) % 4) % 4);

        return base64_decode($normalized, true) ?: '';
    }

    private function plainText(string $value): string
    {
        return trim((string) preg_replace('/\s+/u', ' ', html_entity_decode(strip_tags($value), ENT_QUOTES | ENT_HTML5, 'UTF-8')));
    }
}
