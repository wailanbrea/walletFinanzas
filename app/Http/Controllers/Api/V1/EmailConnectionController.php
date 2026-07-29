<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\EmailSyncRun;
use App\Services\EmailOAuthService;
use App\Services\EmailSyncService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;

class EmailConnectionController extends Controller
{
    public function __construct(
        private readonly EmailOAuthService $oauth,
        private readonly EmailSyncService $syncService,
    ) {}

    public function index(Request $request): JsonResponse
    {
        $connections = $request->user()->emailConnections->keyBy('provider');

        return response()->json(['data' => collect(EmailOAuthService::PROVIDERS)
            ->map(function (array $definition, string $provider) use ($connections): array {
                $connection = $connections->get($provider);

                return [
                    'provider' => $provider,
                    'display_name' => $definition['display_name'],
                    'status' => $connection?->status ?? 'disconnected',
                    'email' => $connection?->email,
                    'configuration_ready' => $this->oauth->isConfigured($provider),
                    'connected_at' => $connection?->connected_at?->toISOString(),
                    'expires_at' => $connection?->expires_at?->toISOString(),
                ];
            })->values()->all()]);
    }

    public function authorizationUrl(Request $request, string $provider): JsonResponse
    {
        abort_unless($this->oauth->supports($provider), 404);
        if (! $this->oauth->isConfigured($provider)) {
            return response()->json([
                'message' => 'La conexión con '.EmailOAuthService::PROVIDERS[$provider]['display_name'].' no está configurada en el servidor.',
            ], 503);
        }

        return response()->json(['data' => [
            'authorization_url' => $this->oauth->authorizationUrl($request->user(), $provider),
        ]]);
    }

    /** Encola la sincronización (202) y devuelve el id del run para sondear el estado. */
    public function sync(Request $request, string $provider): JsonResponse
    {
        $run = $this->syncService->queue($request->user(), $provider);

        return response()->json(['data' => $this->runPayload($run)], 202);
    }

    /** Estado de una sincronización encolada (para el sondeo del cliente). */
    public function syncRun(Request $request, string $provider, EmailSyncRun $run): JsonResponse
    {
        abort_unless($run->user_id === $request->user()->id, 404);

        return response()->json(['data' => $this->runPayload($run)]);
    }

    /** @return array<string, mixed> */
    private function runPayload(EmailSyncRun $run): array
    {
        return [
            'sync_run_id' => $run->id,
            'status' => $run->status,
            'messages_discovered' => (int) $run->messages_discovered,
            'messages_created' => (int) $run->messages_created,
            'candidates_created' => (int) $run->candidates_created,
            'error_code' => $run->error_code,
        ];
    }

    public function destroy(Request $request, string $provider): Response
    {
        abort_unless($this->oauth->supports($provider), 404);
        $this->oauth->disconnect($request->user(), $provider);

        return response()->noContent();
    }
}
