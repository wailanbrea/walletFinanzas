<?php

namespace App\Services;

use App\Models\EmailCandidate;
use App\Models\EmailConnection;
use App\Models\ProviderMessage;
use Carbon\CarbonImmutable;
use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Facades\Http;
use Throwable;

class EmailMailboxScanner
{
    public function __construct(private EmailOAuthService $oauth, private FinancialEmailExtractor $extractor) {}

    public function scan(EmailConnection $connection): array
    {
        $token = $this->oauth->accessToken($connection);
        $messages = $connection->provider === 'gmail' ? $this->gmail($token) : $this->microsoft($token);
        $created = 0;
        $candidates = 0;

        foreach ($messages as $item) {
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
            if (! $message->candidate()->exists()) {
                $candidate = $this->extractor->extract($message->subject, $message->snippet, $message->occurred_at);
                if ($candidate) {
                    EmailCandidate::query()->create($candidate + [
                        'user_id' => $connection->user_id,
                        'provider_message_id' => $message->id,
                        'provider' => $connection->provider,
                    ]);
                    $candidates++;
                }
            }
        }
        $connection->update(['last_synced_at' => now(), 'status' => 'connected']);

        return ['messages_discovered' => count($messages), 'messages_created' => $created, 'candidates_created' => $candidates];
    }

    private function gmail(string $token): array
    {
        $limit = $this->messageLimit();
        $messages = [];
        $pageToken = null;
        do {
            $query = ['maxResults' => min(50, $limit - count($messages)), 'q' => 'newer_than:90d'];
            if ($pageToken) {
                $query['pageToken'] = $pageToken;
            }
            $page = $this->http($token)->get('https://gmail.googleapis.com/gmail/v1/users/me/messages', $query)->throw()->json();
            foreach ($page['messages'] ?? [] as $reference) {
                if (count($messages) >= $limit || ! is_string($reference['id'] ?? null)) {
                    break;
                }
                $detail = $this->http($token)->get('https://gmail.googleapis.com/gmail/v1/users/me/messages/'.rawurlencode($reference['id']), [
                    'format' => 'metadata',
                    'metadataHeaders' => ['Subject', 'Date'],
                ])->throw()->json();
                $headers = collect($detail['payload']['headers'] ?? [])->mapWithKeys(fn (array $header) => [strtolower((string) ($header['name'] ?? '')) => $header['value'] ?? null]);
                $messages[] = [
                    'id' => (string) $reference['id'],
                    'subject' => $this->limited($headers['subject'] ?? null, 500),
                    'snippet' => $this->limited($detail['snippet'] ?? null, 1000),
                    'occurred_at' => $this->date($headers['date'] ?? null),
                ];
            }
            $pageToken = is_string($page['nextPageToken'] ?? null) ? $page['nextPageToken'] : null;
        } while ($pageToken && count($messages) < $limit);

        return $messages;
    }

    private function microsoft(string $token): array
    {
        $limit = $this->messageLimit();
        $messages = [];
        $url = 'https://graph.microsoft.com/v1.0/me/messages';
        $query = ['$top' => min(50, $limit), '$select' => 'id,subject,bodyPreview,receivedDateTime', '$orderby' => 'receivedDateTime desc'];
        do {
            $page = $this->http($token)->get($url, $query)->throw()->json();
            $query = [];
            foreach ($page['value'] ?? [] as $item) {
                if (count($messages) >= $limit || ! is_string($item['id'] ?? null)) {
                    break;
                }
                $messages[] = [
                    'id' => $item['id'],
                    'subject' => $this->limited($item['subject'] ?? null, 500),
                    'snippet' => $this->limited($item['bodyPreview'] ?? null, 1000),
                    'occurred_at' => $this->date($item['receivedDateTime'] ?? null),
                ];
            }
            $next = $page['@odata.nextLink'] ?? null;
            $url = is_string($next) && str_starts_with($next, 'https://graph.microsoft.com/v1.0/') ? $next : null;
        } while ($url && count($messages) < $limit);

        return $messages;
    }

    private function http(string $token): PendingRequest
    {
        return Http::acceptJson()->withToken($token)->timeout(20)->retry(2, 200, throw: false);
    }

    private function date(mixed $value): CarbonImmutable
    {
        try {
            return CarbonImmutable::parse(is_string($value) ? $value : 'now');
        } catch (Throwable) {
            return CarbonImmutable::now();
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
