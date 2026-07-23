<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class WalletCloudSyncApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_client_ids_are_preserved_and_account_transaction_pull_is_global(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $accountId = (string) Str::uuid();
        $transactionId = (string) Str::uuid();

        $this->postJson('/api/v1/accounts', [
            'id' => $accountId,
            'name' => 'Efectivo',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertCreated()->assertJsonPath('data.id', $accountId);

        $this->postJson('/api/v1/transactions', [
            'idempotency_key' => $transactionId,
            'account_id' => $accountId,
            'amount' => -500,
            'currency' => 'DOP',
            'category_id' => 'cat_compras',
            'timestamp' => '2026-07-23T12:00:00Z',
            'status' => 'completed',
        ])->assertUnprocessable();

        $this->postJson('/api/v1/categories', [
            'id' => 'cat_compras',
            'name' => 'Compras',
            'icon' => 'cart',
            'color_hex' => '#112233',
            'is_deleted' => false,
        ])->assertCreated();
        $this->postJson('/api/v1/transactions', [
            'idempotency_key' => $transactionId,
            'account_id' => $accountId,
            'amount' => -500,
            'currency' => 'DOP',
            'category_id' => 'cat_compras',
            'timestamp' => '2026-07-23T12:00:00Z',
            'status' => 'completed',
        ])->assertCreated();
        $this->assertDatabaseHas('accounts', ['id' => $accountId, 'balance' => 9500]);

        $this->getJson('/api/v1/accounts')->assertOk()->assertJsonPath('data.0.id', $accountId);
        $this->getJson('/api/v1/transactions')->assertOk()->assertJsonPath('data.0.idempotency_key', $transactionId);
    }

    public function test_all_android_sync_resources_support_idempotent_push_and_pull(): void
    {
        $other = User::factory()->create();
        Sanctum::actingAs($other);
        $accountId = (string) Str::uuid();
        $this->postJson('/api/v1/accounts', [
            'id' => $accountId,
            'name' => 'Banco',
            'balance' => 0,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertCreated();

        $resources = [
            'categories' => ['id' => 'cat_compras', 'name' => 'Compras', 'icon' => 'cart', 'color_hex' => '#112233', 'is_deleted' => false],
            'budgets' => ['id' => 'budget-1', 'category_id' => 'cat_compras', 'limit_amount' => 10000, 'spent_amount' => 500, 'period' => 'MONTHLY', 'is_deleted' => false],
            'goals' => ['id' => 'goal-1', 'name' => 'Viaje', 'icon' => 'flight', 'target_amount' => 50000, 'saved_amount' => 10000, 'target_date' => null, 'is_completed' => false, 'is_deleted' => false],
            'debts' => ['id' => 'debt-1', 'name' => 'Préstamo', 'description' => '', 'direction' => 'I_OWE', 'total_amount' => 20000, 'paid_amount' => 1000, 'due_date' => null, 'is_closed' => false, 'is_deleted' => false],
            'planned-payments' => ['id' => 'payment-1', 'name' => 'Internet', 'account_id' => $accountId, 'category_id' => 'cat_compras', 'amount' => 2500, 'type' => 'EXPENSE', 'frequency' => 'MONTHLY', 'next_due_date' => 1780000000000, 'is_active' => true, 'is_deleted' => false],
        ];

        foreach ($resources as $path => $payload) {
            $this->postJson('/api/v1/'.$path, $payload)->assertCreated()->assertJsonPath('data.id', $payload['id']);
            $this->postJson('/api/v1/'.$path, $payload)->assertOk();
            $this->getJson('/api/v1/'.$path)->assertOk()->assertJsonPath('data.0.id', $payload['id']);
        }
    }

    public function test_existing_provider_account_ids_and_uncategorized_planned_payments_are_supported(): void
    {
        Sanctum::actingAs(User::factory()->create());
        $accountId = 'se_remote-account-1';
        $this->postJson('/api/v1/accounts', [
            'id' => $accountId,
            'name' => 'Cuenta importada',
            'balance' => 1000,
            'currency' => 'USD',
            'country_code' => 'DO',
        ])->assertCreated();
        $this->postJson('/api/v1/planned-payments', [
            'id' => 'payment-without-category',
            'name' => 'Pago sin categoría',
            'account_id' => $accountId,
            'category_id' => '',
            'amount' => 100,
            'type' => 'EXPENSE',
            'frequency' => 'ONCE',
            'next_due_date' => 1780000000000,
            'is_active' => true,
            'is_deleted' => false,
        ])->assertCreated()->assertJsonPath('data.category_id', '');
    }

    public function test_sync_resources_are_isolated_by_user(): void
    {
        Sanctum::actingAs(User::factory()->create());
        $this->postJson('/api/v1/categories', [
            'id' => 'private-category',
            'name' => 'Privada',
            'icon' => 'lock',
            'color_hex' => '#112233',
            'is_deleted' => false,
        ])->assertCreated();

        Sanctum::actingAs(User::factory()->create());
        $this->getJson('/api/v1/categories')->assertOk()->assertJsonCount(0, 'data');
        $this->postJson('/api/v1/categories', [
            'id' => 'private-category',
            'name' => 'Del segundo usuario',
            'icon' => 'lock',
            'color_hex' => '#112233',
            'is_deleted' => false,
        ])->assertCreated();
        $this->getJson('/api/v1/categories')->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.name', 'Del segundo usuario');
    }
}
