<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Auth\Notifications\ResetPassword;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Password;
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

    public function test_login_rotates_same_device_token_and_sets_expiration(): void
    {
        $user = User::factory()->create([
            'email' => 'demo@example.com',
            'password' => Hash::make('Password123!'),
        ]);
        $user->createToken('android-test');
        $user->createToken('tablet');

        $this->postJson('/api/v1/auth/login', [
            'email' => 'demo@example.com',
            'password' => 'Password123!',
            'device_name' => 'android-test',
        ])->assertOk();

        $this->assertSame(1, $user->tokens()->where('name', 'android-test')->count());
        $this->assertSame(1, $user->tokens()->where('name', 'tablet')->count());
        $this->assertNotNull($user->tokens()->where('name', 'android-test')->firstOrFail()->expires_at);
    }

    public function test_password_recovery_is_non_enumerative_and_validates_email(): void
    {
        Notification::fake();
        $user = User::factory()->create(['email' => 'demo@example.com']);

        $expectedMessage = 'Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.';

        $this->postJson('/api/v1/auth/forgot-password', ['email' => $user->email])
            ->assertOk()
            ->assertJsonPath('message', $expectedMessage);

        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'unknown@example.com'])
            ->assertOk()
            ->assertJsonPath('message', $expectedMessage);

        $this->postJson('/api/v1/auth/forgot-password', ['email' => 'invalid'])
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['email']);

        Notification::assertSentTo($user, ResetPassword::class, function (ResetPassword $notification) use ($user): bool {
            $url = $notification->toMail($user)->actionUrl;

            return str_contains($url, '/reset-password/') && str_contains($url, 'email=');
        });
    }

    public function test_user_can_complete_password_reset_with_valid_token(): void
    {
        $user = User::factory()->create([
            'email' => 'demo@example.com',
            'password' => Hash::make('OldPassword123!'),
        ]);
        $token = Password::createToken($user);

        $this->post('/reset-password', [
            'token' => $token,
            'email' => $user->email,
            'password' => 'NewPassword123!',
            'password_confirmation' => 'NewPassword123!',
        ])->assertRedirect('/login');

        $this->assertTrue(Hash::check('NewPassword123!', $user->fresh()->password));
    }
}
