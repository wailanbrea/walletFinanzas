<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\V1\EmailConnectionResource;
use App\Http\Resources\Api\V1\EmailSyncRunResource;
use App\Jobs\SyncEmailConnection;
use App\Models\EmailConnection;
use App\Models\EmailOAuthState;
use App\Models\EmailSyncRun;
use App\Services\EmailOAuthService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use RuntimeException;

class EmailConnectionController extends Controller
{
    public function __construct(private EmailOAuthService $oauth) {}

    public function index(Request $request): JsonResponse
    {
        $connections = $request->user()->emailConnections->keyBy('provider');
        $data = collect(EmailOAuthService::PROVIDERS)->map(function (string $provider) use ($connections) {
            $connection = $connections->get($provider) ?? new EmailConnection([
                'provider' => $provider,
                'status' => 'disconnected',
            ]);
            $connection->configuration_ready = $this->oauth->isReady($provider);

            return (new EmailConnectionResource($connection))->resolve();
        });

        return response()->json(['data' => $data]);
    }

    public function authorizationUrl(Request $request, string $provider): JsonResponse
    {
        $this->oauth->ensureProvider($provider);
        try {
            $url = $this->oauth->authorizationUrl($request->user(), $provider);
        } catch (RuntimeException $exception) {
            return response()->json([
                'message' => 'La autorizacion OAuth para este proveedor no esta configurada.',
                'code' => $exception->getMessage(),
            ], 503);
        }

        return response()->json(['data' => ['authorization_url' => $url]]);
    }

    public function sync(Request $request, string $provider): JsonResponse
    {
        $this->oauth->ensureProvider($provider);
        $connection = $request->user()->emailConnections()->where('provider', $provider)->where('status', 'connected')->first();
        if (! $connection) {
            return response()->json(['message' => 'No hay una conexion de correo autorizada para este proveedor.', 'code' => 'email_connection_not_found'], 409);
        }
        $activeRun = $request->user()->emailSyncRuns()
            ->where('email_connection_id', $connection->id)
            ->whereIn('status', ['queued', 'running'])
            ->latest('id')
            ->first();
        if ($activeRun) {
            return (new EmailSyncRunResource($activeRun))->response()->setStatusCode(202);
        }
        $run = EmailSyncRun::query()->create([
            'user_id' => $request->user()->id,
            'email_connection_id' => $connection->id,
            'provider' => $provider,
            'status' => 'queued',
        ]);
        SyncEmailConnection::dispatch($run->id);
        $run->refresh();

        return (new EmailSyncRunResource($run))->response()->setStatusCode(202);
    }

    public function syncRun(Request $request, string $provider, int $run): JsonResponse|EmailSyncRunResource
    {
        $this->oauth->ensureProvider($provider);
        $syncRun = $request->user()->emailSyncRuns()->where('provider', $provider)->find($run);
        if (! $syncRun) {
            return response()->json(['message' => 'La ejecucion de sincronizacion no existe.', 'code' => 'email_sync_run_not_found'], 404);
        }

        return new EmailSyncRunResource($syncRun);
    }

    public function destroy(Request $request, string $provider): Response
    {
        $this->oauth->ensureProvider($provider);
        $request->user()->emailConnections()->where('provider', $provider)->delete();
        EmailOAuthState::query()->where('user_id', $request->user()->id)->where('provider', $provider)->delete();

        return response()->noContent();
    }
}
