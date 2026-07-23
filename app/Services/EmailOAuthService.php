<?php

namespace App\Services;

use App\Models\EmailConnection;
use App\Models\EmailOAuthState;
use App\Models\User;
use Illuminate\Http\Client\PendingRequest;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;
use RuntimeException;

class EmailOAuthService
{
    public const PROVIDERS = ['gmail', 'microsoft'];

    public function isReady(string $provider): bool
    {
        $config = $this->providerConfig($provider);

        return filled($config['client_id']) && filled($config['client_secret']) && filled($config['redirect_uri']);
    }

    public function authorizationUrl(User $user, string $provider): string
    {
        $this->ensureProvider($provider);
        if (! $this->isReady($provider)) {
            throw new RuntimeException('email_oauth_not_configured');
        }

        $state = Str::random(64);
        $verifier = $this->base64Url(random_bytes(64));
        EmailOAuthState::create([
            'user_id' => $user->id,
            'provider' => $provider,
            'state_hash' => hash('sha256', $state),
            'code_verifier' => $verifier,
            'expires_at' => now()->addMinutes((int) config('email_sync.state_ttl_minutes', 10)),
        ]);

        $challenge = $this->base64Url(hash('sha256', $verifier, true));
        $config = $this->providerConfig($provider);
        $parameters = [
            'client_id' => $config['client_id'],
            'redirect_uri' => $config['redirect_uri'],
            'response_type' => 'code',
            'scope' => $provider === 'gmail'
                ? 'openid email https://www.googleapis.com/auth/gmail.readonly'
                : 'openid email profile offline_access Mail.Read User.Read',
            'state' => $state,
            'code_challenge' => $challenge,
            'code_challenge_method' => 'S256',
        ];
        if ($provider === 'gmail') {
            $parameters += ['access_type' => 'offline', 'prompt' => 'consent'];
        }

        return $this->authorizationEndpoint($provider).'?'.http_build_query($parameters, '', '&', PHP_QUERY_RFC3986);
    }

    public function complete(string $provider, string $state, string $code): EmailConnection
    {
        $this->ensureProvider($provider);
        $oauthState = DB::transaction(function () use ($provider, $state): EmailOAuthState {
            $record = EmailOAuthState::query()
                ->where('state_hash', hash('sha256', $state))
                ->where('provider', $provider)
                ->whereNull('used_at')
                ->where('expires_at', '>', now())
                ->lockForUpdate()
                ->first();
            if (! $record) {
                throw new RuntimeException('invalid_oauth_state');
            }
            $record->update(['used_at' => now()]);

            return $record;
        });

        $config = $this->providerConfig($provider);
        $token = $this->http()->asForm()->post($this->tokenEndpoint($provider), [
            'client_id' => $config['client_id'],
            'client_secret' => $config['client_secret'],
            'redirect_uri' => $config['redirect_uri'],
            'grant_type' => 'authorization_code',
            'code' => $code,
            'code_verifier' => $oauthState->code_verifier,
        ])->throw()->json();

        $accessToken = $token['access_token'] ?? null;
        if (! is_string($accessToken) || $accessToken === '') {
            throw new RuntimeException('oauth_token_missing');
        }
        $email = $this->profileEmail($provider, $accessToken);
        $existing = EmailConnection::query()->where('user_id', $oauthState->user_id)->where('provider', $provider)->first();

        return EmailConnection::query()->updateOrCreate(
            ['user_id' => $oauthState->user_id, 'provider' => $provider],
            [
                'email' => $email,
                'status' => 'connected',
                'access_token' => $accessToken,
                'refresh_token' => $token['refresh_token'] ?? $existing?->refresh_token,
                'token_expires_at' => now()->addSeconds(max(0, (int) ($token['expires_in'] ?? 3600) - 30)),
                'connected_at' => now(),
            ]
        );
    }

    public function accessToken(EmailConnection $connection): string
    {
        if (! $connection->token_expires_at || $connection->token_expires_at->isFuture()) {
            return $connection->access_token;
        }
        if (! filled($connection->refresh_token)) {
            throw new RuntimeException('email_reauthorization_required');
        }

        $config = $this->providerConfig($connection->provider);
        $payload = [
            'client_id' => $config['client_id'],
            'client_secret' => $config['client_secret'],
            'grant_type' => 'refresh_token',
            'refresh_token' => $connection->refresh_token,
        ];
        if ($connection->provider === 'microsoft') {
            $payload['scope'] = 'openid email profile offline_access Mail.Read User.Read';
        }
        $token = $this->http()->asForm()->post($this->tokenEndpoint($connection->provider), $payload)->throw()->json();
        if (! is_string($token['access_token'] ?? null)) {
            throw new RuntimeException('oauth_token_missing');
        }
        $connection->update([
            'access_token' => $token['access_token'],
            'refresh_token' => $token['refresh_token'] ?? $connection->refresh_token,
            'token_expires_at' => now()->addSeconds(max(0, (int) ($token['expires_in'] ?? 3600) - 30)),
            'status' => 'connected',
        ]);

        return $connection->access_token;
    }

    public function ensureProvider(string $provider): void
    {
        abort_unless(in_array($provider, self::PROVIDERS, true), 422, 'Proveedor de correo no soportado.');
    }

    private function profileEmail(string $provider, string $accessToken): string
    {
        $response = $this->http()->withToken($accessToken)->get(
            $provider === 'gmail'
                ? 'https://gmail.googleapis.com/gmail/v1/users/me/profile'
                : 'https://graph.microsoft.com/v1.0/me',
            $provider === 'microsoft' ? ['$select' => 'mail,userPrincipalName'] : []
        )->throw()->json();
        $email = $provider === 'gmail' ? ($response['emailAddress'] ?? null) : ($response['mail'] ?? $response['userPrincipalName'] ?? null);
        if (! is_string($email) || ! filter_var($email, FILTER_VALIDATE_EMAIL)) {
            throw new RuntimeException('oauth_profile_invalid');
        }

        return strtolower($email);
    }

    private function providerConfig(string $provider): array
    {
        $key = $provider === 'gmail' ? 'google_oauth' : 'microsoft_oauth';

        return [
            'client_id' => config("services.$key.client_id"),
            'client_secret' => config("services.$key.client_secret"),
            'redirect_uri' => config("services.$key.redirect_uri"),
        ];
    }

    private function authorizationEndpoint(string $provider): string
    {
        return $provider === 'gmail'
            ? 'https://accounts.google.com/o/oauth2/v2/auth'
            : 'https://login.microsoftonline.com/'.$this->microsoftTenant().'/oauth2/v2.0/authorize';
    }

    private function tokenEndpoint(string $provider): string
    {
        return $provider === 'gmail'
            ? 'https://oauth2.googleapis.com/token'
            : 'https://login.microsoftonline.com/'.$this->microsoftTenant().'/oauth2/v2.0/token';
    }

    private function microsoftTenant(): string
    {
        $tenant = (string) config('services.microsoft_oauth.tenant_id', 'common');

        return preg_match('/\A[a-zA-Z0-9.-]+\z/', $tenant) ? $tenant : 'common';
    }

    private function http(): PendingRequest
    {
        return Http::acceptJson()->timeout(20)->retry(2, 200, throw: false);
    }

    private function base64Url(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }
}
