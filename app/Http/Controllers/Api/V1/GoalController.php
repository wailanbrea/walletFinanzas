<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Api\V1\Concerns\SyncsUserOwnedResource;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreGoalRequest;
use App\Http\Resources\Api\V1\GoalResource;
use App\Models\Goal;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class GoalController extends Controller
{
    use SyncsUserOwnedResource;

    public function index(Request $request): AnonymousResourceCollection
    {
        return $this->indexSyncResource($request, Goal::class, GoalResource::class);
    }

    public function store(StoreGoalRequest $request): JsonResponse
    {
        return $this->upsertSyncResource($request, $request->validated(), Goal::class, GoalResource::class);
    }
}
