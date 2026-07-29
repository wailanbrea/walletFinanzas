<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class DebtResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->client_id,
            'name' => $this->name,
            'description' => $this->description,
            'direction' => $this->direction,
            'total_amount' => $this->total_amount,
            'paid_amount' => $this->paid_amount,
            'due_date' => $this->due_date,
            'is_closed' => $this->is_closed,
            'is_deleted' => $this->is_deleted,
            'updated_at' => $this->updated_at?->toISOString(),
        ];
    }
}
