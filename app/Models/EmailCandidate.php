<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailCandidate extends Model
{
    use HasUuids;

    protected $fillable = [
        'user_id', 'provider_message_id', 'provider', 'merchant', 'card_last_four',
        'amount', 'currency', 'direction', 'category_suggestion', 'occurred_at',
        'confidence', 'status', 'subject', 'category',
        // Instantanea de la conversion: la tasa se guarda con el movimiento para que
        // un gasto pasado siga valiendo lo que valia ese dia.
        'converted_amount', 'converted_currency', 'exchange_rate_micros',
        'exchange_rate_at', 'exchange_rate_source',
        // Duplicado: el mismo cargo visto por dos proveedores de correo.
        'duplicate_of_id',
    ];

    protected function casts(): array
    {
        return [
            'amount' => 'integer',
            'confidence' => 'integer',
            'occurred_at' => 'immutable_datetime',
            'converted_amount' => 'integer',
            'exchange_rate_micros' => 'integer',
            'exchange_rate_at' => 'immutable_datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function message(): BelongsTo
    {
        return $this->belongsTo(ProviderMessage::class, 'provider_message_id');
    }
}
