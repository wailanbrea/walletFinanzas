<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class EmailConnectionApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_index_returns_both_provider_states_without_tokens(): void
    {
        config()->set('services.gmail', [
            'client_id' => '',
            'client_secret' => '',
            'redirect' => '',
        ]);
        config()->set('services.microsoft', [
            'client_id' => '',
            'client_secret' => '',
            'redirect' => '',
            'tenant' => 'common',
        ]);
        Sanctum::actingAs(User::factory()->create(), ['wallet']);

        $this->getJson('/api/v1/email-connections')
            ->assertOk()
            ->assertExactJson(['data' => [
                [
                    'provider' => 'gmail',
                    'display_name' => 'Gmail',
                    'status' => 'disconnected',
                    'email' => null,
                    'configuration_ready' => false,
                    'connected_at' => null,
                    'expires_at' => null,
                ],
                [
                    'provider' => 'microsoft',
                    'display_name' => 'Microsoft Outlook',
                    'status' => 'disconnected',
                    'email' => null,
                    'configuration_ready' => false,
                    'connected_at' => null,
                    'expires_at' => null,
                ],
            ]]);
    }

    public function test_authorization_requires_configuration_and_generates_one_time_pkce_state(): void
    {
        config()->set('services.gmail', [
            'client_id' => '',
            'client_secret' => '',
            'redirect' => '',
        ]);
        $user = User::factory()->create();
        Sanctum::actingAs($user, ['wallet']);

        $this->postJson('/api/v1/email-connections/gmail/authorization-url')
            ->assertStatus(503)
            ->assertJsonPath('message', 'La conexión con Gmail no está configurada en el servidor.');

        config()->set('services.gmail', [
            'client_id' => 'google-client',
            'client_secret' => 'google-secret',
            'redirect' => 'https://wallet.test/oauth/email/gmail/callback',
        ]);

        $response = $this->postJson('/api/v1/email-connections/gmail/authorization-url')
            ->assertOk()
            ->assertJsonStructure(['data' => ['authorization_url']]);

        parse_str(parse_url($response->json('data.authorization_url'), PHP_URL_QUERY), $query);
        $this->assertSame('google-client', $query['client_id']);
        $this->assertSame('S256', $query['code_challenge_method']);
        $this->assertSame('https://www.googleapis.com/auth/gmail.readonly', $query['scope']);
        $this->assertNotEmpty($query['code_challenge']);

        $state = DB::table('oauth_states')->where('user_id', $user->id)->first();
        $this->assertSame(hash('sha256', $query['state']), $state->state_hash);
        $this->assertNotSame($query['state'], $state->state_hash);
    }

    public function test_gmail_callback_exchanges_code_stores_encrypted_tokens_and_consumes_state(): void
    {
        config()->set('services.gmail', [
            'client_id' => 'google-client',
            'client_secret' => 'google-secret',
            'redirect' => 'https://wallet.test/oauth/email/gmail/callback',
        ]);
        $user = User::factory()->create();
        Sanctum::actingAs($user, ['wallet']);
        $authorization = $this->postJson('/api/v1/email-connections/gmail/authorization-url')->json('data.authorization_url');
        parse_str(parse_url($authorization, PHP_URL_QUERY), $query);

        Http::fake([
            'oauth2.googleapis.com/token' => Http::response([
                'access_token' => 'plain-access-token',
                'refresh_token' => 'plain-refresh-token',
                'expires_in' => 3600,
            ]),
            'gmail.googleapis.com/gmail/v1/users/me/profile' => Http::response(['emailAddress' => 'owner@gmail.com']),
        ]);

        $response = $this->get('/oauth/email/gmail/callback?'.http_build_query([
            'state' => $query['state'],
            'code' => 'authorization-code',
        ]));
        $response->assertOk()
            ->assertViewIs('oauth.email-connected')
            ->assertViewHas('returnUri', 'walletfinanzas://email-oauth?provider=gmail&status=connected')
            ->assertViewHas('intentUri', 'intent://email-oauth?provider=gmail&status=connected#Intent;scheme=walletfinanzas;package=com.bsolutions.wallet;end')
            ->assertSee('Volver a Wallet')
            ->assertDontSee('authorization-code', false)
            ->assertDontSee('plain-access-token', false);

        $raw = DB::table('email_connections')->where('user_id', $user->id)->first();
        $this->assertSame('owner@gmail.com', $raw->email);
        $this->assertStringNotContainsString('plain-access-token', $raw->access_token);
        $this->assertStringNotContainsString('plain-refresh-token', $raw->refresh_token);
        $this->assertDatabaseMissing('oauth_states', ['user_id' => $user->id]);

        $this->get('/oauth/email/gmail/callback?'.http_build_query([
            'state' => $query['state'],
            'code' => 'authorization-code',
        ]))->assertStatus(419);
    }

    public function test_microsoft_callback_uses_mail_read_and_graph_identity(): void
    {
        config()->set('services.microsoft', [
            'client_id' => 'ms-client',
            'client_secret' => 'ms-secret',
            'redirect' => 'https://wallet.test/oauth/email/microsoft/callback',
            'tenant' => 'common',
        ]);
        $user = User::factory()->create();
        Sanctum::actingAs($user, ['wallet']);
        $authorization = $this->postJson('/api/v1/email-connections/microsoft/authorization-url')
            ->assertOk()->json('data.authorization_url');
        parse_str(parse_url($authorization, PHP_URL_QUERY), $query);
        $this->assertStringContainsString('Mail.Read', $query['scope']);
        $this->assertStringContainsString('User.Read', $query['scope']);
        $this->assertStringContainsString('offline_access', $query['scope']);

        Http::fake([
            'login.microsoftonline.com/*' => Http::response([
                'access_token' => 'microsoft-access', 'refresh_token' => 'microsoft-refresh', 'expires_in' => 3600,
            ]),
            'graph.microsoft.com/*' => Http::response([
                'mail' => null, 'userPrincipalName' => 'owner@outlook.com',
            ]),
        ]);

        $response = $this->get('/oauth/email/microsoft/callback?'.http_build_query([
            'state' => $query['state'], 'code' => 'ms-code',
        ]));
        $response->assertOk()
            ->assertViewIs('oauth.email-connected')
            ->assertViewHas('returnUri', 'walletfinanzas://email-oauth?provider=microsoft&status=connected')
            ->assertViewHas('intentUri', 'intent://email-oauth?provider=microsoft&status=connected#Intent;scheme=walletfinanzas;package=com.bsolutions.wallet;end')
            ->assertSee('Volver a Wallet')
            ->assertDontSee('ms-code', false)
            ->assertDontSee('microsoft-access', false);

        $this->assertDatabaseHas('email_connections', [
            'user_id' => $user->id, 'provider' => 'microsoft', 'email' => 'owner@outlook.com',
        ]);
    }

    public function test_disconnect_is_idempotent_and_scoped_to_authenticated_user(): void
    {
        config()->set('services.microsoft', [
            'client_id' => 'ms-client',
            'client_secret' => 'ms-secret',
            'redirect' => 'https://wallet.test/oauth/email/microsoft/callback',
            'tenant' => 'common',
        ]);
        $owner = User::factory()->create();
        $other = User::factory()->create();
        $owner->emailConnections()->create([
            'provider' => 'microsoft', 'email' => 'owner@outlook.com',
            'access_token' => 'secret', 'refresh_token' => 'refresh',
            'status' => 'connected', 'connected_at' => now(),
        ]);

        Sanctum::actingAs($other, ['wallet']);
        $this->deleteJson('/api/v1/email-connections/microsoft')->assertNoContent();
        $this->assertDatabaseHas('email_connections', ['user_id' => $owner->id, 'status' => 'connected']);

        Sanctum::actingAs($owner, ['wallet']);
        $this->deleteJson('/api/v1/email-connections/microsoft')->assertNoContent();
        $this->deleteJson('/api/v1/email-connections/microsoft')->assertNoContent();
        $this->assertDatabaseHas('email_connections', [
            'user_id' => $owner->id,
            'provider' => 'microsoft',
            'status' => 'disconnected',
            'access_token' => null,
            'refresh_token' => null,
        ]);
    }
}
