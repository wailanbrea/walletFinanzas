<?php

namespace App\Models;

use App\Models\Concerns\BelongsToWalletUser;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;

class Budget extends Model
{
    use BelongsToWalletUser, HasUuids;

    protected $fillable = ['user_id', 'client_id', 'category_id', 'limit_amount', 'spent_amount', 'period', 'is_deleted'];

    protected function casts(): array
    {
        return ['limit_amount' => 'integer', 'spent_amount' => 'integer', 'is_deleted' => 'boolean'];
    }
}
