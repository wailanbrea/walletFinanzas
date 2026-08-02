<?php

namespace App\Http\Resources\Api\V1;

use App\Services\DuplicateEmailCandidateDetector;
use App\Services\UsdDopExchangeRateService;
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
            'card_last_four' => $this->card_last_four,
            'amount' => $this->amount,
            'currency' => $this->currency,
            'direction' => $this->direction,
            'event_type' => $this->event_type,
            'sender_name' => $this->message?->sender_name,
            'sender_address' => $this->message?->sender_address,
            'sender_domain' => $this->message?->sender_domain,
            'category_suggestion' => $this->category_suggestion,
            'occurred_at' => $this->occurred_at->toISOString(),
            'confidence' => $this->confidence,
            'status' => $this->status,
            'subject' => $this->subject,
            'converted_amount' => $this->converted_amount,
            'converted_currency' => $this->converted_currency,
            'exchange_rate_micros' => $this->exchange_rate_micros,
            'exchange_rate_at' => $this->exchange_rate_at?->toISOString(),
            'exchange_rate_source' => $this->exchange_rate_source,
            'conversion_kind' => $this->conversionKind(),
            // El cliente lo necesita explícito: sin esto no distingue "no hacía falta
            // convertir" de "no se pudo", y en ambos casos dejaba el cargo sin poder
            // clasificar sin decir por qué.
            'conversion_status' => $this->conversionStatus(),
            'duplicate_of_id' => $this->duplicate_of_id,
        ];
    }

    /** De dónde sale la tasa aplicada; null si no hubo conversión. */
    private function conversionKind(): ?string
    {
        if ($this->converted_amount === null) {
            return null;
        }

        return $this->exchange_rate_source === UsdDopExchangeRateService::SOURCE
            ? 'historical_estimate'
            : 'latest_estimate';
    }

    /** 'not_required' si ya venía en DOP, 'available' si se convirtió, 'unavailable' si no se pudo. */
    private function conversionStatus(): string
    {
        if ($this->currency === DuplicateEmailCandidateDetector::BASE_CURRENCY) {
            return 'not_required';
        }

        return $this->converted_amount === null ? 'unavailable' : 'available';
    }
}
