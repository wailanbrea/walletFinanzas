<?php

namespace App\Http\Resources\Api\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class EmailCandidateResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'provider' => $this->provider,
            'merchant' => $this->merchant,
            'amount' => $this->amount,
            'currency' => $this->currency,
            'direction' => $this->direction,
            'category_suggestion' => $this->category_suggestion,
            'occurred_at' => $this->occurred_at->toISOString(),
            'confidence' => $this->confidence,
            'status' => $this->status,
            'subject' => $this->subject,
            'converted_amount' => null,
            'converted_currency' => null,
            'exchange_rate_micros' => null,
            'exchange_rate_at' => null,
            'exchange_rate_source' => null,
            'conversion_kind' => null,
            'conversion_status' => null,
        ];
    }
}
