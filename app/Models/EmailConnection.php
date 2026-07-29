<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class EmailConnection extends Model
{
    protected $fillable = [
        'user_id', 'provider', 'email', 'access_token', 'refresh_token',
        'status', 'connected_at', 'expires_at', 'last_sync_at', 'last_sync_status',
    ];

    protected $hidden = ['access_token', 'refresh_token'];

    protected function casts(): array
    {
        return [
            'access_token' => 'encrypted',
            'refresh_token' => 'encrypted',
            'connected_at' => 'immutable_datetime',
            'expires_at' => 'immutable_datetime',
            'last_sync_at' => 'immutable_datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function messages(): HasMany
    {
        return $this->hasMany(EmailMessage::class);
    }

    public function syncRuns(): HasMany
    {
        return $this->hasMany(EmailSyncRun::class);
    }
}
