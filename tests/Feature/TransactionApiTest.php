<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class TransactionApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_transaction_creation_is_authenticated_and_updates_balance_atomically(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        $this->postJson('/api/v1/transactions', [])->assertUnauthorized();

        Sanctum::actingAs($owner);
        $transaction = $this->postJson('/api/v1/transactions', [
            'account_id' => $account->id,
            'amount' => -2500,
            'currency' => 'dop',
            'description' => 'Supermercado',
            'timestamp' => '2026-07-20T14:30:00Z',
            'status' => 'completed',
        ])->assertCreated()
            ->assertJsonPath('data.amount', -2500)
            ->assertJsonPath('data.currency', 'DOP')
            ->assertJsonPath('data.timestamp', '2026-07-20T14:30:00.000000Z')
            ->json('data');

        $this->assertDatabaseHas('transactions', [
            'id' => $transaction['id'],
            'account_id' => $account->id,
            'amount' => -2500,
        ]);
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'balance' => 7500,
        ]);
    }

    public function test_user_cannot_create_or_read_transactions_for_another_users_account(): void
    {
        $owner = User::factory()->create();
        $outsider = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Privada',
            'balance' => 1000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        Sanctum::actingAs($outsider);

        $payload = [
            'account_id' => $account->id,
            'amount' => -100,
            'currency' => 'DOP',
            'timestamp' => '2026-07-20T14:30:00Z',
            'status' => 'completed',
        ];

        $this->postJson('/api/v1/transactions', $payload)->assertNotFound();
        $this->getJson('/api/v1/transactions?account_id='.$account->id)->assertNotFound();
        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_transaction_rejects_zero_decimal_and_currency_mismatch(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'DOP',
            'balance' => 1000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        Sanctum::actingAs($owner);

        foreach ([0, 10.50] as $invalidAmount) {
            $this->postJson('/api/v1/transactions', [
                'account_id' => $account->id,
                'amount' => $invalidAmount,
                'currency' => 'DOP',
                'timestamp' => '2026-07-20T14:30:00Z',
                'status' => 'completed',
            ])->assertUnprocessable()->assertJsonValidationErrors(['amount']);
        }

        $this->postJson('/api/v1/transactions', [
            'account_id' => $account->id,
            'amount' => 100,
            'currency' => 'USD',
            'timestamp' => '2026-07-20T14:30:00Z',
            'status' => 'completed',
        ])->assertUnprocessable()->assertJsonValidationErrors(['currency']);
    }
}
