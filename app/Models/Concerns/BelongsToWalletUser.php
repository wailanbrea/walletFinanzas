<?php

namespace App\Models\Concerns;

use App\Models\User;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

trait BelongsToWalletUser
{
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
