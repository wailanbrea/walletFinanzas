<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailSyncRun extends Model
{
    protected $fillable = ['user_id', 'email_connection_id', 'provider', 'sync_from_at', 'status', 'messages_discovered', 'messages_created', 'candidates_created', 'conversions_backfilled', 'error_code', 'started_at', 'finished_at'];

    protected function casts(): array
    {
        return [
            'sync_from_at' => 'immutable_datetime',
            'started_at' => 'immutable_datetime',
            'finished_at' => 'immutable_datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function connection(): BelongsTo
    {
        return $this->belongsTo(EmailConnection::class, 'email_connection_id');
    }
}
