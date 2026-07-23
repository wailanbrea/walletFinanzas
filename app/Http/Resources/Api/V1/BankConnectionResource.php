<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class BankConnectionResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'provider_name' => $this->provider_name,
            'provider_code' => $this->provider_code,
            'country_code' => $this->country_code,
            'status' => $this->status,
            'last_sync_at' => $this->last_sync_at?->toISOString(),
        ];
    }
}
