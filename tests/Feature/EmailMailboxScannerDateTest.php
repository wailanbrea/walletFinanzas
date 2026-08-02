<?php

namespace Tests\Feature;

use App\Models\EmailCandidate;
use App\Models\EmailConnection;
use App\Models\ProviderMessage;
use App\Models\User;
use App\Services\FinancialEmailExtractor;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Client\Request;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Mockery;
use Tests\TestCase;

class EmailMailboxScannerDateTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        config(['email_sync.max_messages_per_run' => 1, 'queue.default' => 'sync']);
        Http::preventStrayRequests();
    }

    public function test_gmail_backfill_uses_inclusive_date_and_resumes_with_cursor(): void
    {
        $user = User::factory()->create();
        $this->connection($user);
        Sanctum::actingAs($user);
        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/m1')) {
                return Http::response($this->gmailMessage('m1', 'Mon, 20 Jul 2026 12:00:00 +0000'));
            }
            if (str_contains($request->url(), '/messages/m2')) {
                return Http::response($this->gmailMessage('m2', 'Sun, 19 Jul 2026 12:00:00 +0000'));
            }
            if (($request->data()['pageToken'] ?? null) === 'next-page') {
                return Http::response(['messages' => [['id' => 'm2']]]);
            }

            return Http::response([
                'messages' => [['id' => 'm1']],
                'nextPageToken' => 'next-page',
            ]);
        });

        $first = $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'sync_from_date' => '2026-07-01',
        ])->assertStatus(202);
        $first->assertJsonPath('data.messages_discovered', 2);
        $this->assertDatabaseCount('email_candidates', 2);
        $this->assertDatabaseCount('email_sync_runs', 1);
        $this->assertNotNull($user->emailMailboxes()->firstOrFail()->backfill_completed_at);
        Http::assertSent(function (Request $request): bool {
            if (! str_ends_with(parse_url($request->url(), PHP_URL_PATH), '/messages')) {
                return false;
            }
            $query = $request->data()['q'] ?? '';

            return str_contains($query, 'after:') && str_contains($query, 'before:');
        });
        Http::assertSent(fn (Request $request): bool => ($request->data()['pageToken'] ?? null) === 'next-page');
    }

    public function test_gmail_internal_date_prevents_a_malformed_header_from_skipping_the_message(): void
    {
        $user = User::factory()->create();
        $this->connection($user);
        Sanctum::actingAs($user);
        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/internal-date')) {
                return Http::response($this->gmailMessage(
                    'internal-date',
                    'not-a-date',
                    (string) CarbonImmutable::parse('2026-07-20T12:00:00Z')->getTimestampMs(),
                ));
            }

            return Http::response(['messages' => [['id' => 'internal-date']]]);
        });

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'sync_from_date' => '2026-07-01',
        ])->assertStatus(202)->assertJsonPath('data.messages_discovered', 1);

        $this->assertDatabaseHas('provider_messages', [
            'provider_message_id' => 'internal-date',
            'occurred_at' => '2026-07-20 12:00:00',
        ]);
    }

    public function test_gmail_sender_uses_the_mailbox_inside_angle_brackets(): void
    {
        $user = User::factory()->create();
        $this->connection($user);
        Sanctum::actingAs($user);
        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/sender-mailbox')) {
                return Http::response($this->gmailMessage(
                    'sender-mailbox',
                    'Mon, 20 Jul 2026 12:00:00 +0000',
                    null,
                    'Trusted alerts@trusted-bank.com <fraud@evil.test>',
                ));
            }

            return Http::response(['messages' => [['id' => 'sender-mailbox']]]);
        });

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'sync_from_date' => '2026-07-01',
        ])->assertStatus(202);

        $this->assertDatabaseHas('provider_messages', [
            'sender_address' => 'fraud@evil.test',
            'sender_domain' => 'evil.test',
        ]);
    }

    public function test_microsoft_non_multiple_limit_continues_on_page_boundaries_without_skipping(): void
    {
        config(['email_sync.max_messages_per_run' => 75]);
        $user = User::factory()->create();
        EmailConnection::query()->create([
            'user_id' => $user->id,
            'provider' => 'microsoft',
            'email' => 'owner@example.test',
            'status' => 'connected',
            'access_token' => 'synthetic-token',
            'refresh_token' => 'synthetic-refresh-token',
            'token_expires_at' => now()->addHour(),
            'connected_at' => now(),
        ]);
        Sanctum::actingAs($user);
        $page = 0;
        Http::fake(function () use (&$page) {
            $page++;
            $range = $page === 1 ? range(1, 50) : range(51, 75);
            $response = [
                'value' => array_map(fn (int $id): array => [
                    'id' => 'graph-'.$id,
                    'subject' => 'Cargo RD$ 10.00',
                    'bodyPreview' => 'Compra aprobada',
                    'receivedDateTime' => '2026-07-20T12:00:00Z',
                ], $range),
            ];
            if ($page === 1) {
                $response['@odata.nextLink'] = 'https://graph.microsoft.com/v1.0/me/messages?$skiptoken=next';
            }

            return Http::response($response);
        });

        $response = $this->postJson('/api/v1/email-connections/microsoft/sync', [
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'sync_from_date' => '2026-07-01',
        ])->assertStatus(202);

        $response->assertJsonPath('data.messages_discovered', 75);
        $this->assertSame(2, $page);
        $this->assertDatabaseCount('provider_messages', 75);
        $this->assertDatabaseCount('email_candidates', 75);
        $this->assertDatabaseCount('email_sync_runs', 1);
        $this->assertDatabaseHas('provider_messages', [
            'provider_message_id' => 'graph-75',
        ]);
    }

    public function test_concurrent_review_cannot_be_reset_to_pending(): void
    {
        $user = User::factory()->create();
        $connection = $this->connection($user);
        $message = ProviderMessage::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'provider_message_id' => 'race-message',
            'subject' => 'Pago con tarjeta',
            'snippet' => 'Compra aprobada USD 3.60',
            'occurred_at' => '2026-07-20 12:00:00',
        ]);
        $candidate = EmailCandidate::query()->create([
            'user_id' => $user->id,
            'provider_message_id' => $message->id,
            'provider' => 'gmail',
            'amount' => 360,
            'currency' => 'USD',
            'direction' => 'expense',
            'occurred_at' => '2026-07-20 12:00:00',
            'confidence' => 80,
            'status' => 'pending',
        ]);
        $extractor = Mockery::mock(FinancialEmailExtractor::class);
        $extractor->shouldReceive('isDefiniteNonTransaction')->andReturn(false);
        $extractor->shouldReceive('extract')->once()->andReturnUsing(function () use ($candidate): array {
            EmailCandidate::query()->whereKey($candidate->id)->update(['status' => 'dismissed']);

            return [
                'merchant' => 'Comercio nuevo',
                'card_last_four' => null,
                'amount' => 360,
                'currency' => 'USD',
                'direction' => 'expense',
                'category_suggestion' => 'Compras',
                'occurred_at' => now(),
                'confidence' => 90,
                'subject' => 'Pago con tarjeta',
            ];
        });
        $this->app->instance(FinancialEmailExtractor::class, $extractor);
        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/race-message')) {
                return Http::response($this->gmailMessage('race-message', 'Mon, 20 Jul 2026 12:00:00 +0000'));
            }

            return Http::response(['messages' => [['id' => 'race-message']]]);
        });
        Sanctum::actingAs($user);

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'sync_from_date' => '2026-07-01',
        ])->assertStatus(202);

        $this->assertSame('dismissed', $candidate->fresh()->status);
        $this->assertNull($candidate->fresh()->merchant);
    }

    private function connection(User $user): EmailConnection
    {
        return EmailConnection::query()->create([
            'user_id' => $user->id,
            'provider' => 'gmail',
            'email' => 'owner@example.test',
            'status' => 'connected',
            'access_token' => 'synthetic-token',
            'refresh_token' => 'synthetic-refresh-token',
            'token_expires_at' => now()->addHour(),
            'connected_at' => now(),
        ]);
    }

    private function gmailMessage(
        string $id,
        string $date,
        ?string $internalDate = null,
        string $from = 'Banco <alerts@example.test>',
    ): array {
        return [
            'id' => $id,
            'snippet' => 'Compra aprobada USD 3.60',
            'internalDate' => $internalDate ?? (string) CarbonImmutable::parse($date)->getTimestampMs(),
            'payload' => ['headers' => [
                ['name' => 'Subject', 'value' => 'Pago con tarjeta'],
                ['name' => 'Date', 'value' => $date],
                ['name' => 'From', 'value' => $from],
            ]],
        ];
    }
}
