<?php

namespace Tests\Feature;

use App\Models\EmailConnection;
use App\Models\ProviderMessage;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class DiagnoseEmailClassificationTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_reports_what_the_extractor_gets_and_misses(): void
    {
        $user = User::factory()->create();
        // Uno completo, uno sin comercio, y uno que no es movimiento.
        $this->message($user, 'Compra aprobada', 'Compra aprobada por RD$1,250.00 en Supermercado Nacional');
        $this->message($user, 'Notificación de Consumo', 'Consumo aprobado por RD$800.00');
        $this->message($user, 'Boletín semanal', 'Novedades de la semana, no responda a este correo');

        $this->artisan('email:diagnose')
            ->expectsOutputToContain('Correos analizados: 3')
            ->expectsOutputToContain('Reconocidos como movimiento')
            ->expectsOutputToContain('Sin comercio identificado')
            ->expectsOutputToContain('Sin categoría específica')
            // El techo: se reporta cuanto texto habia para clasificar.
            ->expectsOutputToContain('Texto disponible por correo')
            ->assertSuccessful();
    }

    public function test_it_can_dump_the_text_the_extractor_actually_sees(): void
    {
        $user = User::factory()->create();
        // Un aviso reconocido pero sin comercio: es el que hay que poder mirar.
        $this->message($user, 'Notificación de Consumo', 'Consumo aprobado por RD$800.00 | Referencia | 998877');

        $this->artisan('email:diagnose', ['--dump' => 3])
            ->expectsOutputToContain('Texto que ve el extractor en los casos sin comercio')
            ->expectsOutputToContain('Referencia | 998877')
            ->assertSuccessful();
    }

    public function test_without_dump_the_text_is_not_printed(): void
    {
        $user = User::factory()->create();
        $this->message($user, 'Notificación de Consumo', 'Consumo aprobado por RD$800.00 | Referencia | 998877');

        $this->artisan('email:diagnose')
            ->doesntExpectOutputToContain('Texto que ve el extractor')
            ->assertSuccessful();
    }

    public function test_it_says_so_when_there_is_nothing_to_analyse(): void
    {
        $this->artisan('email:diagnose')
            ->expectsOutputToContain('No hay correos guardados que analizar.')
            ->assertSuccessful();
    }

    public function test_it_can_be_limited_to_one_user(): void
    {
        $mine = User::factory()->create();
        $theirs = User::factory()->create();
        $this->message($mine, 'Compra aprobada', 'Compra aprobada por RD$100.00 en Colmado');
        $this->message($theirs, 'Compra aprobada', 'Compra aprobada por RD$200.00 en Farmacia');
        $this->message($theirs, 'Compra aprobada', 'Compra aprobada por RD$300.00 en Farmacia');

        $this->artisan('email:diagnose', ['--user' => $mine->id])
            ->expectsOutputToContain('Correos analizados: 1')
            ->assertSuccessful();
    }

    private function message(User $user, string $subject, string $snippet): void
    {
        $connection = EmailConnection::query()->firstOrCreate(
            ['user_id' => $user->id, 'provider' => 'gmail'],
            ['email' => 'x@example.com', 'access_token' => 'token', 'connected_at' => now()]
        );
        ProviderMessage::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'provider_message_id' => uniqid('m', true),
            'subject' => $subject,
            'snippet' => $snippet,
            'occurred_at' => '2026-07-25T14:00:00Z',
        ]);
    }
}
