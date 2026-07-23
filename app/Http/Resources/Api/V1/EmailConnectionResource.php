<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class EmailConnectionResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'provider' => $this->provider,
            'display_name' => $this->provider === 'gmail' ? 'Gmail' : 'Microsoft',
            'status' => $this->status,
            'email' => $this->email,
            'configuration_ready' => (bool) ($this->configuration_ready ?? true),
            'connected_at' => $this->connected_at?->toISOString(),
            'expires_at' => $this->token_expires_at?->toISOString(),
        ];
    }
}
