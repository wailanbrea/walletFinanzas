<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class GoalResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->client_id,
            'name' => $this->name,
            'icon' => $this->icon,
            'target_amount' => $this->target_amount,
            'saved_amount' => $this->saved_amount,
            'target_date' => $this->target_date,
            'is_completed' => $this->is_completed,
            'is_deleted' => $this->is_deleted,
            'updated_at' => $this->updated_at?->toISOString(),
        ];
    }
}
