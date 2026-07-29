<?php

namespace App\Models;

use App\Models\Concerns\BelongsToWalletUser;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

class Debt extends Model
{
    use BelongsToWalletUser, HasUuids;

    protected $fillable = [
        'user_id', 'client_id', 'name', 'description', 'direction', 'total_amount',
        'paid_amount', 'due_date', 'is_closed', 'is_deleted',
    ];

    protected function casts(): array
    {
        return [
            'total_amount' => 'integer', 'paid_amount' => 'integer', 'due_date' => 'integer',
            'is_closed' => 'boolean', 'is_deleted' => 'boolean',
        ];
    }
}
