<?php

namespace Tests\Feature;

use App\Models\EmailConnection;
use App\Models\ProviderMessage;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class RefetchEmailBodiesTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_replaces_the_preview_with_the_full_body(): void
    {
        $this->fakeGmailBody('<tr><td>Comercio</td><td>SUPERMERCADO NACIONAL</td></tr><p>RD$1,250.00</p>');
        $message = $this->shortMessage('Compra aprobada por RD$1,250.00');

        $this->artisan('email:refetch-bodies')
            ->expectsOutputToContain('Correos por releer: 1')
            ->expectsOutputToContain('Con más texto que antes: 1')
            ->assertSuccessful();

        $snippet = $message->fresh()->snippet;
        // Lo que la vista previa no alcanzaba a mostrar.
        $this->assertStringContainsString('SUPERMERCADO NACIONAL', $snippet);
    }

    public function test_a_dry_run_writes_nothing(): void
    {
        $this->fakeGmailBody('<p>Comercio | FARMACIA CAROL</p>');
        $message = $this->shortMessage('Consumo aprobado');

        $this->artisan('email:refetch-bodies', ['--dry-run' => true])
            ->expectsOutputToContain('Simulación (no se escribió nada)')
            ->assertSuccessful();

        $this->assertSame('Consumo aprobado', $message->fresh()->snippet);
    }

    public function test_messages_that_already_have_text_are_left_alone(): void
    {
        $this->fakeGmailBody('<p>algo</p>');
        $long = str_repeat('a', 400);
        $message = $this->shortMessage($long);

        $this->artisan('email:refetch-bodies')
            ->expectsOutputToContain('No hay correos con texto corto que releer.')
            ->assertSuccessful();

        $this->assertSame($long, $message->fresh()->snippet);
    }

    public function test_a_body_that_is_not_longer_does_not_overwrite_what_was_there(): void
    {
        $this->fakeGmailBody('<p>corto</p>');
        $message = $this->shortMessage('Consumo aprobado por RD$800.00 en Colmado');

        $this->artisan('email:refetch-bodies')
            ->expectsOutputToContain('Sin más texto que antes: 1')
            ->assertSuccessful();

        $this->assertSame('Consumo aprobado por RD$800.00 en Colmado', $message->fresh()->snippet);
    }

    public function test_a_message_the_provider_no_longer_has_is_left_untouched(): void
    {
        Http::fake(['gmail.googleapis.com/*' => Http::response(status: 404)]);
        $message = $this->shortMessage('Consumo aprobado');

        // Se cuenta con los que no ganaron texto: desde fuera no se distingue de un
        // mensaje sin cuerpo legible, y para medir da igual.
        $this->artisan('email:refetch-bodies')
            ->expectsOutputToContain('Sin más texto que antes: 1')
            ->assertSuccessful();

        $this->assertSame('Consumo aprobado', $message->fresh()->snippet);
    }

    private function fakeGmailBody(string $html): void
    {
        Http::fake([
            'gmail.googleapis.com/*' => Http::response([
                'payload' => [
                    'mimeType' => 'text/html',
                    'body' => ['data' => strtr(base64_encode($html), '+/', '-_')],
                ],
            ]),
        ]);
    }

    private function shortMessage(string $snippet): ProviderMessage
    {
        $user = User::factory()->create();
        $connection = EmailConnection::query()->create([
            'user_id' => $user->id,
            'provider' => 'gmail',
            'email' => 'x@example.com',
            'access_token' => 'token',
            'connected_at' => now(),
            'expires_at' => now()->addHour(),
        ]);

        return ProviderMessage::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => 'gmail',
            'provider_message_id' => 'msg-1',
            'subject' => 'Aviso',
            'snippet' => $snippet,
            'occurred_at' => '2026-07-28T12:00:00Z',
        ]);
    }
}
