<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AuthApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_user_can_register_and_receive_a_sanctum_token(): void
    {
        $response = $this->postJson('/api/v1/auth/register', [
            'name' => 'Usuario Demo',
            'email' => 'demo@example.com',
            'password' => 'Password123!',
            'password_confirmation' => 'Password123!',
            'device_name' => 'android-test',
        ]);

        $response
            ->assertCreated()
            ->assertJsonPath('data.user.email', 'demo@example.com')
            ->assertJsonStructure(['data' => ['token', 'user' => ['id', 'name', 'email']]]);

        $this->assertDatabaseHas('users', ['email' => 'demo@example.com']);
    }

    public function test_user_can_login_and_invalid_credentials_are_rejected(): void
    {
        $this->postJson('/api/v1/auth/register', [
            'name' => 'Usuario Demo',
            'email' => 'demo@example.com',
            'password' => 'Password123!',
            'password_confirmation' => 'Password123!',
            'device_name' => 'android-test',
        ])->assertCreated();

        $this->postJson('/api/v1/auth/login', [
            'email' => 'demo@example.com',
            'password' => 'Password123!',
            'device_name' => 'android-test',
        ])->assertOk()->assertJsonStructure(['data' => ['token']]);

        $this->postJson('/api/v1/auth/login', [
            'email' => 'demo@example.com',
            'password' => 'incorrecta',
            'device_name' => 'android-test',
        ])->assertUnprocessable()->assertJsonValidationErrors(['email']);
    }
}
