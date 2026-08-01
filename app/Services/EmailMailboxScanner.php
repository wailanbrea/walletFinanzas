<?php

namespace App\Services;

use App\Models\EmailCandidate;
use App\Models\EmailCategorizationRule;
use App\Models\EmailConnection;
use App\Models\EmailMailbox;
use App\Models\ProviderMessage;
use Carbon\CarbonImmutable;
use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Facades\Http;
use RuntimeException;
use Throwable;

class EmailMailboxScanner
{
    public function __construct(
        private EmailOAuthService $oauth,
        private FinancialEmailExtractor $extractor,
        private UsdDopExchangeRateService $exchangeRates,
        private DuplicateEmailCandidateDetector $duplicates,
        private EmailBodyText $bodyText,
    ) {}

    public function scan(EmailConnection $connection, ?CarbonImmutable $syncFrom = null): array
    {
        $token = $this->oauth->accessToken($connection);
        $mailbox = EmailMailbox::forConnection($connection);
        $syncFrom = ($syncFrom ?? $mailbox->sync_from_at ?? CarbonImmutable::now('UTC')->subDays(90)->startOfDay())->utc();
        $backfill = $mailbox->backfill_completed_at === null;
        if ($backfill) {
            $from = $syncFrom;
            $until = $mailbox->backfill_before_at ?? CarbonImmutable::now('UTC');
            $cursor = $mailbox->backfill_cursor;
            if (! $mailbox->backfill_before_at) {
                $mailbox->update(['backfill_before_at' => $until]);
            }
        } elseif ($mailbox->incremental_cursor) {
            $from = $mailbox->incremental_from_at;
            $until = $mailbox->incremental_before_at;
            $cursor = $mailbox->incremental_cursor;
        } else {
            $from = $syncFrom;
            if ($connection->last_synced_at && $connection->last_synced_at->greaterThan($from)) {
                $from = $connection->last_synced_at;
            }
            $until = CarbonImmutable::now('UTC');
            $cursor = null;
        }
        $batch = $connection->provider === 'gmail'
            ? $this->gmail($token, $from, $until, $cursor)
            : $this->microsoft($token, $from, $until, $cursor);
        $messages = array_values(array_filter($batch['messages'], function (array $item) use ($from, $until): bool {
            $occurredAt = $item['occurred_at'];

            return $occurredAt->greaterThanOrEqualTo($from) && $occurredAt->lessThanOrEqualTo($until);
        }));
        $created = 0;
        $candidates = 0;
        $decidedIds = $mailbox->decisions()
            ->whereIn('provider_message_id', array_column($messages, 'id'))
            ->pluck('provider_message_id')
            ->flip();

        // Rotate reviewed candidates so bounded cleanup batches eventually cover every pending item.
        EmailCandidate::query()
            ->where('user_id', $connection->user_id)
            ->where('provider', $connection->provider)
            ->where('status', 'pending')
            ->with('message')
            ->oldest('updated_at')
            ->orderBy('id')
            ->limit($this->messageLimit())
            ->get()
            ->each(function (EmailCandidate $candidate): void {
                if ($candidate->message && $this->extractor->isDefiniteNonTransaction($candidate->message->subject, $candidate->message->snippet)) {
                    EmailCandidate::query()->whereKey($candidate->id)->where('status', 'pending')->delete();

                    return;
                }

                EmailCandidate::query()->whereKey($candidate->id)->where('status', 'pending')->update(['updated_at' => now()->addSecond()]);
            });

        foreach ($messages as $item) {
            if ($decidedIds->has($item['id'])) {
                continue;
            }

            $message = ProviderMessage::query()->firstOrCreate(
                ['user_id' => $connection->user_id, 'provider' => $connection->provider, 'provider_message_id' => $item['id']],
                [
                    'email_connection_id' => $connection->id,
                    'subject' => $item['subject'],
                    'snippet' => $item['snippet'],
                    'occurred_at' => $item['occurred_at'],
                ]
            );
            $created += (int) $message->wasRecentlyCreated;
            $existingCandidate = $message->candidate()->first();
            $candidate = $this->extractor->extract($message->subject, $message->snippet, $message->occurred_at);
            if (! $candidate) {
                if ($existingCandidate && $this->extractor->isDefiniteNonTransaction($message->subject, $message->snippet)) {
                    EmailCandidate::query()->whereKey($existingCandidate->id)
                        ->where('status', 'pending')
                        ->delete();
                }

                continue;
            }
            if ($candidate['merchant']) {
                $learnedCategory = EmailCategorizationRule::query()
                    ->where('user_id', $connection->user_id)
                    ->where('merchant', $candidate['merchant'])
                    ->value('category');
                $candidate['category_suggestion'] = $learnedCategory ?: $candidate['category_suggestion'];
            }
            $candidate = $this->withConversion($candidate);
            if ($existingCandidate) {
                EmailCandidate::query()->whereKey($existingCandidate->id)
                    ->where('status', 'pending')->update($candidate);
            } else {
                EmailCandidate::query()->create($candidate + [
                    'user_id' => $connection->user_id,
                    'status' => 'pending',
                    'provider_message_id' => $message->id,
                    'provider' => $connection->provider,
                ]);
                $candidates++;
            }
        }
        $connectionUpdate = ['status' => 'connected'];
        $nextCursor = $batch['next_cursor'];
        if ($nextCursor && $nextCursor === $cursor) {
            throw new RuntimeException('email_provider_cursor_stalled');
        }
        if ($backfill) {
            if ($nextCursor) {
                $mailbox->update(['backfill_cursor' => $nextCursor]);
            } else {
                $mailbox->update(['backfill_cursor' => null, 'backfill_completed_at' => now()]);
                $connectionUpdate['last_synced_at'] = $until;
            }
        } elseif ($nextCursor) {
            $mailbox->update([
                'incremental_from_at' => $from,
                'incremental_before_at' => $until,
                'incremental_cursor' => $nextCursor,
            ]);
        } else {
            $mailbox->update([
                'incremental_from_at' => null,
                'incremental_before_at' => null,
                'incremental_cursor' => null,
            ]);
            $connectionUpdate['last_synced_at'] = $until;
        }
        $connection->update($connectionUpdate);

        // Al final del barrido, no por cada mensaje: el gemelo puede haber llegado por
        // el otro buzon y solo se le puede emparejar cuando ya esta guardado.
        $duplicates = $this->duplicates->reconcile($connection->user);

        return [
            'messages_discovered' => count($messages),
            'messages_created' => $created,
            'candidates_created' => $candidates,
            'duplicates_marked' => $duplicates,
            'has_more' => (bool) $nextCursor,
        ];
    }

