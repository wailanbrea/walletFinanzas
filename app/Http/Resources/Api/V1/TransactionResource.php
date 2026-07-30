<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class TransactionResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'idempotency_key' => $this->idempotency_key,
            'account_id' => $this->account_id,
            'amount' => $this->amount,
            'currency' => $this->currency,
            'description' => $this->description,
            'category_id' => $this->category_id,
            'debt_id' => $this->debt_id,
            'timestamp' => $this->occurred_at?->toISOString(),
            'status' => $this->status,
            'created_at' => $this->created_at?->toISOString(),
            'updated_at' => $this->updated_at?->toISOString(),
        ];
    }
}
