<?php

namespace App\Console\Commands;

use App\Models\ProviderMessage;
use App\Services\EmailMailboxScanner;
use Illuminate\Console\Command;
use Illuminate\Support\Collection;
use Throwable;

/**
 * Rellena el cuerpo de los correos guardados cuando solo se pedia la vista previa.
 *
 * Sin esto no hay forma de saber si traer el cuerpo mejora la clasificacion: el dedupe
 * por provider_message_id impide que el barrido normal vuelva a mirar los historicos, asi
 * que habria que esperar dias de correos nuevos para tener con que comparar.
 *
 * Solo toca provider_messages.snippet. No crea ni modifica candidatos: el objetivo es
 * poder medir con email:diagnose antes de cambiar nada que el usuario ya reviso.
 */
class RefetchEmailBodies extends Command
{
    protected $signature = 'email:refetch-bodies
        {--user= : Limitar a un usuario por id}
        {--limit=200 : Cuántos mensajes releer como máximo}
        {--min-length=300 : Solo releer los que tengan menos texto que esto}
        {--dry-run : Informar sin escribir nada}';

    protected $description = 'Vuelve a bajar el cuerpo de los correos guardados con solo la vista previa';

    public function handle(EmailMailboxScanner $scanner): int
    {
        $dryRun = (bool) $this->option('dry-run');
        $minLength = (int) $this->option('min-length');

        $messages = ProviderMessage::query()
            ->with('connection')
            ->when($this->option('user'), fn ($q, $userId) => $q->where('user_id', $userId))
            ->orderByDesc('occurred_at')
            ->limit((int) $this->option('limit'))
            ->get()
            // El filtro va aqui y no en SQL para que la longitud se mida en caracteres
            // y no en bytes: los acentos ocupan dos y falsearian el corte.
            ->filter(fn (ProviderMessage $m) => mb_strlen((string) $m->snippet) < $minLength);

        if ($messages->isEmpty()) {
            $this->info('No hay correos con texto corto que releer.');

            return self::SUCCESS;
        }

        $this->info("Correos por releer: {$messages->count()}");
        $before = [];
        $after = [];
        $grew = 0;
        $unchanged = 0;
        $unavailable = 0;

        foreach ($messages as $message) {
            $connection = $message->connection;
            if ($connection === null) {
                $unavailable++;

                continue;
            }
            try {
                $body = $scanner->refetchBodyText($connection, $message->provider_message_id);
            } catch (Throwable $exception) {
                // Un mensaje que falla no debe tumbar el resto: se cuenta y se sigue.
                $this->warn("  {$message->provider_message_id}: {$exception->getMessage()}");
                $unavailable++;

                continue;
            }
            $oldLength = mb_strlen((string) $message->snippet);
            if ($body === null || mb_strlen($body) <= $oldLength) {
                $unchanged++;

                continue;
            }
            $before[] = $oldLength;
            $after[] = mb_strlen($body);
            $grew++;
            if (! $dryRun) {
                $message->update(['snippet' => $body]);
            }
        }

        $this->newLine();
        $this->info($dryRun ? 'Simulación (no se escribió nada):' : 'Resultado:');
        $this->line("  Con más texto que antes: {$grew}");
        // Un mensaje que el proveedor ya no tiene tambien cae aqui: desde fuera no se
        // distingue de uno sin cuerpo legible, y para medir da igual.
        $this->line("  Sin más texto que antes: {$unchanged}");
        $this->line("  Con error de conexión: {$unavailable}");

        if ($grew > 0) {
            $this->line('  Mediana antes: '.(int) Collection::make($before)->median().' caracteres');
            $this->line('  Mediana ahora: '.(int) Collection::make($after)->median().' caracteres');
            $this->newLine();
            $this->info('Ahora corre email:diagnose para comparar contra la línea base.');
        }

        return self::SUCCESS;
    }
}