    /**
     * Adjunta la conversion a DOP si el cargo no viene ya en pesos.
     *
     * Si la tasa no esta disponible el candidato se guarda igual, sin conversion: es
     * mejor mostrar el gasto y que no se pueda clasificar todavia que perderlo.
     *
     * @param  array<string, mixed>  $candidate
     * @return array<string, mixed>
     */
    private function withConversion(array $candidate): array
    {
        $currency = $candidate['currency'] ?? null;
        if ($currency === null || $currency === 'DOP') {
            return $candidate;
        }
        $conversion = $this->exchangeRates->convertFrom(
            $currency,
            (int) $candidate['amount'],
            CarbonImmutable::parse($candidate['occurred_at']),
        );

        return $conversion ? $candidate + $conversion : $candidate;
    }

    private function gmail(
        string $token,
        CarbonImmutable $from,
        CarbonImmutable $until,
        ?string $cursor = null,
    ): array {
        $limit = $this->messageLimit();
        $messages = [];
        $pageToken = $cursor;
        do {
            $query = [
                'maxResults' => min(50, $limit - count($messages)),
                'q' => sprintf('after:%d before:%d', $from->subSecond()->getTimestamp(), $until->addSecond()->getTimestamp()),
            ];
            if ($pageToken) {
                $query['pageToken'] = $pageToken;
            }
            $page = $this->http($token)->get('https://gmail.googleapis.com/gmail/v1/users/me/messages', $query)->throw()->json();
            foreach ($page['messages'] ?? [] as $reference) {
                if (count($messages) >= $limit || ! is_string($reference['id'] ?? null)) {
                    break;
                }
                $detail = $this->http($token)->get('https://gmail.googleapis.com/gmail/v1/users/me/messages/'.rawurlencode($reference['id']), [
                    // full y no metadata: metadata no devuelve el cuerpo, y sin cuerpo el
                    // comercio y la tarjeta de muchos avisos son ilegibles.
                    'format' => 'full',
                ])->throw()->json();
                $headers = collect($detail['payload']['headers'] ?? [])->mapWithKeys(fn (array $header) => [strtolower((string) ($header['name'] ?? '')) => $header['value'] ?? null]);
                $messages[] = [
                    'id' => (string) $reference['id'],
                    'subject' => $this->limited($headers['subject'] ?? null, 500),
                    // Si el cuerpo no se pudo leer, la vista previa sigue sirviendo.
                    'snippet' => $this->bodyText->fromGmailPayload($detail['payload'] ?? null)
                        ?? $this->limited($detail['snippet'] ?? null, 1000),
                    'occurred_at' => $this->gmailDate($detail['internalDate'] ?? null, $headers['date'] ?? null),
                ];
            }
            $pageToken = is_string($page['nextPageToken'] ?? null) ? $page['nextPageToken'] : null;
        } while ($pageToken && count($messages) < $limit);

        return ['messages' => $messages, 'next_cursor' => $pageToken];
    }

