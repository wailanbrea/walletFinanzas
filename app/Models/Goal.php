<?php

namespace App\Models;

use App\Models\Concerns\BelongsToWalletUser;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

class Goal extends Model
{
    use BelongsToWalletUser, HasUuids;

    protected $fillable = [
        'user_id', 'client_id', 'name', 'icon', 'target_amount', 'saved_amount',
        'target_date', 'is_completed', 'is_deleted',
    ];

    protected function casts(): array
    {
        return [
            'target_amount' => 'integer', 'saved_amount' => 'integer', 'target_date' => 'integer',
            'is_completed' => 'boolean', 'is_deleted' => 'boolean',
        ];
    }
}
