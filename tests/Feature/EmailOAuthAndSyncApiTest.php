<?php

namespace Tests\Feature;

use App\Jobs\SyncEmailConnection;
use App\Models\EmailCandidate;
use App\Models\EmailConnection;
use App\Models\EmailOAuthState;
use App\Models\EmailSyncRun;
use App\Models\ProviderMessage;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Client\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Queue;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class EmailOAuthAndSyncApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        config([
            'services.google_oauth' => [
                'client_id' => 'test-google-id',
                'client_secret' => 'test-google-secret',
                'redirect_uri' => 'https://api.example.test/api/v1/oauth/gmail/callback',
            ],
            'services.microsoft_oauth' => [
                'client_id' => 'test-microsoft-id',
                'client_secret' => 'test-microsoft-secret',
                'tenant_id' => 'common',
                'redirect_uri' => 'https://api.example.test/api/v1/oauth/microsoft/callback',
            ],
            'queue.default' => 'sync',
        ]);
        Http::preventStrayRequests();
    }

    public function test_connections_are_authenticated_and_authorization_uses_hashed_one_use_state_and_pkce(): void
    {
        $this->getJson('/api/v1/email-connections')->assertUnauthorized();
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->getJson('/api/v1/email-connections')
            ->assertOk()
            ->assertJsonCount(2, 'data')
            ->assertJsonPath('data.0.provider', 'gmail')
            ->assertJsonPath('data.0.configuration_ready', true);
        $response = $this->postJson('/api/v1/email-connections/gmail/authorization-url')->assertOk();
        $url = $response->json('data.authorization_url');
        parse_str((string) parse_url($url, PHP_URL_QUERY), $query);

        $this->assertSame('S256', $query['code_challenge_method']);
        $this->assertArrayNotHasKey('code_verifier', $query);
        $state = EmailOAuthState::firstOrFail();
        $this->assertSame(hash('sha256', $query['state']), $state->state_hash);
        $this->assertNotSame($state->code_verifier, DB::table('email_oauth_states')->value('code_verifier'));
    }

    public function test_gmail_callback_persists_encrypted_tokens_and_rejects_state_replay(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $url = $this->postJson('/api/v1/email-connections/gmail/authorization-url')->json('data.authorization_url');
        parse_str((string) parse_url($url, PHP_URL_QUERY), $query);
        Http::fake([
            'https://oauth2.googleapis.com/token' => Http::response([
                'access_token' => 'synthetic-access-token',
                'refresh_token' => 'synthetic-refresh-token',
                'expires_in' => 3600,
            ]),
            'https://gmail.googleapis.com/gmail/v1/users/me/profile*' => Http::response(['emailAddress' => 'owner@example.test']),
        ]);

        $callback = '/api/v1/oauth/gmail/callback?'.http_build_query(['state' => $query['state'], 'code' => 'synthetic-code']);
        $this->get($callback)->assertRedirect('walletfinanzas://email-oauth?provider=gmail&status=connected');
        $connection = EmailConnection::firstOrFail();
        $this->assertSame('owner@example.test', $connection->email);
        $this->assertNotSame($connection->access_token, DB::table('email_connections')->value('access_token'));
        $this->get($callback)->assertRedirect('walletfinanzas://email-oauth?provider=gmail&status=failed');
        $this->assertDatabaseCount('email_connections', 1);
    }

    public function test_expired_oauth_state_fails_without_provider_request(): void
    {
        $user = User::factory()->create();
        $plainState = 'expired-state';
        EmailOAuthState::create([
            'user_id' => $user->id,
            'provider' => 'gmail',
            'state_hash' => hash('sha256', $plainState),
            'code_verifier' => 'synthetic-verifier',
            'expires_at' => now()->subMinute(),
        ]);
        Http::fake();

        $this->get('/api/v1/oauth/gmail/callback?'.http_build_query(['state' => $plainState, 'code' => 'synthetic-code']))
            ->assertRedirect('walletfinanzas://email-oauth?provider=gmail&status=failed');
        Http::assertNothingSent();
    }

    public function test_gmail_sync_is_idempotent_and_extracts_integer_minor_units_without_transactions(): void
    {
        config(['email_sync.max_messages_per_run' => 1]);
        $user = User::factory()->create();
        $this->connection($user, 'gmail');
        Sanctum::actingAs($user);
        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/msg-1')) {
                return Http::response([
                    'id' => 'msg-1',
                    'snippet' => 'Compra aprobada USD 123.45',
                    'payload' => ['headers' => [
                        ['name' => 'Subject', 'value' => 'Pago con tarjeta'],
                        ['name' => 'Date', 'value' => 'Wed, 22 Jul 2026 12:00:00 +0000'],
                    ]],
                ]);
            }

            return Http::response(['messages' => [['id' => 'msg-1']]]);
        });

        $first = $this->postJson('/api/v1/email-connections/gmail/sync')->assertStatus(202);
        $first->assertJsonPath('data.status', 'completed')->assertJsonPath('data.candidates_created', 1);
        $gmailConnection = EmailConnection::firstOrFail();
        $staleCandidate = $this->budgetCandidate($user, $gmailConnection, 'stale-budget-alert');
        $categorizedCandidate = $this->budgetCandidate($user, $gmailConnection, 'categorized-budget-alert', 'categorized');
        $microsoftCandidate = $this->budgetCandidate($user, $this->connection($user, 'microsoft'), 'microsoft-budget-alert');
        $other = User::factory()->create();
        $otherCandidate = $this->budgetCandidate($other, $this->connection($other, 'gmail'), 'other-user-budget-alert');
        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.messages_created', 0)
            ->assertJsonPath('data.candidates_created', 0);
        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.messages_created', 0)
            ->assertJsonPath('data.candidates_created', 0);
        $this->assertDatabaseCount('provider_messages', 5);
        $this->assertDatabaseCount('email_candidates', 4);
        $this->assertDatabaseMissing('email_candidates', ['id' => $staleCandidate->id]);
        $this->assertDatabaseHas('email_candidates', ['id' => $categorizedCandidate->id]);
        $this->assertDatabaseHas('email_candidates', ['id' => $microsoftCandidate->id]);
        $this->assertDatabaseHas('email_candidates', ['id' => $otherCandidate->id]);
        $this->assertDatabaseHas('email_candidates', ['amount' => 12345, 'currency' => 'USD', 'direction' => 'expense']);
        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_microsoft_sync_refreshes_token_and_follows_only_bounded_graph_pages(): void
    {
        config(['email_sync.max_messages_per_run' => 2]);
        $user = User::factory()->create();
        $connection = $this->connection($user, 'microsoft', now()->subMinute());
        Sanctum::actingAs($user);
        Http::fake([
            'https://login.microsoftonline.com/common/oauth2/v2.0/token' => Http::response([
                'access_token' => 'synthetic-renewed-token', 'expires_in' => 3600,
            ]),
            'https://graph.microsoft.com/v1.0/me/messages*' => Http::sequence()
                ->push([
                    'value' => [['id' => 'm1', 'subject' => 'Deposito EUR 10.00', 'bodyPreview' => 'Ingreso recibido', 'receivedDateTime' => '2026-07-22T10:00:00Z']],
                    '@odata.nextLink' => 'https://graph.microsoft.com/v1.0/me/messages?$skiptoken=next',
                ])
                ->push(['value' => [['id' => 'm2', 'subject' => 'Cargo $20.00', 'bodyPreview' => 'Compra aprobada', 'receivedDateTime' => '2026-07-22T11:00:00Z']]]),
        ]);

        $this->postJson('/api/v1/email-connections/microsoft/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.status', 'completed')
            ->assertJsonPath('data.messages_discovered', 2);
        $this->assertNotSame($connection->refresh()->access_token, DB::table('email_connections')->where('id', $connection->id)->value('access_token'));
        Http::assertSent(fn (Request $request) => str_contains($request->url(), 'graph.microsoft.com') && $request->hasHeader('Authorization', 'Bearer synthetic-renewed-token'));
    }

    public function test_sync_runs_candidates_and_disconnect_are_user_scoped(): void
    {
        $owner = User::factory()->create();
        $other = User::factory()->create();
        $connection = $this->connection($owner, 'gmail');
        $message = ProviderMessage::create([
            'user_id' => $owner->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'provider_message_id' => 'private-message',
            'subject' => 'Pago USD 1.00',
            'snippet' => 'Compra',
            'occurred_at' => now(),
        ]);
        $candidate = EmailCandidate::create([
            'user_id' => $owner->id,
            'provider_message_id' => $message->id,
            'provider' => 'gmail',
            'amount' => 100,
            'currency' => 'USD',
            'direction' => 'expense',
            'occurred_at' => now(),
            'confidence' => 80,
            'status' => 'pending',
        ]);
        $run = EmailSyncRun::create(['user_id' => $owner->id, 'email_connection_id' => $connection->id, 'provider' => 'gmail', 'status' => 'completed']);

        Sanctum::actingAs($other);
        $this->getJson('/api/v1/email-candidates')->assertOk()->assertJsonCount(0, 'data');
        $this->patchJson('/api/v1/email-candidates/'.$candidate->id, ['action' => 'dismiss'])->assertNotFound();
        $this->getJson('/api/v1/email-connections/gmail/sync-runs/'.$run->id)->assertNotFound();
        $this->deleteJson('/api/v1/email-connections/gmail')->assertNoContent();
        $this->assertDatabaseHas('email_connections', ['id' => $connection->id]);

        Sanctum::actingAs($owner);
        $this->patchJson('/api/v1/email-candidates/'.$candidate->id, ['action' => 'categorize', 'category' => 'Food', 'learn' => true])
            ->assertOk()
            ->assertJsonPath('data.status', 'categorized');
        $this->deleteJson('/api/v1/email-connections/gmail')->assertNoContent();
        $this->assertDatabaseMissing('email_connections', ['id' => $connection->id]);
    }

    public function test_repeated_sync_reuses_the_active_run(): void
    {
        Queue::fake();
        $user = User::factory()->create();
        $this->connection($user, 'gmail');
        Sanctum::actingAs($user);

        $first = $this->postJson('/api/v1/email-connections/gmail/sync')->assertStatus(202);
        $second = $this->postJson('/api/v1/email-connections/gmail/sync')->assertStatus(202);

        $this->assertSame($first->json('data.sync_run_id'), $second->json('data.sync_run_id'));
        $this->assertDatabaseCount('email_sync_runs', 1);
        Queue::assertPushed(SyncEmailConnection::class, 1);
    }

    private function connection(User $user, string $provider, mixed $expiresAt = null): EmailConnection
    {
        return EmailConnection::create([
            'user_id' => $user->id,
            'provider' => $provider,
            'email' => $provider.'@example.test',
            'status' => 'connected',
            'access_token' => 'synthetic-current-token',
            'refresh_token' => 'synthetic-refresh-token',
            'token_expires_at' => $expiresAt ?? now()->addHour(),
            'connected_at' => now(),
        ]);
    }

    private function budgetCandidate(User $user, EmailConnection $connection, string $messageId, string $status = 'pending'): EmailCandidate
    {
        $message = ProviderMessage::create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => $connection->provider,
            'provider_message_id' => $messageId,
            'subject' => '150% of budget reached',
            'snippet' => 'Payment USD 3.60',
            'occurred_at' => now(),
        ]);

        return EmailCandidate::create([
            'user_id' => $user->id,
            'provider_message_id' => $message->id,
            'provider' => $connection->provider,
            'amount' => 360,
            'currency' => 'USD',
            'direction' => 'expense',
            'occurred_at' => now(),
            'confidence' => 80,
            'status' => $status,
        ]);
    }
}