    private function microsoft(
        string $token,
        CarbonImmutable $from,
        CarbonImmutable $until,
        ?string $cursor = null,
    ): array {
        $limit = $this->messageLimit();
        $messages = [];
        $url = $cursor && str_starts_with($cursor, 'https://graph.microsoft.com/v1.0/')
            ? $cursor
            : 'https://graph.microsoft.com/v1.0/me/messages';
        // Se pide body y no solo bodyPreview: la vista previa corta el aviso justo antes
        // de la tabla con el comercio y la tarjeta.
        $query = $cursor ? [] : [
            '$top' => min(50, $limit),
            '$select' => 'id,subject,bodyPreview,body,receivedDateTime',
            '$filter' => sprintf('receivedDateTime ge %s and receivedDateTime le %s', $from->toISOString(), $until->toISOString()),
            '$orderby' => 'receivedDateTime desc',
        ];
        $page = $this->http($token)->get($url, $query)->throw()->json();
        $items = array_values($page['value'] ?? []);
        if (count($items) > $limit) {
            throw new RuntimeException('email_provider_page_exceeds_limit');
        }
        foreach ($items as $item) {
            if (! is_string($item['id'] ?? null)) {
                continue;
            }
            $messages[] = [
                'id' => $item['id'],
                'subject' => $this->limited($item['subject'] ?? null, 500),
                'snippet' => $this->bodyText->fromGraphBody($item['body'] ?? null)
                    ?? $this->limited($item['bodyPreview'] ?? null, 1000),
                'occurred_at' => $this->date($item['receivedDateTime'] ?? null),
            ];
        }
        $next = $page['@odata.nextLink'] ?? null;
        $url = is_string($next) && str_starts_with($next, 'https://graph.microsoft.com/v1.0/') ? $next : null;

        return ['messages' => $messages, 'next_cursor' => $url];
    }

    /**
     * Vuelve a bajar el cuerpo de un mensaje ya guardado y lo devuelve como texto.
     *
     * Los mensajes historicos se guardaron cuando solo se pedia la vista previa, y el
     * dedupe por provider_message_id impide que el barrido normal los vuelva a mirar. Sin
     * esto habria que esperar correos nuevos para saber si traer el cuerpo sirvio de algo.
     *
     * Devuelve null si el proveedor ya no tiene el mensaje o no hay cuerpo legible.
     */
    public function refetchBodyText(EmailConnection $connection, string $providerMessageId): ?string
    {
        $token = $this->oauth->accessToken($connection);

        if ($connection->provider === 'gmail') {
            $response = $this->http($token)->get(
                'https://gmail.googleapis.com/gmail/v1/users/me/messages/'.rawurlencode($providerMessageId),
                ['format' => 'full']
            );

            return $response->successful()
                ? $this->bodyText->fromGmailPayload($response->json('payload'))
                : null;
        }

        $response = $this->http($token)->get(
            'https://graph.microsoft.com/v1.0/me/messages/'.rawurlencode($providerMessageId),
            ['$select' => 'id,body']
        );

        return $response->successful()
            ? $this->bodyText->fromGraphBody($response->json('body'))
            : null;
    }

    private function http(string $token): PendingRequest
    {
        return Http::acceptJson()->withToken($token)->timeout(20)->retry(2, 200, throw: false);
    }

    private function gmailDate(mixed $internalDate, mixed $headerDate): CarbonImmutable
    {
        if ((is_string($internalDate) || is_int($internalDate))
            && ctype_digit((string) $internalDate)) {
            return CarbonImmutable::createFromTimestampUTC(((int) $internalDate) / 1000);
        }

        return $this->date($headerDate);
    }

    private function date(mixed $value): CarbonImmutable
    {
        if (! is_string($value) || trim($value) === '') {
            throw new RuntimeException('email_provider_date_invalid');
        }

        try {
            return CarbonImmutable::parse($value);
        } catch (Throwable $exception) {
            throw new RuntimeException('email_provider_date_invalid', previous: $exception);
        }
    }

    private function limited(mixed $value, int $length): ?string
    {
        return is_string($value) ? mb_substr($value, 0, $length) : null;
    }

    private function messageLimit(): int
    {
        return max(1, min(200, (int) config('email_sync.max_messages_per_run', 100)));
    }
}
