<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Api\V1\Concerns\SyncsUserOwnedResource;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StorePlannedPaymentRequest;
use App\Http\Resources\Api\V1\PlannedPaymentResource;
use App\Models\PlannedPayment;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class PlannedPaymentController extends Controller
{
    use SyncsUserOwnedResource;

    public function index(Request $request): AnonymousResourceCollection
    {
        return $this->indexSyncResource($request, PlannedPayment::class, PlannedPaymentResource::class);
    }

    public function store(StorePlannedPaymentRequest $request): JsonResponse
    {
        return $this->upsertSyncResource(
            $request,
            $request->validated(),
            PlannedPayment::class,
            PlannedPaymentResource::class
        );
    }
}
