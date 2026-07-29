<?php

namespace App\Services;

use App\Models\EmailConnection;
use App\Models\OauthState;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;

class EmailOAuthService
{
    public const PROVIDERS = [
        'gmail' => ['display_name' => 'Gmail'],
        'microsoft' => ['display_name' => 'Microsoft Outlook'],
    ];

    public function supports(string $provider): bool
    {
        return array_key_exists($provider, self::PROVIDERS);
    }

    public function isConfigured(string $provider): bool
    {
        if (! $this->supports($provider)) {
            return false;
        }

        $config = config("services.$provider", []);

        return filled($config['client_id'] ?? null)
            && filled($config['client_secret'] ?? null)
            && filled($config['redirect'] ?? null);
    }

    public function authorizationUrl(User $user, string $provider): string
    {
        $state = Str::random(64);
        $verifier = Str::random(96);
        $challenge = rtrim(strtr(base64_encode(hash('sha256', $verifier, true)), '+/', '-_'), '=');

        $user->oauthStates()->where('provider', $provider)->delete();
        $user->oauthStates()->create([
            'provider' => $provider,
            'state_hash' => hash('sha256', $state),
            'code_verifier' => $verifier,
            'expires_at' => now()->addMinutes(10),
        ]);

        $config = config("services.$provider");
        $params = [
            'client_id' => $config['client_id'],
            'redirect_uri' => $config['redirect'],
            'response_type' => 'code',
            'scope' => $provider === 'gmail'
                ? 'https://www.googleapis.com/auth/gmail.readonly'
                : 'openid email offline_access Mail.Read User.Read',
            'state' => $state,
            'code_challenge' => $challenge,
            'code_challenge_method' => 'S256',
        ];

        if ($provider === 'gmail') {
            $params += ['access_type' => 'offline', 'prompt' => 'consent'];
        }

        return $this->authorizationEndpoint($provider).'?'.http_build_query($params, '', '&', PHP_QUERY_RFC3986);
    }

    public function complete(string $provider, string $plainState, string $code): void
    {
        $state = DB::transaction(function () use ($provider, $plainState): OauthState {
            $state = OauthState::query()
                ->where('provider', $provider)
                ->where('state_hash', hash('sha256', $plainState))
                ->lockForUpdate()
                ->first();

            abort_if(! $state || $state->expires_at->isPast(), 419, 'El estado OAuth es inválido o expiró.');
            $state->delete();

            return $state;
        });

        $config = config("services.$provider");
        $token = Http::asForm()->post($this->tokenEndpoint($provider), [
            'client_id' => $config['client_id'],
            'client_secret' => $config['client_secret'],
            'redirect_uri' => $config['redirect'],
            'grant_type' => 'authorization_code',
            'code' => $code,
            'code_verifier' => $state->code_verifier,
        ])->throw()->json();

        $accessToken = $token['access_token'];
        $email = $this->fetchEmail($provider, $accessToken);
        $existing = $state->user->emailConnections()->where('provider', $provider)->first();

        $state->user->emailConnections()->updateOrCreate(['provider' => $provider], [
            'email' => $email,
            'access_token' => $accessToken,
            'refresh_token' => $token['refresh_token'] ?? $existing?->refresh_token,
            'status' => 'connected',
            'connected_at' => now(),
            'expires_at' => isset($token['expires_in']) ? now()->addSeconds((int) $token['expires_in']) : null,
        ]);
    }

    public function disconnect(User $user, string $provider): void
    {
        $connection = $user->emailConnections()->where('provider', $provider)->first();
        if (! $connection) {
            return;
        }

        if ($provider === 'gmail' && filled($connection->access_token)) {
            try {
                Http::asForm()->timeout(5)->post('https://oauth2.googleapis.com/revoke', [
                    'token' => $connection->access_token,
                ]);
            } catch (\Throwable) {
                // Local disconnection remains reliable when external revocation is unavailable.
            }
        }

        $connection->update([
            'access_token' => null,
            'refresh_token' => null,
            'expires_at' => null,
            'status' => 'disconnected',
        ]);
    }

    public function accessTokenFor(EmailConnection $connection): string
    {
        if (filled($connection->access_token)
            && (! $connection->expires_at || $connection->expires_at->isAfter(now()->addMinute()))) {
            return $connection->access_token;
        }

        abort_unless(filled($connection->refresh_token), 409, 'La conexión de correo expiró. Vuelve a conectarla.');
        $config = config("services.{$connection->provider}");
        $token = Http::asForm()->timeout(20)->post($this->tokenEndpoint($connection->provider), [
            'client_id' => $config['client_id'],
            'client_secret' => $config['client_secret'],
            'refresh_token' => $connection->refresh_token,
            'grant_type' => 'refresh_token',
        ])->throw()->json();

        $connection->update([
            'access_token' => $token['access_token'],
            'refresh_token' => $token['refresh_token'] ?? $connection->refresh_token,
            'expires_at' => isset($token['expires_in']) ? now()->addSeconds((int) $token['expires_in']) : null,
            'status' => 'connected',
        ]);
        $connection->refresh();

        return $connection->access_token;
    }

    private function fetchEmail(string $provider, string $accessToken): string
    {
        $response = Http::withToken($accessToken)->get($provider === 'gmail'
            ? 'https://gmail.googleapis.com/gmail/v1/users/me/profile'
            : 'https://graph.microsoft.com/v1.0/me?$select=mail,userPrincipalName')->throw()->json();

        return $provider === 'gmail'
            ? $response['emailAddress']
            : ($response['mail'] ?? $response['userPrincipalName']);
    }

    private function authorizationEndpoint(string $provider): string
    {
        return $provider === 'gmail'
            ? 'https://accounts.google.com/o/oauth2/v2/auth'
            : 'https://login.microsoftonline.com/'.config('services.microsoft.tenant', 'common').'/oauth2/v2.0/authorize';
    }

    private function tokenEndpoint(string $provider): string
    {
        return $provider === 'gmail'
            ? 'https://oauth2.googleapis.com/token'
            : 'https://login.microsoftonline.com/'.config('services.microsoft.tenant', 'common').'/oauth2/v2.0/token';
    }
}
