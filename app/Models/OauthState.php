<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class OauthState extends Model
{
    protected $fillable = ['user_id', 'provider', 'state_hash', 'code_verifier', 'expires_at'];

    protected $hidden = ['state_hash', 'code_verifier'];

    protected function casts(): array
    {
        return [
            'code_verifier' => 'encrypted',
            'expires_at' => 'immutable_datetime',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
