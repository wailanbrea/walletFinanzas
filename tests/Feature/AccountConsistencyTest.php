<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class AccountConsistencyTest extends TestCase
{
    use RefreshDatabase;

    public function test_consistency_endpoint_requires_authentication(): void
    {
        $this->getJson('/api/v1/accounts/00000000-0000-0000-0000-000000000000/consistency')
            ->assertUnauthorized();
    }

    public function test_returns_consistent_when_balance_matches_transactions(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $account = Account::factory()->create([
            'user_id' => $user->id,
            'balance' => 30000, // 300.00 in minor units
            'opening_balance' => 0,
        ]);

        // 3 completed transactions summing to 30000
        Transaction::factory()->create(['account_id' => $account->id, 'amount' => 10000, 'status' => 'completed']);
        Transaction::factory()->create(['account_id' => $account->id, 'amount' => 10000, 'status' => 'completed']);
        Transaction::factory()->create(['account_id' => $account->id, 'amount' => 10000, 'status' => 'completed']);

        $response = $this->getJson("/api/v1/accounts/{$account->id}/consistency")
            ->assertOk();

        $response
            ->assertJsonPath('account_id', $account->id)
            ->assertJsonPath('stored_balance', 30000)
            ->assertJsonPath('calculated_balance', 30000)
            ->assertJsonPath('opening_balance', 0)
            ->assertJsonPath('unexplained_difference', 0)
            ->assertJsonPath('transaction_count', 3)
            ->assertJsonPath('consistent', true);
    }

    public function test_account_with_opening_balance_and_no_transactions_is_consistent(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $account = Account::factory()->create([
            'user_id' => $user->id,
            'balance' => 222538, // solo saldo de apertura, cero movimientos
            'opening_balance' => 222538,
        ]);

        $response = $this->getJson("/api/v1/accounts/{$account->id}/consistency")
            ->assertOk();

        $response
            ->assertJsonPath('stored_balance', 222538)
            ->assertJsonPath('calculated_balance', 0)
            ->assertJsonPath('opening_balance', 222538)
            ->assertJsonPath('unexplained_difference', 0)
            ->assertJsonPath('transaction_count', 0)
            ->assertJsonPath('consistent', true);
    }

    public function test_returns_consistent_with_zero_transactions_and_zero_balance(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $account = Account::factory()->create([
            'user_id' => $user->id,
            'balance' => 0,
        ]);

        $response = $this->getJson("/api/v1/accounts/{$account->id}/consistency")
            ->assertOk();

        $response
            ->assertJsonPath('stored_balance', 0)
            ->assertJsonPath('calculated_balance', 0)
            ->assertJsonPath('opening_balance', 0)
            ->assertJsonPath('unexplained_difference', 0)
            ->assertJsonPath('transaction_count', 0)
            ->assertJsonPath('consistent', true);
    }

    public function test_ignores_non_completed_transactions_in_calculation(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $account = Account::factory()->create([
            'user_id' => $user->id,
            'balance' => 10000,
        ]);

        Transaction::factory()->create(['account_id' => $account->id, 'amount' => 10000, 'status' => 'completed']);
        Transaction::factory()->create(['account_id' => $account->id, 'amount' => 50000, 'status' => 'pending']);

        $response = $this->getJson("/api/v1/accounts/{$account->id}/consistency")
            ->assertOk();

        $response
            ->assertJsonPath('calculated_balance', 10000)
            ->assertJsonPath('transaction_count', 1)
            ->assertJsonPath('consistent', true);
    }

    public function test_cannot_access_another_users_account_consistency(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);

        $otherAccount = Account::factory()->create([
            'user_id' => User::factory()->create()->id,
            'balance' => 999999,
        ]);

        $this->getJson("/api/v1/accounts/{$otherAccount->id}/consistency")
            ->assertNotFound();
    }

    public function test_detects_balance_corruption_after_baseline_is_set(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        // La cuenta tiene opening_balance = 0 y balance = 10000,
        // lo que significa que se espera que las transacciones sumen 10000.
        $account = Account::factory()->create([
            'user_id' => $user->id,
            'balance' => 10000,
            'opening_balance' => 0,
        ]);

        Transaction::factory()->create(['account_id' => $account->id, 'amount' => 10000, 'status' => 'completed']);

        // Verificar que la cuenta es consistente antes de la corrupcion.
        $response = $this->getJson("/api/v1/accounts/{$account->id}/consistency")
            ->assertOk();

        $response
            ->assertJsonPath('consistent', true)
            ->assertJsonPath('unexplained_difference', 0);

        // Corromper el balance: sumarle 5000 sin crear transaccion.
        $account->update(['balance' => 15000]);

        $response = $this->getJson("/api/v1/accounts/{$account->id}/consistency")
            ->assertOk();

        $response
            ->assertJsonPath('consistent', false)
            ->assertJsonPath('stored_balance', 15000)
            ->assertJsonPath('calculated_balance', 10000)
            ->assertJsonPath('opening_balance', 0)
            ->assertJsonPath('unexplained_difference', 5000);
    }
}
