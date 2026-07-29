<?php

namespace App\Http\Controllers;

use App\Services\EmailOAuthService;
use Illuminate\Http\Request;
use Illuminate\View\View;

class EmailOAuthCallbackController extends Controller
{
    public function __invoke(Request $request, string $provider, EmailOAuthService $oauth): View
    {
        abort_unless($oauth->supports($provider), 404);
        $request->validate(['state' => ['required', 'string'], 'code' => ['required', 'string']]);
        $oauth->complete($provider, $request->string('state')->toString(), $request->string('code')->toString());

        $query = http_build_query([
            'provider' => $provider,
            'status' => 'connected',
        ], '', '&', PHP_QUERY_RFC3986);

        return view('oauth.email-connected', [
            'provider' => EmailOAuthService::PROVIDERS[$provider]['display_name'],
            'returnUri' => 'walletfinanzas://email-oauth?'.$query,
            'intentUri' => 'intent://email-oauth?'.$query.'#Intent;scheme=walletfinanzas;package=com.bsolutions.wallet;end',
        ]);
    }
}
