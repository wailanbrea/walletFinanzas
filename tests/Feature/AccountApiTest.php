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

    public function test_accounts_reject_sanctum_tokens_without_wallet_ability(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('limited-client', ['profile:read'])->plainTextToken;

        $this->withToken($token)
            ->getJson('/api/v1/accounts')
            ->assertForbidden();
    }

    public function test_authenticated_user_can_create_and_list_only_their_accounts(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner, ['wallet']);

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
        Sanctum::actingAs($otherUser, ['wallet']);
        $this->postJson('/api/v1/accounts', [
            'name' => 'Cuenta ajena',
            'balance' => 999,
            'currency' => 'USD',
            'country_code' => 'US',
        ])->assertCreated();

        Sanctum::actingAs($owner, ['wallet']);
        $this->getJson('/api/v1/accounts')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.id', $created['id']);
    }

    public function test_account_rejects_decimal_amounts_and_invalid_card_digits(): void
    {
        Sanctum::actingAs(User::factory()->create(), ['wallet']);

        $this->postJson('/api/v1/accounts', [
            'name' => 'Inválida',
            'balance' => 10.50,
            'currency' => 'DOP',
            'country_code' => 'DO',
            'card_last_four' => '12AB',
        ])->assertUnprocessable()
            ->assertJsonValidationErrors(['balance', 'card_last_four']);
    }
}
