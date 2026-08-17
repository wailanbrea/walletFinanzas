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

    public function test_update_debt_rejects_negative_total_amount(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        $this->withToken($token)->postJson('/api/v1/agent/debts', [
            'id' => 'debt-456',
            'name' => 'Deuda Test',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 100000,
            'paid_amount' => 0,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        $this->withToken($token)->patchJson('/api/v1/agent/debts/debt-456', [
            'total_amount' => -50000,
        ])->assertUnprocessable();
    }

    public function test_update_debt_rejects_paid_amount_greater_than_total(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        $this->withToken($token)->postJson('/api/v1/agent/debts', [
            'id' => 'debt-789',
            'name' => 'Deuda Test',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 100000,
            'paid_amount' => 0,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        // Sending both total_amount and paid_amount where paid > total triggers validation
        $this->withToken($token)->patchJson('/api/v1/agent/debts/debt-789', [
            'total_amount' => 100000,
            'paid_amount' => 150000,
        ])->assertUnprocessable();
    }

    public function test_update_debt_rejects_closed_debt_with_mismatched_paid_amount(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        $this->withToken($token)->postJson('/api/v1/agent/debts', [
            'id' => 'debt-101',
            'name' => 'Deuda Test',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 100000,
            'paid_amount' => 50000,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        $this->withToken($token)->patchJson('/api/v1/agent/debts/debt-101', [
            'is_closed' => true,
            'paid_amount' => 80000,
        ])->assertUnprocessable();
    }

    // BUG: gte:paid_amount está en total_amount, pero con 'sometimes' no se evalúa
    // si el PATCH solo trae paid_amount. Esto permite paid_amount > total_amount.
    public function test_update_debt_allows_paid_amount_greater_than_total_when_only_paid_amount_sent(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        $this->withToken($token)->postJson('/api/v1/agent/debts', [
            'id' => 'debt-bug1',
            'name' => 'Deuda Test',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 100000,
            'paid_amount' => 0,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        // BUG: paid_amount > total_amount pero no se envía total_amount en el PATCH,
        // por lo que la regla gte:paid_amount en total_amount no se evalúa.
        $this->withToken($token)->patchJson('/api/v1/agent/debts/debt-bug1', [
            'paid_amount' => 150000,
        ])->assertUnprocessable();
    }

    // BUG: si is_closed viene true pero paid_amount es null (no se envía),
    // la comprobación manual se salta y se puede cerrar una deuda con 0 pagado.
    public function test_update_debt_allows_closing_debt_with_zero_paid_amount(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('hermes-test', ['agent.write'])->plainTextToken;

        $this->withToken($token)->postJson('/api/v1/agent/debts', [
            'id' => 'debt-bug2',
            'name' => 'Deuda Test',
            'description' => '',
            'direction' => 'I_OWE',
            'total_amount' => 100000,
            'paid_amount' => 50000,
            'is_closed' => false,
            'is_deleted' => false,
        ])->assertCreated();

        // BUG: cierra la deuda sin enviar paid_amount (null), la comprobación
        // manual salta y la deuda queda cerrada con solo 50.000 de 100.000 pagados.
        $this->withToken($token)->patchJson('/api/v1/agent/debts/debt-bug2', [
            'is_closed' => true,
        ])->assertUnprocessable();
    }
}
