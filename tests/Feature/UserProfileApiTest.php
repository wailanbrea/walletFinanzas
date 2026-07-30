<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class UserProfileApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_the_name_survives_the_round_trip(): void
    {
        $user = User::factory()->create(['name' => 'Nombre del registro']);

        // Sin esto el nombre solo vivia en el telefono: al abrir sesion en otro
        // dispositivo se veia el del registro y no el que el usuario habia puesto.
        Sanctum::actingAs($user);
        $this->patchJson('/api/v1/user', ['name' => 'Wailan Brea'])
            ->assertOk()
            ->assertJsonPath('data.name', 'Wailan Brea');

        $this->getJson('/api/v1/user')
            ->assertOk()
            ->assertJsonPath('data.name', 'Wailan Brea');

        $this->assertDatabaseHas('users', ['id' => $user->id, 'name' => 'Wailan Brea']);
    }

    public function test_it_is_authenticated(): void
    {
        $this->getJson('/api/v1/user')->assertUnauthorized();
        $this->patchJson('/api/v1/user', ['name' => 'X'])->assertUnauthorized();
    }

    public function test_an_empty_name_is_rejected(): void
    {
        $user = User::factory()->create(['name' => 'Wailan']);
        Sanctum::actingAs($user);

        // Un nombre en blanco dejaria el perfil sin identificar en todos los dispositivos.
        $this->patchJson('/api/v1/user', ['name' => '   '])->assertStatus(422);
        $this->assertDatabaseHas('users', ['id' => $user->id, 'name' => 'Wailan']);
    }

    public function test_the_email_is_never_changed_here(): void
    {
        $user = User::factory()->create(['email' => 'wailan@example.com']);
        Sanctum::actingAs($user);

        // El correo es la credencial de acceso: cambiarlo por esta via dejaria al usuario
        // fuera de su propia cuenta sin ninguna confirmacion.
        $this->patchJson('/api/v1/user', ['name' => 'Otro', 'email' => 'otro@example.com'])
            ->assertOk()
            ->assertJsonPath('data.email', 'wailan@example.com');

        $this->assertDatabaseHas('users', ['id' => $user->id, 'email' => 'wailan@example.com']);
    }

    public function test_a_user_only_sees_their_own_profile(): void
    {
        User::factory()->create(['name' => 'Ajeno']);
        $mine = User::factory()->create(['name' => 'Mio']);
        Sanctum::actingAs($mine);

        $this->getJson('/api/v1/user')->assertJsonPath('data.name', 'Mio');
    }
}
