<?php

namespace App\Http\Controllers\Api\V1\Concerns;

use Carbon\CarbonImmutable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Resources\Json\JsonResource;
use Illuminate\Support\Facades\DB;

trait SyncsUserOwnedResource
{
    /** @param class-string<Model> $modelClass @param class-string<JsonResource> $resourceClass */
    protected function indexSyncResource(
        Request $request,
        string $modelClass,
        string $resourceClass
    ): AnonymousResourceCollection {
        $validated = $request->validate([
            'updated_since' => ['sometimes', 'date'],
            'per_page' => ['sometimes', 'integer', 'min:1', 'max:200'],
        ]);

        $query = $modelClass::query()
            ->where('user_id', $request->user()->id)
            ->orderBy('updated_at')
            ->orderBy('id');

        if (! empty($validated['updated_since'])) {
            $query->where('updated_at', '>', CarbonImmutable::parse($validated['updated_since']));
        }

        return $resourceClass::collection(
            $query->cursorPaginate($validated['per_page'] ?? 100)->withQueryString()
        );
    }

    /** @param class-string<Model> $modelClass @param class-string<JsonResource> $resourceClass */
    protected function upsertSyncResource(
        Request $request,
        array $data,
        string $modelClass,
        string $resourceClass
    ): JsonResponse {
        $clientId = $data['id'];
        unset($data['id'], $data['updated_at']);

        [$resource, $created] = DB::transaction(function () use ($request, $modelClass, $clientId, $data): array {
            $user = $request->user()->newQuery()
                ->whereKey($request->user()->getKey())
                ->lockForUpdate()
                ->firstOrFail();
            $resource = $modelClass::query()
                ->where('user_id', $user->id)
                ->where('client_id', $clientId)
                ->lockForUpdate()
                ->first();
            $created = $resource === null;
            $resource ??= new $modelClass([
                'user_id' => $user->id,
                'client_id' => $clientId,
            ]);
            $resource->fill($data);
            if (! $resource->exists || $resource->isDirty()) {
                $resource->save();
            }

            return [$resource, $created];
        });

        return response()->json([
            'data' => (new $resourceClass($resource))->resolve($request),
        ], $created ? 201 : 200);
    }
}
