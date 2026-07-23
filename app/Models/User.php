<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    /**
     * The attributes that are mass assignable.
     *
     * @var list<string>
     */
    protected $fillable = [
        'name',
        'email',
        'password',
    ];

    /**
     * The attributes that should be hidden for serialization.
     *
     * @var list<string>
     */
    protected $hidden = [
        'password',
        'remember_token',
    ];

    public function bankConnections(): HasMany
    {
        return $this->hasMany(BankConnection::class);
    }

    public function accounts(): HasMany
    {
        return $this->hasMany(Account::class);
    }

    public function emailConnections(): HasMany
    {
        return $this->hasMany(EmailConnection::class);
    }

    public function emailOAuthStates(): HasMany
    {
        return $this->hasMany(EmailOAuthState::class);
    }

    public function emailSyncRuns(): HasMany
    {
        return $this->hasMany(EmailSyncRun::class);
    }

    public function providerMessages(): HasMany
    {
        return $this->hasMany(ProviderMessage::class);
    }

    public function emailCandidates(): HasMany
    {
        return $this->hasMany(EmailCandidate::class);
    }

    public function emailCategorizationRules(): HasMany
    {
        return $this->hasMany(EmailCategorizationRule::class);
    }

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
        ];
    }
}
