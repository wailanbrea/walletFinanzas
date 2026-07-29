<?php

namespace Database\Factories;

use App\Models\Account;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Account>
 */
class AccountFactory extends Factory
{
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'name' => fake()->word(),
            'type' => 'CASH',
            'balance' => fake()->numberBetween(0, 1000000),
            'credit_limit' => null,
            'currency' => 'DOP',
            'country_code' => 'DO',
            'is_active' => true,
        ];
    }

    public function creditCard(): static
    {
        return $this->state(fn (array $attributes): array => [
            'type' => 'CREDIT_CARD',
            'credit_limit' => 100000,
        ]);
    }
}
