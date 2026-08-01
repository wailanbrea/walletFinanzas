<?php

namespace Tests\Feature;

use App\Jobs\SyncEmailConnection;
use App\Models\Account;
use App\Models\EmailCandidate;
use App\Models\EmailConnection;
use App\Models\EmailMailbox;
use App\Models\EmailOAuthState;
use App\Models\EmailSyncRun;
use App\Models\ProviderMessage;
use App\Models\Transaction;
use App\Models\User;
use App\Services\EmailMailboxScanner;
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

    public function test_reauthorizing_with_a_different_gmail_account_replaces_the_connection_without_reusing_its_mailbox(): void
    {
        $user = User::factory()->create();
        $oldConnection = $this->connection($user, 'gmail');
        $oldMailbox = EmailMailbox::forConnection($oldConnection);
        $this->budgetCandidate($user, $oldConnection, 'old-account-message');
        $plainState = str_repeat('s', 64);
        EmailOAuthState::create([
            'user_id' => $user->id,
            'provider' => 'gmail',
            'state_hash' => hash('sha256', $plainState),
            'code_verifier' => 'synthetic-verifier',
            'expires_at' => now()->addMinute(),
        ]);
        Http::fake([
            'https://oauth2.googleapis.com/token' => Http::response([
                'access_token' => 'new-account-token',
                'refresh_token' => 'new-account-refresh-token',
                'expires_in' => 3600,
            ]),
            'https://gmail.googleapis.com/gmail/v1/users/me/profile*' => Http::response([
                'emailAddress' => 'other-owner@example.test',
            ]),
        ]);

        $this->get('/api/v1/oauth/gmail/callback?'.http_build_query([
            'state' => $plainState,
            'code' => 'synthetic-code',
        ]))->assertRedirect('walletfinanzas://email-oauth?provider=gmail&status=connected');

        $newConnection = EmailConnection::query()->firstOrFail();
        $this->assertNotSame($oldConnection->id, $newConnection->id);
        $this->assertNotSame($oldMailbox->id, $newConnection->email_mailbox_id);
        $this->assertSame('other-owner@example.test', $newConnection->email);
        $this->assertDatabaseHas('email_mailboxes', ['id' => $oldMailbox->id]);
        $this->assertDatabaseMissing('provider_messages', ['provider_message_id' => 'old-account-message']);
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
        Http::assertSent(fn (Request $request): bool => str_contains($request->url(), 'graph.microsoft.com/v1.0/me/messages')
            && str_contains((string) ($request->data()['$filter'] ?? ''), 'receivedDateTime ge')
        );
    }

    public function test_invalid_grant_pauses_sync_until_oauth_reconnects_the_connection(): void
    {
        $user = User::factory()->create();
        $connection = $this->connection($user, 'gmail', now()->subMinute());
        Sanctum::actingAs($user);
        $reauthorizing = false;
        Http::fake(function (Request $request) use (&$reauthorizing) {
            if (str_contains($request->url(), 'oauth2.googleapis.com/token')) {
                return $reauthorizing
                    ? Http::response([
                        'access_token' => 'reauthorized-access-token',
                        'refresh_token' => 'reauthorized-refresh-token',
                        'expires_in' => 3600,
                    ])
                    : Http::response([
                        'error' => 'invalid_grant',
                        'error_description' => 'Token has been expired or revoked.',
                    ], 400);
            }

            return Http::response(['emailAddress' => 'gmail@example.test']);
        });

        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(202)
            ->assertJsonPath('data.status', 'failed')
            ->assertJsonPath('data.error_code', 'email_reauthorization_required');
        $connection->refresh();
        $this->assertSame('reauthorization_required', $connection->status);
        $this->assertNull($connection->refresh_token);
        $this->assertNull($connection->token_expires_at);
        $this->postJson('/api/v1/email-connections/gmail/sync')
            ->assertStatus(409)
            ->assertJsonPath('code', 'email_reauthorization_required');

        $plainState = str_repeat('r', 64);
        EmailOAuthState::create([
            'user_id' => $user->id,
            'provider' => 'gmail',
            'state_hash' => hash('sha256', $plainState),
            'code_verifier' => 'synthetic-verifier',
            'expires_at' => now()->addMinute(),
        ]);
        $reauthorizing = true;

        $this->get('/api/v1/oauth/gmail/callback?'.http_build_query([
            'state' => $plainState,
            'code' => 'synthetic-code',
        ]))->assertRedirect('walletfinanzas://email-oauth?provider=gmail&status=connected');
        $connection->refresh();
        $this->assertSame('connected', $connection->status);
        $this->assertSame('reauthorized-refresh-token', $connection->refresh_token);
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

    public function test_an_automatic_request_redispatches_a_stale_queued_run(): void
    {
        Queue::fake();
        $user = User::factory()->create();
        $connection = $this->connection($user, 'gmail');
        $run = EmailSyncRun::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'status' => 'queued',
        ]);
        DB::table('email_sync_runs')->where('id', $run->id)->update(['updated_at' => now()->subMinute()]);
        Sanctum::actingAs($user);

        $response = $this->postJson('/api/v1/email-connections/gmail/sync')->assertStatus(202);

        $this->assertSame($run->id, $response->json('data.sync_run_id'));
        Queue::assertPushed(
            SyncEmailConnection::class,
            fn (SyncEmailConnection $job): bool => $job->runId === $run->id,
        );
    }

    public function test_a_retry_reclaims_a_stale_running_lease(): void
    {
        $user = User::factory()->create();
        $connection = $this->connection($user, 'gmail');
        $run = EmailSyncRun::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'status' => 'running',
            'started_at' => now()->subMinutes(2),
        ]);
        DB::table('email_sync_runs')->where('id', $run->id)->update(['updated_at' => now()->subMinutes(2)]);
        $scanner = $this->mock(EmailMailboxScanner::class);
        $scanner->shouldReceive('scan')->once()->andReturn([
            'messages_discovered' => 1,
            'messages_created' => 1,
            'candidates_created' => 0,
            'duplicates_marked' => 0,
            'has_more' => false,
        ]);

        (new SyncEmailConnection($run->id))->handle($scanner);

        $this->assertDatabaseHas('email_sync_runs', [
            'id' => $run->id,
            'status' => 'completed',
            'messages_discovered' => 1,
        ]);
    }

    public function test_a_sync_run_with_more_messages_queues_the_next_batch(): void
    {
        Queue::fake();
        $user = User::factory()->create();
        $connection = $this->connection($user, 'gmail');
        $run = EmailSyncRun::create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'status' => 'queued',
        ]);
        $scanner = $this->mock(EmailMailboxScanner::class);
        $scanner->shouldReceive('scan')->twice()->andReturn(
            [
                'messages_discovered' => 100,
                'messages_created' => 100,
                'candidates_created' => 4,
                'duplicates_marked' => 1,
                'has_more' => true,
            ],
            [
                'messages_discovered' => 25,
                'messages_created' => 20,
                'candidates_created' => 2,
                'duplicates_marked' => 0,
                'has_more' => false,
            ],
        );

        (new SyncEmailConnection($run->id))->handle($scanner);

        $this->assertDatabaseHas('email_sync_runs', [
            'id' => $run->id,
            'status' => 'queued',
            'messages_discovered' => 100,
        ]);
        $this->assertDatabaseCount('email_sync_runs', 1);
        Queue::assertPushed(
            SyncEmailConnection::class,
            fn (SyncEmailConnection $job): bool => $job->runId === $run->id,
        );

        (new SyncEmailConnection($run->id))->handle($scanner);

        $this->assertDatabaseHas('email_sync_runs', [
            'id' => $run->id,
            'status' => 'completed',
            'messages_discovered' => 125,
            'messages_created' => 120,
            'candidates_created' => 6,
        ]);

        // A duplicate delivery cannot rescan or increment an already completed run.
        (new SyncEmailConnection($run->id))->handle($scanner);
        $this->assertDatabaseHas('email_sync_runs', [
            'id' => $run->id,
            'messages_discovered' => 125,
        ]);
    }

    public function test_candidate_index_returns_only_pending_items(): void
    {
        $user = User::factory()->create();
        $connection = $this->connection($user, 'gmail');
        $pending = $this->budgetCandidate($user, $connection, 'pending-message');
        $this->budgetCandidate($user, $connection, 'categorized-message', 'categorized');
        $this->budgetCandidate($user, $connection, 'dismissed-message', 'dismissed');
        $this->budgetCandidate($user, $connection, 'duplicate-message', 'duplicate');
        Sanctum::actingAs($user);

        $this->getJson('/api/v1/email-candidates')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.id', $pending->id)
            ->assertJsonPath('data.0.status', 'pending');
    }

    public function test_dismissed_message_stays_hidden_after_disconnect_and_reconnect(): void
    {
        $user = User::factory()->create();
        $connection = $this->connection($user, 'gmail');
        $candidate = $this->budgetCandidate($user, $connection, 'permanent-dismiss');
        Sanctum::actingAs($user);

        $this->patchJson('/api/v1/email-candidates/'.$candidate->id, ['action' => 'dismiss'])
            ->assertOk()
            ->assertJsonPath('data.status', 'dismissed');
        $mailboxId = EmailMailbox::query()->where('email', 'gmail@example.test')->value('id');
        $this->assertDatabaseHas('email_message_decisions', [
            'email_mailbox_id' => $mailboxId,
            'provider_message_id' => 'permanent-dismiss',
            'status' => 'dismissed',
        ]);

        $this->deleteJson('/api/v1/email-connections/gmail')->assertNoContent();
        $this->assertDatabaseHas('email_mailboxes', ['id' => $mailboxId]);
        $this->assertDatabaseHas('email_message_decisions', ['email_mailbox_id' => $mailboxId]);
        $this->connection($user, 'gmail');
        Http::fake(function (Request $request) {
            if (str_contains($request->url(), '/messages/permanent-dismiss')) {
                return Http::response([
                    'id' => 'permanent-dismiss',
                    'snippet' => 'Compra aprobada USD 3.60',
                    'payload' => ['headers' => [
                        ['name' => 'Subject', 'value' => 'Pago con tarjeta'],
                        ['name' => 'Date', 'value' => 'Fri, 31 Jul 2026 12:00:00 +0000'],
                    ]],
                ]);
            }

            return Http::response(['messages' => [['id' => 'permanent-dismiss']]]);
        });
        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-01T00:00:00Z',
            'sync_from_date' => '2026-07-01',
        ])->assertStatus(202)->assertJsonPath('data.candidates_created', 0);
        $this->assertDatabaseCount('email_candidates', 0);
        $this->getJson('/api/v1/email-candidates')->assertOk()->assertJsonCount(0, 'data');
    }

    public function test_sync_date_is_saved_per_mailbox_and_returned_by_api(): void
    {
        $user = User::factory()->create();
        $this->connection($user, 'gmail');
        Sanctum::actingAs($user);
        Http::fake(['https://gmail.googleapis.com/*' => Http::response(['messages' => []])]);
        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-10T04:00:00Z',
            'sync_from_date' => '2026-07-10',
        ])->assertStatus(202)->assertJsonPath('data.sync_from_at', '2026-07-10T04:00:00.000000Z');

        $this->assertDatabaseHas('email_mailboxes', ['sync_from_at' => '2026-07-10 04:00:00']);
        $this->getJson('/api/v1/email-connections')
            ->assertOk()
            ->assertJsonPath('data.0.sync_from_at', '2026-07-10T04:00:00.000000Z');
    }

    public function test_automatic_sync_accepts_serialized_null_cutoffs_and_partial_manual_cutoffs_are_rejected(): void
    {
        $user = User::factory()->create();
        $this->connection($user, 'gmail');
        Sanctum::actingAs($user);
        Http::fake(['https://gmail.googleapis.com/*' => Http::response(['messages' => []])]);

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => null,
            'sync_from_date' => null,
        ])->assertStatus(202);

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_date' => '2026-07-10',
        ])->assertUnprocessable()
            ->assertJsonValidationErrors('sync_from_date');

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-10T04:00:00Z',
            'sync_from_date' => '2099-01-01',
        ])->assertUnprocessable()
            ->assertJsonValidationErrors('sync_from_date');

        $this->postJson('/api/v1/email-connections/gmail/sync', [
            'sync_from_at' => '2026-07-10T04:00:00',
            'sync_from_date' => '2026-07-10',
        ])->assertUnprocessable()
            ->assertJsonValidationErrors('sync_from_at');
    }

    public function test_migration_quarantines_legacy_email_cache_without_deleting_transactions(): void
    {
        $migration = require database_path('migrations/2026_08_01_000000_create_email_mailboxes_and_decisions.php');
        $migration->down();

        $user = User::factory()->create();
        $account = Account::query()->create([
            'user_id' => $user->id,
            'name' => 'Cuenta principal',
            'type' => 'bank',
            'balance' => 100_00,
            'currency' => 'DOP',
        ]);
        $transaction = Transaction::query()->create([
            'user_id' => $user->id,
            'account_id' => $account->id,
            'amount' => -500,
            'currency' => 'DOP',
            'description' => 'Compra ya contabilizada',
            'occurred_at' => now()->subDay(),
            'status' => 'completed',
        ]);
        $connection = $this->connection($user, 'gmail');
        $this->budgetCandidate($user, $connection, 'legacy-untrusted-message', 'dismissed');

        $migration->up();

        $this->assertDatabaseCount('provider_messages', 0);
        $this->assertDatabaseCount('email_candidates', 0);
        $this->assertDatabaseCount('email_message_decisions', 0);
        $this->assertDatabaseHas('transactions', ['id' => $transaction->id]);
        $connection->refresh();
        $this->assertNotNull($connection->email_mailbox_id);
        $this->assertNotNull($connection->last_synced_at);
        $this->assertDatabaseHas('email_mailboxes', [
            'id' => $connection->email_mailbox_id,
            'email' => 'gmail@example.test',
        ]);
        $this->assertNotNull(EmailMailbox::query()->findOrFail($connection->email_mailbox_id)->backfill_completed_at);
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
