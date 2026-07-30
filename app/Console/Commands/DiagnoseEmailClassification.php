<?php

namespace App\Console\Commands;

use App\Models\ProviderMessage;
use App\Services\FinancialEmailExtractor;
use Illuminate\Console\Command;
use Illuminate\Support\Collection;

/**
 * Mide que tan bien clasifica el extractor los correos ya guardados.
 *
 * Existe para no decidir a ciegas si conviene mejorar las reglas, meter un modelo de
 * lenguaje o traer mas texto del correo: primero hay que saber que falla y cuanto. Sin
 * numeros, cualquier cambio es una apuesta.
 *
 * No escribe nada: reprocesa en memoria y reporta.
 */
class DiagnoseEmailClassification extends Command
{
    protected $signature = 'email:diagnose
        {--user= : Limitar a un usuario por id}
        {--show=15 : Cuántos casos fallidos listar}';

    protected $description = 'Reporta qué tan bien se están clasificando los correos guardados';

    public function handle(FinancialEmailExtractor $extractor): int
    {
        $messages = ProviderMessage::query()
            ->when($this->option('user'), fn ($q, $userId) => $q->where('user_id', $userId))
            ->orderByDesc('occurred_at')
            ->get();

        if ($messages->isEmpty()) {
            $this->warn('No hay correos guardados que analizar.');

            return self::SUCCESS;
        }

        $discarded = [];
        $noMerchant = [];
        $noCategory = [];
        $noCard = 0;
        $classified = 0;
        $snippetLengths = [];

        foreach ($messages as $message) {
            $snippetLengths[] = mb_strlen((string) $message->snippet);
            $extracted = $extractor->extract($message->subject, $message->snippet, $message->occurred_at);

            if ($extracted === null) {
                // Puede ser correcto (no era un movimiento) o un fallo: por eso se listan.
                $discarded[] = $message->subject;

                continue;
            }
            $classified++;
            if (blank($extracted['merchant'])) {
                $noMerchant[] = $message->subject;
            }
            if (blank($extracted['category_suggestion'])) {
                $noCategory[] = $message->subject;
            }
            if (blank($extracted['card_last_four'])) {
                $noCard++;
            }
        }

        $total = $messages->count();
        $this->info("Correos analizados: {$total}");
        $this->line('  Reconocidos como movimiento: '.$this->withPercent($classified, $total));
        $this->line('  Descartados: '.$this->withPercent(count($discarded), $total));

        if ($classified > 0) {
            $this->newLine();
            $this->info('De los reconocidos:');
            $this->line('  Sin comercio identificado: '.$this->withPercent(count($noMerchant), $classified));
            $this->line('  Sin categoría sugerida: '.$this->withPercent(count($noCategory), $classified));
            $this->line('  Sin tarjeta detectada: '.$this->withPercent($noCard, $classified));
        }

        // El techo real: ningun clasificador puede leer lo que no se guardo.
        $lengths = collect($snippetLengths);
        $this->newLine();
        $this->info('Texto disponible por correo (asunto aparte):');
        $this->line('  Mediana: '.(int) $lengths->median().' caracteres');
        $this->line('  Máximo: '.$lengths->max().' caracteres');
        $this->line('  Vacíos: '.$lengths->filter(fn (int $n) => $n === 0)->count());

        $show = max(0, (int) $this->option('show'));
        $this->listFailures('Descartados (revisar si alguno sí era un movimiento)', $discarded, $show);
        $this->listFailures('Sin comercio', $noMerchant, $show);
        $this->listFailures('Sin categoría', $noCategory, $show);

        return self::SUCCESS;
    }

    private function withPercent(int $count, int $total): string
    {
        $percent = $total > 0 ? round($count / $total * 100) : 0;

        return "{$count} ({$percent}%)";
    }

    /**
     * Agrupa por asunto repetido: un mismo aviso que falla veinte veces es un patron
     * que se arregla con una regla, no veinte problemas distintos.
     */
    private function listFailures(string $title, array $subjects, int $show): void
    {
        if ($subjects === [] || $show === 0) {
            return;
        }
        $this->newLine();
        $this->info($title.':');
        Collection::make($subjects)
            ->map(fn (?string $subject) => trim((string) $subject) ?: '(sin asunto)')
            ->countBy()
            ->sortDesc()
            ->take($show)
            ->each(fn (int $times, string $subject) => $this->line("  {$times}×  ".mb_strimwidth($subject, 0, 90, '…')));
    }
}
