<?php

namespace Database\Factories;

use App\Models\Account;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Account>
 */
class AccountFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'id' => (string) Str::uuid(),
            'user_id' => null,
            'name' => fake()->words(3, true),
            'type' => fake()->randomElement(['CASH', 'BANK', 'SAVINGS', 'DEBIT_CARD', 'CREDIT_CARD']),
            'balance' => fake()->numberBetween(0, 500000),
            'opening_balance' => 0,
            'credit_limit' => null,
            'currency' => 'DOP',
            'institution_name' => fake()->optional()->company(),
            'country_code' => fake()->randomElement(['US', 'DO', 'MX', 'ES', 'GB', 'FR', 'DE', 'IT', 'CA', 'BR']),
            'card_last_four' => fake()->numberBetween(1000, 9999),
            'is_active' => true,
        ];
    }

    public function withCreditLimit(int $limit): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => 'CREDIT_CARD',
            'credit_limit' => $limit,
        ]);
    }
}
