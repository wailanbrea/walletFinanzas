<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class FinancialPlanningSyncApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_financial_planning_resources_require_authentication(): void
    {
        foreach (['budgets', 'goals', 'debts', 'planned-payments'] as $resource) {
            $this->getJson("/api/v1/$resource")->assertUnauthorized();
            $this->postJson("/api/v1/$resource", [])->assertUnauthorized();
        }
    }

    public function test_user_can_upsert_pull_and_tombstone_all_financial_planning_resources(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user, ['wallet']);
        [$accountId, $categoryId] = $this->createDependencies();
        $this->assertDatabaseHas('categories', [
            'user_id' => $user->id,
            'client_id' => $categoryId,
            'is_deleted' => false,
        ]);

        $resources = [
            'budgets' => [
                'id' => (string) Str::uuid(), 'category_id' => $categoryId,
                'limit_amount' => 150000, 'spent_amount' => 25000,
                'period' => 'MONTHLY', 'is_deleted' => false,
            ],
            'goals' => [
                'id' => (string) Str::uuid(), 'name' => 'Fondo de emergencia', 'icon' => 'savings',
                'target_amount' => 500000, 'saved_amount' => 120000, 'target_date' => 1800000000000,
                'is_completed' => false, 'is_deleted' => false,
            ],
            'debts' => [
                'id' => (string) Str::uuid(), 'name' => 'Prestamo', 'description' => '',
                'direction' => 'I_OWE', 'total_amount' => 300000, 'paid_amount' => 50000,
                'due_date' => null, 'is_closed' => false, 'is_deleted' => false,
            ],
            'planned-payments' => [
                'id' => (string) Str::uuid(), 'name' => 'Internet', 'account_id' => $accountId,
                'category_id' => $categoryId, 'amount' => 350000, 'type' => 'EXPENSE',
                'frequency' => 'MONTHLY', 'next_due_date' => 1800000000000,
                'is_active' => true, 'is_deleted' => false,
            ],
        ];

        foreach ($resources as $resource => $payload) {
            $createdResponse = $this->postJson("/api/v1/$resource", $payload);
            $this->assertSame(201, $createdResponse->status(), "$resource: {$createdResponse->getContent()}");
            $createdResponse
                ->assertJsonPath('data.id', $payload['id'])
                ->assertJsonPath('data.is_deleted', false);

            $this->postJson("/api/v1/$resource", [...$payload, 'is_deleted' => true])
                ->assertOk()
                ->assertJsonPath('data.is_deleted', true);

            $this->getJson("/api/v1/$resource")
                ->assertOk()
                ->assertJsonCount(1, 'data')
                ->assertJsonPath('data.0.id', $payload['id'])
                ->assertJsonPath('data.0.is_deleted', true);
        }
    }

    public function test_resources_and_references_are_strictly_scoped_per_user(): void
    {
        $first = User::factory()->create();
        $second = User::factory()->create();

        Sanctum::actingAs($first, ['wallet']);
        [$firstAccount, $firstCategory] = $this->createDependencies();
        $goalId = (string) Str::uuid();
        $this->postJson('/api/v1/goals', [
            'id' => $goalId, 'name' => 'Meta privada', 'icon' => 'flag',
            'target_amount' => 10000, 'saved_amount' => 0, 'target_date' => null,
            'is_completed' => false, 'is_deleted' => false,
        ])->assertCreated();

        Sanctum::actingAs($second, ['wallet']);
        $this->getJson('/api/v1/goals')->assertOk()->assertJsonCount(0, 'data');

        $this->postJson('/api/v1/budgets', [
            'id' => (string) Str::uuid(), 'category_id' => $firstCategory,
            'limit_amount' => 10000, 'spent_amount' => 0,
            'period' => 'MONTHLY', 'is_deleted' => false,
        ])->assertUnprocessable()->assertJsonValidationErrors('category_id');

        $this->postJson('/api/v1/planned-payments', [
            'id' => (string) Str::uuid(), 'name' => 'Ajeno', 'account_id' => $firstAccount,
            'category_id' => '', 'amount' => 1000, 'type' => 'EXPENSE',
            'frequency' => 'ONCE', 'next_due_date' => 1800000000000,
            'is_active' => true, 'is_deleted' => false,
        ])->assertUnprocessable()->assertJsonValidationErrors('account_id');
    }

    /** @return array{string, string} */
    private function createDependencies(): array
    {
        $accountId = (string) Str::uuid();
        $categoryId = 'cat_servicios';

        $this->postJson('/api/v1/accounts', [
            'id' => $accountId, 'name' => 'Principal', 'balance' => 0,
            'currency' => 'DOP', 'country_code' => 'DO',
        ])->assertCreated();
        $this->postJson('/api/v1/categories', [
            'id' => $categoryId, 'name' => 'Servicios', 'icon' => 'receipt',
            'color_hex' => '#546E7A', 'is_deleted' => false,
        ])->assertCreated();

        return [$accountId, $categoryId];
    }
}
