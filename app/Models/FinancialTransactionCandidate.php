<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class FinancialTransactionCandidate extends Model
{
    protected $fillable = [
        'user_id', 'email_message_id', 'provider', 'merchant', 'card_last_four', 'amount', 'currency',
        'converted_amount', 'converted_currency', 'exchange_rate_micros', 'exchange_rate_at',
        'exchange_rate_source', 'direction', 'category_suggestion', 'occurred_at', 'confidence',
        'reasons', 'status',
    ];

    protected function casts(): array
    {
        return [
            'amount' => 'integer',
            'converted_amount' => 'integer',
            'exchange_rate_micros' => 'integer',
            'exchange_rate_at' => 'immutable_datetime',
            'confidence' => 'integer',
            'reasons' => 'array',
            'occurred_at' => 'immutable_datetime',
        ];
    }

    public function message(): BelongsTo
    {
        return $this->belongsTo(EmailMessage::class, 'email_message_id');
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
