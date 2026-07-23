<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailCategorizationRule extends Model
{
    protected $fillable = ['user_id', 'merchant', 'category'];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
