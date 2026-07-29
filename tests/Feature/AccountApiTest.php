<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class AccountApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_accounts_require_authentication(): void
    {
        $this->getJson('/api/v1/accounts')->assertUnauthorized();
    }

    public function test_authenticated_user_can_create_and_list_only_their_accounts(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);

        $created = $this->postJson('/api/v1/accounts', [
            'name' => 'Cuenta principal',
            'balance' => 150000,
            'currency' => 'dop',
            'institution_name' => 'Banco Demo',
            'country_code' => 'do',
            'card_last_four' => '1234',
            'is_active' => true,
        ])->assertCreated()
            ->assertJsonPath('data.name', 'Cuenta principal')
            ->assertJsonPath('data.balance', 150000)
            ->assertJsonPath('data.currency', 'DOP')
            ->json('data');

        $this->assertMatchesRegularExpression(
            '/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/',
            $created['id']
        );

        $otherUser = User::factory()->create();
        $this->withHeader('Authorization', '');
        Sanctum::actingAs($otherUser);
        $this->postJson('/api/v1/accounts', [
            'name' => 'Cuenta ajena',
            'balance' => 999,
            'currency' => 'USD',
            'country_code' => 'US',
        ])->assertCreated();

        Sanctum::actingAs($owner);
        $this->getJson('/api/v1/accounts')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.id', $created['id']);
    }

    public function test_account_rejects_decimal_amounts_and_invalid_card_digits(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/accounts', [
            'name' => 'Inválida',
            'balance' => 10.50,
            'currency' => 'DOP',
            'country_code' => 'DO',
            'card_last_four' => '12AB',
        ])->assertUnprocessable()
            ->assertJsonValidationErrors(['balance', 'card_last_four']);
    }

    public function test_credit_card_requires_a_positive_credit_limit_in_minor_units(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $payload = [
            'name' => 'Tarjeta',
            'type' => 'credit_card',
            'balance' => -2500,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ];

        $this->postJson('/api/v1/accounts', $payload)
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['credit_limit']);

        $this->postJson('/api/v1/accounts', [...$payload, 'credit_limit' => 0])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['credit_limit']);

        $this->postJson('/api/v1/accounts', [...$payload, 'credit_limit' => 250000])
            ->assertCreated()
            ->assertJsonPath('data.type', 'CREDIT_CARD')
            ->assertJsonPath('data.credit_limit', 250000);
    }

    public function test_non_credit_account_normalizes_credit_limit_to_null(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/accounts', [
            'name' => 'Ahorros',
            'type' => 'savings',
            'balance' => 50000,
            'credit_limit' => 999999,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertCreated()
            ->assertJsonPath('data.type', 'SAVINGS')
            ->assertJsonPath('data.credit_limit', null);
    }

    public function test_account_rejects_an_unknown_type(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/accounts', [
            'name' => 'Invalida',
            'type' => 'investment',
            'balance' => 0,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertUnprocessable()
            ->assertJsonValidationErrors(['type']);
    }

    public function test_legacy_client_without_type_creates_a_bank_account(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->postJson('/api/v1/accounts', [
            'name' => 'Cuenta anterior',
            'balance' => 10000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertCreated()->assertJsonPath('data.type', 'BANK');

        $this->assertDatabaseHas('accounts', [
            'user_id' => $user->id,
            'name' => 'Cuenta anterior',
            'type' => 'BANK',
        ]);
    }
}
