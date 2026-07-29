<?php

namespace App\Models;

use App\Models\Concerns\BelongsToWalletUser;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

class PlannedPayment extends Model
{
    use BelongsToWalletUser, HasUuids;

    protected $fillable = [
        'user_id', 'client_id', 'account_id', 'category_id', 'name', 'amount', 'type',
        'frequency', 'next_due_date', 'is_active', 'is_deleted',
    ];

    protected function casts(): array
    {
        return [
            'amount' => 'integer', 'next_due_date' => 'integer', 'is_active' => 'boolean',
            'is_deleted' => 'boolean',
        ];
    }
}
