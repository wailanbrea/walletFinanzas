<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\V1\EmailConnectionResource;
use App\Http\Resources\Api\V1\EmailSyncRunResource;
use App\Jobs\SyncEmailConnection;
use App\Models\EmailConnection;
use App\Models\EmailMailbox;
use App\Models\EmailOAuthState;
use App\Models\EmailSyncRun;
use App\Services\EmailOAuthService;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;
use RuntimeException;

class EmailConnectionController extends Controller
{
    public function __construct(private EmailOAuthService $oauth) {}

    public function index(Request $request): JsonResponse
    {
        $connections = $request->user()->emailConnections()->with('mailbox')->get()->keyBy('provider');
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
        $validated = $request->validate([
            'sync_from_at' => ['sometimes', 'nullable', 'date', 'before_or_equal:now', 'regex:/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/'],
            'sync_from_date' => ['sometimes', 'nullable', 'date_format:Y-m-d'],
        ]);
        $hasSyncFrom = filled($validated['sync_from_at'] ?? null);
        $hasSyncDate = filled($validated['sync_from_date'] ?? null);
        if ($hasSyncFrom !== $hasSyncDate) {
            throw ValidationException::withMessages([
                'sync_from_date' => ['La fecha local y su instante UTC deben enviarse juntas.'],
            ]);
        }
        if ($hasSyncFrom) {
            $syncFrom = CarbonImmutable::parse($validated['sync_from_at'])->utc();
            $localMidnight = CarbonImmutable::parse($validated['sync_from_date'].'T00:00:00Z');
            $possibleOffsetSeconds = $localMidnight->getTimestamp() - $syncFrom->getTimestamp();
            if ($possibleOffsetSeconds < -12 * 3600 || $possibleOffsetSeconds > 14 * 3600) {
                throw ValidationException::withMessages([
                    'sync_from_date' => ['La fecha local no corresponde al inicio del dia enviado.'],
                ]);
            }
        }

        $outcome = DB::transaction(function () use ($request, $provider, $validated, $hasSyncFrom): array {
            $connection = EmailConnection::query()
                ->where('user_id', $request->user()->id)
                ->where('provider', $provider)
                ->where('status', 'connected')
                ->lockForUpdate()
                ->first();
            if (! $connection) {
                return ['error' => 'email_connection_not_found'];
            }
            $activeRun = EmailSyncRun::query()
                ->where('user_id', $request->user()->id)
                ->where('email_connection_id', $connection->id)
                ->whereIn('status', ['queued', 'running'])
                ->latest('id')
                ->first();
            if ($activeRun) {
                return $hasSyncFrom
                    ? ['error' => 'email_sync_already_running']
                    : ['run' => $activeRun, 'dispatch' => $activeRun->status === 'queued' && $activeRun->updated_at->lte(now()->subSeconds(30))];
            }

            $mailbox = EmailMailbox::forConnection($connection);
            $requestedFrom = $hasSyncFrom
                ? CarbonImmutable::parse($validated['sync_from_at'])->utc()
                : $mailbox->sync_from_at;
            $syncFrom = $requestedFrom ?? CarbonImmutable::now('UTC')->subDays(90)->startOfDay();
            $syncDate = $hasSyncFrom ? $validated['sync_from_date'] : ($mailbox->sync_from_date?->toDateString() ?? $syncFrom->toDateString());
            if (! $mailbox->sync_from_at
                || ! $mailbox->sync_from_at->equalTo($syncFrom)
                || $mailbox->sync_from_date?->toDateString() !== $syncDate) {
                $mailbox->update([
                    'sync_from_date' => $syncDate,
                    'sync_from_at' => $syncFrom,
                    'backfill_before_at' => CarbonImmutable::now('UTC'),
                    'backfill_cursor' => null,
                    'backfill_completed_at' => null,
                    'incremental_from_at' => null,
                    'incremental_before_at' => null,
                    'incremental_cursor' => null,
                ]);
            }
            $run = EmailSyncRun::query()->create([
                'user_id' => $request->user()->id,
                'email_connection_id' => $connection->id,
                'provider' => $provider,
                'sync_from_at' => $syncFrom,
                'status' => 'queued',
            ]);

            return ['run' => $run, 'dispatch' => true];
        });
        if (($outcome['error'] ?? null) === 'email_connection_not_found') {
            return response()->json(['message' => 'No hay una conexion de correo autorizada para este proveedor.', 'code' => 'email_connection_not_found'], 409);
        }
        if (($outcome['error'] ?? null) === 'email_sync_already_running') {
            return response()->json([
                'message' => 'Ya hay una sincronizacion en curso para esta cuenta.',
                'code' => 'email_sync_already_running',
            ], 409);
        }

        /** @var EmailSyncRun $run */
        $run = $outcome['run'];
        if ($outcome['dispatch']) {
            SyncEmailConnection::dispatch($run->id);
        }
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
