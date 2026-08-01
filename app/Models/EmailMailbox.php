<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;

class EmailMailbox extends Model
{
    use HasUuids;

    protected $fillable = [
        'user_id', 'provider', 'email', 'sync_from_date', 'sync_from_at',
        'backfill_before_at', 'backfill_cursor', 'backfill_completed_at',
        'incremental_from_at', 'incremental_before_at', 'incremental_cursor',
    ];

    protected function casts(): array
    {
        return [
            'sync_from_date' => 'immutable_date',
            'sync_from_at' => 'immutable_datetime',
            'backfill_before_at' => 'immutable_datetime',
            'backfill_completed_at' => 'immutable_datetime',
            'incremental_from_at' => 'immutable_datetime',
            'incremental_before_at' => 'immutable_datetime',
        ];
    }

    public static function forConnection(EmailConnection $connection): self
    {
        $email = Str::lower(trim($connection->email));
        $mailbox = self::query()->firstOrCreate([
            'user_id' => $connection->user_id,
            'provider' => $connection->provider,
            'email' => $email,
        ]);
        if ($connection->email_mailbox_id !== $mailbox->id || $connection->email !== $email) {
            $connection->update(['email_mailbox_id' => $mailbox->id, 'email' => $email]);
        }

        return $mailbox;
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function decisions(): HasMany
    {
        return $this->hasMany(EmailMessageDecision::class);
    }

    public function connections(): HasMany
    {
        return $this->hasMany(EmailConnection::class);
    }
}
