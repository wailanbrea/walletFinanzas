<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EmailOAuthState extends Model
{
    use HasUuids;

    protected $table = 'email_oauth_states';

    protected $fillable = ['user_id', 'provider', 'state_hash', 'code_verifier', 'expires_at', 'used_at'];

    protected $hidden = ['state_hash', 'code_verifier'];

    protected function casts(): array
    {
        return ['code_verifier' => 'encrypted', 'expires_at' => 'immutable_datetime', 'used_at' => 'immutable_datetime'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
