<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class BankConnection extends Model
{
    use HasFactory, HasUuids;

    protected $fillable = [
        'user_id',
        'provider_name',
        'provider_code',
        'country_code',
        'status',
        'last_sync_at',
    ];

    protected function casts(): array
    {
        return [
            'last_sync_at' => 'immutable_datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
