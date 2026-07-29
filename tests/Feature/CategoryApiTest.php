<?php

namespace Tests\Feature;

use App\Models\Category;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class CategoryApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_categories_require_authentication(): void
    {
        $this->getJson('/api/v1/categories')->assertUnauthorized();
        $this->postJson('/api/v1/categories', [])->assertUnauthorized();
    }

    public function test_user_can_upsert_list_and_tombstone_category_by_client_id(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner, ['wallet']);

        $payload = [
            'id' => 'cat_alimentacion',
            'name' => 'Alimentación',
            'icon' => 'restaurant',
            'color_hex' => '#e57373',
            'is_deleted' => false,
        ];

        $this->postJson('/api/v1/categories', $payload)
            ->assertCreated()
            ->assertJsonPath('data.id', 'cat_alimentacion')
            ->assertJsonPath('data.color_hex', '#E57373')
            ->assertJsonPath('data.is_deleted', false);

        $this->postJson('/api/v1/categories', [
            ...$payload,
            'name' => 'Comida',
        ])->assertOk()
            ->assertJsonPath('data.name', 'Comida');

        $this->postJson('/api/v1/categories', [
            ...$payload,
            'name' => 'Comida',
            'is_deleted' => true,
        ])->assertOk()
            ->assertJsonPath('data.is_deleted', true);

        $this->getJson('/api/v1/categories')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.id', 'cat_alimentacion')
            ->assertJsonPath('data.0.is_deleted', true);

        $this->assertDatabaseCount('categories', 1);
        $this->assertDatabaseHas('categories', [
            'user_id' => $owner->id,
            'client_id' => 'cat_alimentacion',
            'is_deleted' => true,
        ]);
    }

    public function test_category_client_ids_are_scoped_per_user(): void
    {
        $first = User::factory()->create();
        $second = User::factory()->create();
        $payload = [
            'id' => 'cat_compras',
            'name' => 'Compras',
            'icon' => 'shopping_cart',
            'color_hex' => '#F06292',
            'is_deleted' => false,
        ];

        Sanctum::actingAs($first, ['wallet']);
        $this->postJson('/api/v1/categories', $payload)->assertCreated();

        Sanctum::actingAs($second, ['wallet']);
        $this->postJson('/api/v1/categories', [
            ...$payload,
            'name' => 'Mis compras',
        ])->assertCreated();
        $this->getJson('/api/v1/categories')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.name', 'Mis compras');

        $this->assertDatabaseCount('categories', 2);
    }

    public function test_category_validation_and_active_limit_are_enforced(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner, ['wallet']);

        $this->postJson('/api/v1/categories', [
            'id' => 'invalid id',
            'name' => '',
            'icon' => 'restaurant',
            'color_hex' => 'red',
            'is_deleted' => false,
        ])->assertUnprocessable()
            ->assertJsonValidationErrors(['id', 'name', 'color_hex']);

        $now = now();
        Category::query()->insert(array_map(
            fn (int $index): array => [
                'id' => (string) Str::uuid(),
                'user_id' => $owner->id,
                'client_id' => "custom_$index",
                'name' => "Categoría $index",
                'icon' => 'category',
                'color_hex' => '#90A4AE',
                'is_deleted' => false,
                'created_at' => $now,
                'updated_at' => $now,
            ],
            range(1, 200)
        ));

        $this->postJson('/api/v1/categories', [
            'id' => 'custom_201',
            'name' => 'Una de más',
            'icon' => 'category',
            'color_hex' => '#90A4AE',
            'is_deleted' => false,
        ])->assertUnprocessable()
            ->assertJsonValidationErrors(['id']);
    }
}
