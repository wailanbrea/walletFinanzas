<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class HermesAgentApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_read_scoped_agent_can_read_agent_accounts_but_not_write(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.read'])->plainTextToken;

        $this->withToken($token)->getJson('/api/v1/agent/accounts')->assertOk();
        $this->withToken($token)->postJson('/api/v1/agent/accounts', [
            'name' => 'No debe crearse',
            'balance' => 100,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertForbidden();
    }

    public function test_agent_token_cannot_use_regular_wallet_routes(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.read', 'agent.write'])->plainTextToken;

        $this->withToken($token)->getJson('/api/v1/accounts')->assertForbidden();
        $this->withToken($token)->getJson('/api/v1/user')->assertForbidden();
    }

    public function test_write_scoped_agent_can_create_an_account(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        $this->withToken($token)->postJson('/api/v1/agent/accounts', [
            'name' => 'Cartera Hermes',
            'balance' => 20000,
            'currency' => 'DOP',
            'country_code' => 'DO',
        ])->assertCreated()->assertJsonPath('data.name', 'Cartera Hermes');
    }

    public function test_write_scoped_agent_can_update_and_close_debt(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        // Create a debt first
        $this->withToken($token)->postJson('/api/v1/agent/debts', [
            'id' => 'debt-123',
            'name' => 'Deuda Test',
            'description' => 'Prueba de agente',
            'direction' => 'OWED_TO_ME',
            'total_amount' => 500000,
            'paid_amount' => 0,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        // Update paid_amount and close the debt
        $response = $this->withToken($token)->patchJson('/api/v1/agent/debts/debt-123', [
            'paid_amount' => 500000,
            'is_closed' => true,
        ]);

        $response->assertOk()
            ->assertJsonPath('data.paid_amount', 500000)
            ->assertJsonPath('data.is_closed', true);
    }
}
