<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Api\V1\Concerns\SyncsUserOwnedResource;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreDebtRequest;
use App\Http\Resources\Api\V1\DebtResource;
use App\Models\Debt;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class DebtController extends Controller
{
    use SyncsUserOwnedResource;

    public function index(Request $request): AnonymousResourceCollection
    {
        return $this->indexSyncResource($request, Debt::class, DebtResource::class);
    }

    public function store(StoreDebtRequest $request): JsonResponse
    {
        return $this->upsertSyncResource($request, $request->validated(), Debt::class, DebtResource::class);
    }
}
