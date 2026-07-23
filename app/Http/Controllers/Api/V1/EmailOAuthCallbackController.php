<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Services\EmailOAuthService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Throwable;

class EmailOAuthCallbackController extends Controller
{
    public function __construct(private EmailOAuthService $oauth) {}

    public function __invoke(Request $request, string $provider): RedirectResponse
    {
        $status = 'failed';
        try {
            $this->oauth->ensureProvider($provider);
            $state = $request->query('state');
            $code = $request->query('code');
            if (! is_string($state) || strlen($state) !== 64 || ! is_string($code) || $code === '' || strlen($code) > 4096 || $request->has('error')) {
                throw new \RuntimeException('invalid_oauth_callback');
            }
            $this->oauth->complete($provider, $state, $code);
            $status = 'connected';
        } catch (Throwable) {
            // The deep link deliberately exposes no provider or token error details.
        }

        $safeProvider = in_array($provider, EmailOAuthService::PROVIDERS, true) ? $provider : 'gmail';

        return redirect()->away('walletfinanzas://email-oauth?'.http_build_query([
            'provider' => $safeProvider,
            'status' => $status,
        ], '', '&', PHP_QUERY_RFC3986));
    }
}
