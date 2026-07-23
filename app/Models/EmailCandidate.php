<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailCandidate extends Model
{
    use HasUuids;

    protected $fillable = ['user_id', 'provider_message_id', 'provider', 'merchant', 'amount', 'currency', 'direction', 'category_suggestion', 'occurred_at', 'confidence', 'status', 'subject', 'category'];

    protected function casts(): array
    {
        return ['amount' => 'integer', 'confidence' => 'integer', 'occurred_at' => 'immutable_datetime'];
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
