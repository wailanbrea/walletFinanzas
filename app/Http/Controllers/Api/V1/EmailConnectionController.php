<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;

class EmailConnectionController extends Controller
{
    public function index(): JsonResponse
    {
        return response()->json([
            'data' => [
                $this->connection('gmail', 'Gmail'),
                $this->connection('microsoft', 'Microsoft'),
            ],
        ]);
    }

    public function authorizationUrl(string $provider): JsonResponse
    {
        $this->provider($provider);

        return response()->json([
            'message' => 'La autorizacion OAuth para este proveedor aun no esta configurada.',
            'code' => 'email_oauth_not_configured',
        ], 503);
    }

    public function sync(string $provider): JsonResponse
    {
        $this->provider($provider);

        return response()->json([
            'message' => 'No hay una conexion de correo autorizada para este proveedor.',
            'code' => 'email_connection_not_found',
        ], 409);
    }

    public function syncRun(string $provider, int $run): JsonResponse
    {
        $this->provider($provider);

        return response()->json([
            'message' => 'La ejecucion de sincronizacion no existe.',
            'code' => 'email_sync_run_not_found',
        ], 404);
    }

    public function destroy(string $provider): Response
    {
        $this->provider($provider);

        return response()->noContent();
    }

    public function candidates(): JsonResponse
    {
        return response()->json(['data' => []]);
    }

    public function reviewCandidate(Request $request, string $candidate): JsonResponse
    {
        $request->validate([
            'action' => ['required', 'in:categorize,dismiss'],
            'category' => ['nullable', 'string', 'max:120'],
            'learn' => ['sometimes', 'boolean'],
        ]);

        return response()->json([
            'message' => 'El candidato de correo no existe.',
            'code' => 'email_candidate_not_found',
        ], 404);
    }

    private function connection(string $provider, string $displayName): array
    {
        return [
            'provider' => $provider,
            'display_name' => $displayName,
            'status' => 'disconnected',
            'email' => null,
            'configuration_ready' => false,
            'connected_at' => null,
            'expires_at' => null,
        ];
    }

    private function provider(string $provider): void
    {
        abort_unless(in_array($provider, ['gmail', 'microsoft'], true), 422, 'Proveedor de correo no soportado.');
    }
}
