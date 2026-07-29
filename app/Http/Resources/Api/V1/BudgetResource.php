<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class BudgetResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->client_id,
            'category_id' => $this->category_id,
            'limit_amount' => $this->limit_amount,
            'spent_amount' => $this->spent_amount,
            'period' => $this->period,
            'is_deleted' => $this->is_deleted,
            'updated_at' => $this->updated_at?->toISOString(),
        ];
    }
}
