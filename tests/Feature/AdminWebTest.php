<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AdminWebTest extends TestCase
{
    use RefreshDatabase;

    public function test_root_and_login_follow_authentication_state(): void
    {
        $this->get('/')->assertRedirect('/login');
        $this->get('/login')->assertOk()->assertSee('Acceso administrativo')->assertSee('csrf-token');

        $admin = User::factory()->create(['is_admin' => true, 'password' => 'secure-password']);
        $this->post('/login', ['email' => $admin->email, 'password' => 'wrong'])
            ->assertSessionHasErrors('email');
        $this->post('/login', ['email' => $admin->email, 'password' => 'secure-password'])
            ->assertRedirect('/dashboard');
        $this->assertAuthenticatedAs($admin);
    }

    public function test_dashboard_requires_an_administrator(): void
    {
        $this->get('/dashboard')->assertRedirect('/login');
        $user = User::factory()->create(['is_admin' => false]);
        $this->actingAs($user)->get('/dashboard')->assertForbidden();
    }

    public function test_admin_can_consult_users_accounts_transactions_and_connections_without_tokens(): void
    {
        $admin = User::factory()->create(['is_admin' => true]);
        $owner = User::factory()->create(['email' => 'owner@example.com']);
        $account = Account::create([
            'user_id' => $owner->id, 'name' => 'Cuenta principal', 'balance' => 250000,
            'currency' => 'USD', 'is_active' => true,
        ]);
        Transaction::create([
            'user_id' => $owner->id, 'account_id' => $account->id,
            'amount' => 1999, 'currency' => 'USD', 'description' => 'Supermercado',
            'occurred_at' => now(), 'status' => 'posted',
        ]);
        $owner->emailConnections()->create([
            'provider' => 'gmail', 'email' => 'owner@gmail.com', 'access_token' => 'never-show-this',
            'refresh_token' => 'never-show-refresh', 'status' => 'connected', 'connected_at' => now(),
        ]);

        $this->actingAs($admin)->get('/dashboard')->assertOk()->assertSee('Panel de administración');
        $this->actingAs($admin)->get('/admin/users')->assertOk()->assertSee('owner@example.com');
        $this->actingAs($admin)->get('/admin/accounts')->assertOk()->assertSee('Cuenta principal');
        $this->actingAs($admin)->get('/admin/transactions')->assertOk()->assertSee('Supermercado');
        $this->actingAs($admin)->get('/admin/email-connections')
            ->assertOk()->assertSee('owner@gmail.com')
            ->assertDontSee('never-show-this')->assertDontSee('never-show-refresh');
    }

    public function test_admin_can_create_a_non_admin_user_and_disconnect_email(): void
    {
        $admin = User::factory()->create(['is_admin' => true]);
        $owner = User::factory()->create();
        $connection = $owner->emailConnections()->create([
            'provider' => 'microsoft', 'email' => 'owner@outlook.com',
            'access_token' => 'secret', 'status' => 'connected', 'connected_at' => now(),
        ]);

        $this->actingAs($admin)->post('/admin/users', [
            'name' => 'Nuevo usuario', 'email' => 'new@example.com',
            'password' => 'password-seguro', 'password_confirmation' => 'password-seguro',
        ])->assertRedirect('/admin/users');
        $this->assertDatabaseHas('users', ['email' => 'new@example.com', 'is_admin' => false]);

        $this->actingAs($admin)->delete('/admin/email-connections/'.$connection->id)
            ->assertRedirect('/admin/email-connections');
        $this->assertDatabaseHas('email_connections', [
            'id' => $connection->id,
            'status' => 'disconnected',
            'access_token' => null,
            'refresh_token' => null,
        ]);
    }
}
