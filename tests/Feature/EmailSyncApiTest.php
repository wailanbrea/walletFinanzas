<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Client\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class EmailSyncApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_gmail_manual_sync_classifies_financial_email_and_is_idempotent(): void
    {
        $owner = User::factory()->create();
        $owner->emailConnections()->create([
            'provider' => 'gmail',
            'email' => 'owner@gmail.com',
            'access_token' => 'gmail-access',
            'refresh_token' => 'gmail-refresh',
            'status' => 'connected',
            'connected_at' => now(),
            'expires_at' => now()->addHour(),
        ]);

        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/gmail-message-1')) {
                return Http::response([
                    'id' => 'gmail-message-1',
                    'threadId' => 'thread-1',
                    'internalDate' => '1784550600000',
                    'payload' => [
                        'headers' => [
                            ['name' => 'Subject', 'value' => 'Compra aprobada en Supermercado Nacional'],
                            ['name' => 'From', 'value' => 'Alertas Banco <alertas@banco.example>'],
                            ['name' => 'Message-ID', 'value' => '<gmail-1@example>'],
                        ],
                        'mimeType' => 'text/plain',
                        'body' => ['data' => $this->gmailBase64('Su compra por RD$3,250.00 fue aprobada.')],
                    ],
                ]);
            }

            return Http::response([
                'messages' => [['id' => 'gmail-message-1', 'threadId' => 'thread-1']],
            ]);
        });

        Sanctum::actingAs($owner, ['wallet']);

        // El sync se encola (202); bajo QUEUE_CONNECTION=sync el job corre inline, así
        // que el run ya viene 'completed' con los contadores en la respuesta.
        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.status', 'completed')
            ->assertJsonPath('data.messages_discovered', 1)
            ->assertJsonPath('data.messages_created', 1)
            ->assertJsonPath('data.candidates_created', 1);

        $candidateId = $this->getJson('/api/v1/email-candidates')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.provider', 'gmail')
            ->assertJsonPath('data.0.amount', -325000)
            ->assertJsonPath('data.0.currency', 'DOP')
            ->assertJsonPath('data.0.direction', 'expense')
            ->assertJsonPath('data.0.category_suggestion', 'Alimentación')
            ->assertJsonPath('data.0.status', 'pending')
            ->json('data.0.id');

        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.messages_created', 0)
            ->assertJsonPath('data.candidates_created', 0);

        $this->assertDatabaseCount('email_messages', 1);
        $this->assertDatabaseCount('financial_transaction_candidates', 1);
        $this->assertDatabaseHas('financial_transaction_candidates', [
            'id' => $candidateId,
            'user_id' => $owner->id,
            'amount' => -325000,
            'currency' => 'DOP',
            'status' => 'pending',
        ]);

        Sanctum::actingAs(User::factory()->create(), ['wallet']);
        $this->getJson('/api/v1/email-candidates')->assertOk()->assertJsonCount(0, 'data');
    }

    public function test_microsoft_sync_refreshes_expired_token_and_classifies_income(): void
    {
        config()->set('services.microsoft', [
            'client_id' => 'microsoft-client',
            'client_secret' => 'microsoft-secret',
            'redirect' => 'https://wallet.test/oauth/email/microsoft/callback',
            'tenant' => 'common',
        ]);
        $owner = User::factory()->create();
        $connection = $owner->emailConnections()->create([
            'provider' => 'microsoft',
            'email' => 'owner@outlook.com',
            'access_token' => 'expired-access',
            'refresh_token' => 'microsoft-refresh',
            'status' => 'connected',
            'connected_at' => now(),
            'expires_at' => now()->subMinute(),
        ]);

        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/oauth2/v2.0/token')) {
                return Http::response([
                    'access_token' => 'fresh-microsoft-access',
                    'refresh_token' => 'rotated-microsoft-refresh',
                    'expires_in' => 3600,
                ]);
            }

            if (str_contains($request->url(), '@2026-07-20/v1/currencies/usd.json')) {
                return Http::response(
                    '{"date":"2026-07-20","usd":{"dop":61.234567}}',
                    200,
                    ['Content-Type' => 'application/json']
                );
            }

            return Http::response([
                'value' => [[
                    'id' => 'microsoft-message-1',
                    'internetMessageId' => '<microsoft-1@example>',
                    'subject' => 'Depósito recibido',
                    'from' => ['emailAddress' => ['name' => 'Banco', 'address' => 'alertas@banco.example']],
                    'receivedDateTime' => '2026-07-20T14:30:00Z',
                    'body' => ['contentType' => 'text', 'content' => 'Recibiste un depósito de US$1,200.00 en tu cuenta.'],
                ]],
            ]);
        });

        Sanctum::actingAs($owner, ['wallet']);

        $this->postJson('/api/v1/email-connections/microsoft/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.status', 'completed')
            ->assertJsonPath('data.messages_created', 1)
            ->assertJsonPath('data.candidates_created', 1);

        $this->getJson('/api/v1/email-candidates')
            ->assertOk()
            ->assertJsonPath('data.0.provider', 'microsoft')
            ->assertJsonPath('data.0.amount', 120000)
            ->assertJsonPath('data.0.currency', 'USD')
            ->assertJsonPath('data.0.converted_amount', 7348148)
            ->assertJsonPath('data.0.converted_currency', 'DOP')
            ->assertJsonPath('data.0.exchange_rate_micros', 61234567)
            ->assertJsonPath('data.0.exchange_rate_source', 'fawaz-exchange-api-historical')
            ->assertJsonPath('data.0.conversion_kind', 'historical_estimate')
            ->assertJsonPath('data.0.conversion_status', 'available')
            ->assertJsonPath('data.0.direction', 'income');

        $this->assertDatabaseHas('financial_transaction_candidates', [
            'user_id' => $owner->id,
            'amount' => 120000,
            'currency' => 'USD',
            'converted_amount' => 7348148,
            'converted_currency' => 'DOP',
            'exchange_rate_micros' => 61234567,
            'exchange_rate_source' => 'fawaz-exchange-api-historical',
        ]);

        $connection->refresh();
        $this->assertSame('fresh-microsoft-access', $connection->access_token);
        $this->assertSame('rotated-microsoft-refresh', $connection->refresh_token);
        $raw = DB::table('email_connections')->where('id', $connection->id)->first();
        $this->assertStringNotContainsString('fresh-microsoft-access', $raw->access_token);

        Http::assertSent(fn (Request $request): bool => str_contains($request->url(), '/oauth2/v2.0/token'));
        Http::assertSent(fn (Request $request): bool => str_contains($request->url(), 'graph.microsoft.com/v1.0/me/messages')
            && $request->hasHeader('Authorization', 'Bearer fresh-microsoft-access'));
    }

    public function test_sync_reclassifies_existing_message_without_candidate_after_parser_improvement(): void
    {
        $owner = User::factory()->create();
        $connection = $owner->emailConnections()->create([
            'provider' => 'gmail',
            'email' => 'owner@gmail.com',
            'access_token' => 'gmail-access',
            'refresh_token' => 'gmail-refresh',
            'status' => 'connected',
            'connected_at' => now(),
            'expires_at' => now()->addHour(),
        ]);
        $connection->messages()->create([
            'user_id' => $owner->id,
            'provider' => 'gmail',
            'provider_message_id' => 'gmail-existing-message',
            'internet_message_id' => '<existing@example>',
            'thread_id' => 'thread-existing',
            'subject' => 'Mensaje antes no reconocido',
            'sender_name' => 'Alertas Banco',
            'sender_email' => 'alertas@banco.example',
            'received_at' => '2026-07-20T14:30:00Z',
            'body_excerpt' => 'Contenido anterior sin parser.',
            'content_hash' => hash('sha256', 'Contenido anterior sin parser.'),
        ]);

        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/gmail-existing-message')) {
                return Http::response([
                    'id' => 'gmail-existing-message',
                    'threadId' => 'thread-existing',
                    'internalDate' => '1784557800000',
                    'payload' => [
                        'headers' => [
                            ['name' => 'Subject', 'value' => 'Compra aprobada'],
                            ['name' => 'From', 'value' => 'Alertas Banco <alertas@banco.example>'],
                            ['name' => 'Message-ID', 'value' => '<existing@example>'],
                        ],
                        'mimeType' => 'text/plain',
                        'body' => ['data' => $this->gmailBase64('Su compra por RD$850.00 fue aprobada.')],
                    ],
                ]);
            }

            return Http::response([
                'messages' => [['id' => 'gmail-existing-message', 'threadId' => 'thread-existing']],
            ]);
        });

        Sanctum::actingAs($owner, ['wallet']);

        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.messages_created', 0)
            ->assertJsonPath('data.candidates_created', 1);

        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.messages_created', 0)
            ->assertJsonPath('data.candidates_created', 0);

        $this->assertDatabaseCount('email_messages', 1);
        $this->assertDatabaseCount('financial_transaction_candidates', 1);
    }

    public function test_sync_automatically_replaces_stale_usd_conversion_using_occurred_at(): void
    {
        $owner = User::factory()->create();
        $connection = $owner->emailConnections()->create([
            'provider' => 'gmail',
            'email' => 'owner@gmail.com',
            'access_token' => 'gmail-access',
            'status' => 'connected',
            'connected_at' => now(),
            'expires_at' => now()->addHour(),
        ]);
        $message = $connection->messages()->create([
            'user_id' => $owner->id,
            'provider_message_id' => 'old-usd-message',
            'subject' => 'Pago USD anterior',
            'received_at' => '2026-07-10T16:16:40Z',
            'body_excerpt' => 'Pago procesado.',
            'content_hash' => hash('sha256', 'old-usd-message'),
        ]);
        $candidate = $message->candidate()->create([
            'user_id' => $owner->id,
            'provider' => 'gmail',
            'merchant' => 'Proveedor de prueba',
            'amount' => -1000,
            'currency' => 'USD',
            'converted_amount' => -60000,
            'converted_currency' => 'DOP',
            'exchange_rate_micros' => 60000000,
            'exchange_rate_at' => '2026-07-21T00:00:00Z',
            'exchange_rate_source' => 'exchangerate-api-open',
            'direction' => 'expense',
            'occurred_at' => '2026-07-10T16:16:40Z',
            'confidence' => 98,
            'reasons' => ['known_sender_format:test'],
            'status' => 'pending',
        ]);

        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '@2026-07-10/v1/currencies/usd.json')) {
                return Http::response(
                    '{"date":"2026-07-10","usd":{"dop":58.71137777}}',
                    200,
                    ['Content-Type' => 'application/json']
                );
            }

            return Http::response(['messages' => []]);
        });

        Sanctum::actingAs($owner, ['wallet']);

        // El backfill FX ocurre dentro del sync encolado; se verifica por el estado del
        // candidato (el contador conversions_backfilled ya no se expone en el run).
        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.status', 'completed');

        $candidate->refresh();
        $this->assertSame(-58711, $candidate->converted_amount);
        $this->assertSame(58711378, $candidate->exchange_rate_micros);
        $this->assertSame('fawaz-exchange-api-historical', $candidate->exchange_rate_source);
    }

    public function test_sync_rejects_missing_or_disconnected_connection(): void
    {
        Sanctum::actingAs(User::factory()->create(), ['wallet']);

        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertConflict()
            ->assertJsonPath('message', 'Conecta Gmail antes de sincronizar.');
    }

    public function test_sync_rejects_a_second_request_while_same_connection_is_locked(): void
    {
        $owner = User::factory()->create();
        $connection = $owner->emailConnections()->create([
            'provider' => 'gmail',
            'email' => 'owner@gmail.com',
            'access_token' => 'gmail-access',
            'status' => 'connected',
            'connected_at' => now(),
        ]);
        $lock = Cache::lock('email-sync:'.$connection->id, 120);
        $this->assertTrue($lock->get());
        Sanctum::actingAs($owner, ['wallet']);

        try {
            $this->postJson('/api/v1/email-connections/gmail/sync')
                ->assertConflict()
                ->assertJsonPath('message', 'Ya hay una sincronización en curso para esta cuenta.');
        } finally {
            $lock->release();
        }
    }

    private function gmailBase64(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }
}
