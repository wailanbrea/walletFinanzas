<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class EmailConnection extends Model
{
    use HasUuids;

    protected $fillable = ['user_id', 'provider', 'email', 'status', 'access_token', 'refresh_token', 'token_expires_at', 'connected_at', 'last_synced_at'];

    protected $hidden = ['access_token', 'refresh_token'];

    protected function casts(): array
    {
        return [
            'access_token' => 'encrypted',
            'refresh_token' => 'encrypted',
            'token_expires_at' => 'immutable_datetime',
            'connected_at' => 'immutable_datetime',
            'last_synced_at' => 'immutable_datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function syncRuns(): HasMany
    {
        return $this->hasMany(EmailSyncRun::class);
    }

    public function messages(): HasMany
    {
        return $this->hasMany(ProviderMessage::class);
    }
}
