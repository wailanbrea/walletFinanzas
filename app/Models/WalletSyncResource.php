<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;

class WalletSyncResource extends Model
{
    public $incrementing = false;

    protected $keyType = 'string';

    protected $guarded = [];

    protected $hidden = ['user_id', 'created_at'];

    protected function casts(): array
    {
        return [
            'limit_amount' => 'integer',
            'spent_amount' => 'integer',
            'target_amount' => 'integer',
            'saved_amount' => 'integer',
            'target_date' => 'integer',
            'total_amount' => 'integer',
            'paid_amount' => 'integer',
            'due_date' => 'integer',
            'amount' => 'integer',
            'next_due_date' => 'integer',
            'is_deleted' => 'boolean',
            'is_completed' => 'boolean',
            'is_closed' => 'boolean',
            'is_active' => 'boolean',
        ];
    }

    protected function setKeysForSaveQuery($query): Builder
    {
        return parent::setKeysForSaveQuery($query)->where('user_id', $this->user_id);
    }
}
