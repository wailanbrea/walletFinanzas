<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
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
            'idempotency_key' => (string) Str::uuid(),
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
            'idempotency_key' => (string) Str::uuid(),
        ];

        $this->postJson('/api/v1/transactions', $payload)->assertNotFound();
        $this->getJson('/api/v1/transactions?account_id='.$account->id)->assertNotFound();
        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_repeated_idempotency_key_does_not_duplicate_transaction_or_balance_change(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Idempotente',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        Sanctum::actingAs($owner);

        $payload = [
            'account_id' => $account->id,
            'amount' => -1000,
            'currency' => 'DOP',
            'description' => 'Reintento móvil',
            'timestamp' => '2026-07-20T14:30:00Z',
            'status' => 'completed',
            'idempotency_key' => (string) Str::uuid(),
        ];

        $firstId = $this->postJson('/api/v1/transactions', $payload)
            ->assertCreated()
            ->json('data.id');

        $this->postJson('/api/v1/transactions', $payload)
            ->assertOk()
            ->assertJsonPath('data.id', $firstId);

        foreach ([
            ['description' => 'Operación diferente'],
            ['timestamp' => '2026-07-20T14:31:00Z'],
            ['status' => 'pending'],
        ] as $changedFields) {
            $this->postJson('/api/v1/transactions', [
                ...$payload,
                ...$changedFields,
            ])->assertConflict();
        }

        $this->assertDatabaseCount('transactions', 1);
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'balance' => 9000,
        ]);
    }

    public function test_idempotent_retry_canonicalizes_numeric_string_amount(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Importe canónico',
            'balance' => 1000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        Sanctum::actingAs($owner);

        $payload = [
            'account_id' => $account->id,
            'amount' => '-100',
            'currency' => 'DOP',
            'timestamp' => '2026-07-20T14:30:00Z',
            'status' => 'completed',
            'idempotency_key' => (string) Str::uuid(),
        ];

        $firstId = $this->postJson('/api/v1/transactions', $payload)
            ->assertCreated()
            ->json('data.id');

        $this->postJson('/api/v1/transactions', $payload)
            ->assertOk()
            ->assertJsonPath('data.id', $firstId);

        $this->postJson('/api/v1/transactions', [
            ...$payload,
            'amount' => -100,
        ])->assertOk()->assertJsonPath('data.id', $firstId);

        $this->assertDatabaseCount('transactions', 1);
        $this->assertDatabaseHas('accounts', ['id' => $account->id, 'balance' => 900]);
    }

    public function test_idempotent_retry_canonicalizes_fractional_offset_timestamp(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Fecha canónica',
            'balance' => 1000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        Sanctum::actingAs($owner);

        $payload = [
            'account_id' => $account->id,
            'amount' => -100,
            'currency' => 'DOP',
            'timestamp' => '2026-07-20T12:30:00.123-04:00',
            'status' => 'completed',
            'idempotency_key' => (string) Str::uuid(),
        ];

        $firstId = $this->postJson('/api/v1/transactions', $payload)
            ->assertCreated()
            ->json('data.id');

        $this->postJson('/api/v1/transactions', $payload)
            ->assertOk()
            ->assertJsonPath('data.id', $firstId);

        $this->postJson('/api/v1/transactions', [
            ...$payload,
            'timestamp' => '2026-07-20T16:30:00Z',
        ])->assertOk()->assertJsonPath('data.id', $firstId);

        $this->assertDatabaseCount('transactions', 1);
        $this->assertDatabaseHas('accounts', ['id' => $account->id, 'balance' => 900]);
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
                'idempotency_key' => (string) Str::uuid(),
            ])->assertUnprocessable()->assertJsonValidationErrors(['amount']);
        }

        $this->postJson('/api/v1/transactions', [
            'account_id' => $account->id,
            'amount' => 100,
            'currency' => 'USD',
            'timestamp' => '2026-07-20T14:30:00Z',
            'status' => 'completed',
            'idempotency_key' => (string) Str::uuid(),
        ])->assertUnprocessable()->assertJsonValidationErrors(['currency']);
    }

    public function test_editing_a_transaction_moves_the_balance_by_the_difference(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        $created = $this->postJson('/api/v1/transactions', [
            'account_id' => $account->id,
            'idempotency_key' => (string) Str::uuid(),
            'amount' => -2500,
            'currency' => 'DOP',
            'timestamp' => now()->toISOString(),
            'status' => 'completed',
        ])->assertCreated()->json('data');

        $this->assertSame(7500, $account->fresh()->balance);

        // Al corregir el importe el saldo se mueve solo por la diferencia.
        $this->patchJson("/api/v1/transactions/{$created['id']}", ['amount' => -4000])
            ->assertOk()
            ->assertJsonPath('data.amount', -4000);

        $this->assertSame(6000, $account->fresh()->balance);
    }

    public function test_deleting_a_transaction_returns_its_amount_to_the_balance(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);

        $created = $this->postJson('/api/v1/transactions', [
            'account_id' => $account->id,
            'idempotency_key' => (string) Str::uuid(),
            'amount' => -2500,
            'currency' => 'DOP',
            'timestamp' => now()->toISOString(),
            'status' => 'completed',
        ])->assertCreated()->json('data');

        $this->deleteJson("/api/v1/transactions/{$created['id']}")->assertNoContent();
        $this->assertSame(10000, $account->fresh()->balance);

        // Repetir el borrado no vuelve a mover el saldo: la cola de la app reintenta.
        $this->deleteJson("/api/v1/transactions/{$created['id']}")->assertNotFound();
        $this->assertSame(10000, $account->fresh()->balance);
    }

    public function test_a_user_cannot_edit_or_delete_another_users_transaction(): void
    {
        $owner = User::factory()->create();
        $account = Account::create([
            'user_id' => $owner->id,
            'name' => 'Efectivo',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ]);
        Sanctum::actingAs($owner);
        $created = $this->postJson('/api/v1/transactions', [
            'account_id' => $account->id,
            'idempotency_key' => (string) Str::uuid(),
            'amount' => -2500,
            'currency' => 'DOP',
            'timestamp' => now()->toISOString(),
            'status' => 'completed',
        ])->assertCreated()->json('data');

        Sanctum::actingAs(User::factory()->create());
        $this->patchJson("/api/v1/transactions/{$created['id']}", ['amount' => -1])->assertNotFound();
        $this->deleteJson("/api/v1/transactions/{$created['id']}")->assertNotFound();
        $this->assertSame(7500, $account->fresh()->balance);
    }
}
