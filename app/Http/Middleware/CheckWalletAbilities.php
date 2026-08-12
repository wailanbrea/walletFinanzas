<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Auth\AuthenticationException;
use Laravel\Sanctum\Exceptions\MissingAbilityException;

class CheckWalletAbilities
{
    public function handle($request, Closure $next, ...$abilities)
    {
        $user = $request->user();
        $token = $user?->currentAccessToken();

        if (! $user || ! $token) {
            throw new AuthenticationException;
        }

        // Tokens created by older app versions have no abilities column value.
        // Keep those first-party tokens working; Hermes tokens are always scoped.
        $tokenAbilities = $token->abilities ?? [];
        if ($tokenAbilities !== [] && ! in_array('*', $tokenAbilities, true)) {
            foreach ($abilities as $ability) {
                if (! in_array($ability, $tokenAbilities, true)) {
                    throw new MissingAbilityException($ability);
                }
            }
        }

        return $next($request);
    }
}
