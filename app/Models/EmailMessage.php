<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasOne;

class EmailMessage extends Model
{
    protected $fillable = [
        'user_id', 'email_connection_id', 'provider_message_id', 'internet_message_id',
        'sender_email', 'sender_name', 'subject', 'received_at', 'body_excerpt',
        'content_hash', 'status',
    ];

    protected function casts(): array
    {
        return ['received_at' => 'immutable_datetime'];
    }

    public function connection(): BelongsTo
    {
        return $this->belongsTo(EmailConnection::class, 'email_connection_id');
    }

    public function candidate(): HasOne
    {
        return $this->hasOne(FinancialTransactionCandidate::class);
    }
}
