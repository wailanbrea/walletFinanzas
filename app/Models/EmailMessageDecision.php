<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailMessageDecision extends Model
{
    protected $fillable = [
        'email_mailbox_id', 'provider_message_id', 'status', 'category', 'decided_at',
    ];

    protected function casts(): array
    {
        return ['decided_at' => 'immutable_datetime'];
    }

    public function mailbox(): BelongsTo
    {
        return $this->belongsTo(EmailMailbox::class, 'email_mailbox_id');
    }
}
