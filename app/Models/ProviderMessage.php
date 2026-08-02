<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasOne;

class ProviderMessage extends Model
{
    protected $fillable = [
        'user_id', 'email_connection_id', 'provider', 'provider_message_id', 'subject',
        'snippet', 'sender_name', 'sender_address', 'sender_domain', 'occurred_at',
    ];

    protected function casts(): array
    {
        return ['occurred_at' => 'immutable_datetime'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function connection(): BelongsTo
    {
        return $this->belongsTo(EmailConnection::class, 'email_connection_id');
    }

    public function candidate(): HasOne
    {
        return $this->hasOne(EmailCandidate::class);
    }
}
