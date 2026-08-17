<?php

namespace Database\Factories;

use App\Models\Transaction;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Transaction>
 */
class TransactionFactory extends Factory
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
            'user_id' => fn () => User::factory()->create()->id,
            'account_id' => null,
            'amount' => fake()->numberBetween(-100000, 100000),
            'currency' => 'DOP',
            'description' => fake()->optional()->sentence(4),
            'category_id' => null,
            'debt_id' => null,
            'occurred_at' => fake()->dateTimeBetween('-1 year', 'now'),
            'status' => 'completed',
        ];
    }

    public function completed(): static
    {
        return $this->state(fn (array $attributes) => [
            'status' => 'completed',
        ]);
    }

    public function pending(): static
    {
        return $this->state(fn (array $attributes) => [
            'status' => 'pending',
        ]);
    }
}
