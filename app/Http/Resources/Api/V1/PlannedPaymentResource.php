<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class PlannedPaymentResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->client_id,
            'name' => $this->name,
            'account_id' => $this->account_id,
            'category_id' => $this->category_id,
            'amount' => $this->amount,
            'type' => $this->type,
            'frequency' => $this->frequency,
            'next_due_date' => $this->next_due_date,
            'is_active' => $this->is_active,
            'is_deleted' => $this->is_deleted,
            'updated_at' => $this->updated_at?->toISOString(),
        ];
    }
}
