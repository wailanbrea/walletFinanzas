<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailClassificationRule extends Model
{
    protected $fillable = [
        'user_id', 'provider', 'type', 'sender_hash', 'subject_fingerprint', 'category',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
