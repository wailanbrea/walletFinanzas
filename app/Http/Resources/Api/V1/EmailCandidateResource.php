<?php

namespace App\Http\Resources\Api\V1;

use App\Services\UsdDopExchangeRateService;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class EmailCandidateResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id' => (string) $this->id,
            'provider' => $this->provider,
            'merchant' => $this->merchant,
            'card_last_four' => $this->card_last_four,
            'amount' => $this->amount,
            'currency' => $this->currency,
            'converted_amount' => $this->converted_amount,
            'converted_currency' => $this->converted_currency,
            'exchange_rate_micros' => $this->exchange_rate_micros,
            'exchange_rate_at' => $this->exchange_rate_at?->toISOString(),
            'exchange_rate_source' => $this->exchange_rate_source,
            'conversion_kind' => match (true) {
                $this->currency !== 'USD', $this->converted_amount === null => null,
                $this->exchange_rate_source === UsdDopExchangeRateService::SOURCE => 'historical_estimate',
                default => 'latest_estimate',
            },
            'conversion_status' => $this->currency !== 'USD'
                ? 'not_required'
                : ($this->converted_amount === null ? 'unavailable' : 'available'),
            'direction' => $this->direction,
            'category_suggestion' => $this->category_suggestion,
            'occurred_at' => $this->occurred_at?->toISOString(),
            'confidence' => $this->confidence,
            'reasons' => $this->reasons,
            'status' => $this->status,
            'subject' => $this->message?->subject,
            'sender_email' => $this->message?->sender_email,
        ];
    }
}
