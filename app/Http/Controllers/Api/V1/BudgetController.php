<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Api\V1\Concerns\SyncsUserOwnedResource;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreBudgetRequest;
use App\Http\Resources\Api\V1\BudgetResource;
use App\Models\Budget;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class BudgetController extends Controller
{
    use SyncsUserOwnedResource;

    public function index(Request $request): AnonymousResourceCollection
    {
        return $this->indexSyncResource($request, Budget::class, BudgetResource::class);
    }

    public function store(StoreBudgetRequest $request): JsonResponse
    {
        return $this->upsertSyncResource($request, $request->validated(), Budget::class, BudgetResource::class);
    }
}
