<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Services\EmailOAuthService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
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
        } catch (Throwable $exception) {
            Log::warning('email_oauth_callback_failed', [
                'provider' => $provider,
                'message' => $exception->getMessage(),
                'exception' => $exception::class,
                'has_error_query' => $request->has('error'),
                'error_query' => $request->query('error'),
                'state_length' => is_string($request->query('state')) ? strlen($request->query('state')) : null,
                'code_present' => is_string($request->query('code')) && $request->query('code') !== '',
            ]);
            // The deep link deliberately exposes no provider or token error details.
        }

        $safeProvider = in_array($provider, EmailOAuthService::PROVIDERS, true) ? $provider : 'gmail';

        return redirect()->away('walletfinanzas://email-oauth?'.http_build_query([
            'provider' => $safeProvider,
            'status' => $status,
        ], '', '&', PHP_QUERY_RFC3986));
    }
}
