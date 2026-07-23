<?php

namespace Tests\Feature;

use App\Models\BankConnection;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class BankConnectionApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_bank_connections_require_authentication_and_are_isolated_by_user(): void
    {
        $this->getJson('/api/v1/bank-connections')->assertUnauthorized();

        $owner = User::factory()->create();
        $other = User::factory()->create();

        BankConnection::create([
            'user_id' => $owner->id,
            'provider_name' => 'Salt Edge',
            'provider_code' => 'SE_DO_001',
            'country_code' => 'DO',
            'status' => 'connected',
            'last_sync_at' => '2026-07-20T14:30:00Z',
        ]);
        BankConnection::create([
            'user_id' => $other->id,
            'provider_name' => 'Proveedor ajeno',
            'provider_code' => 'OTHER',
            'country_code' => 'US',
            'status' => 'connected',
        ]);

        Sanctum::actingAs($owner);
        $this->getJson('/api/v1/bank-connections')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.provider_code', 'SE_DO_001')
            ->assertJsonPath('data.0.last_sync_at', '2026-07-20T14:30:00.000000Z');
    }
}
