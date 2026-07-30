<?php

namespace App\Console\Commands;

use App\Models\EmailCandidate;
use App\Models\User;
use App\Services\DuplicateEmailCandidateDetector;
use App\Services\UsdDopExchangeRateService;
use Illuminate\Console\Command;
use Illuminate\Support\Collection;

/**
 * Rellena la conversion de los candidatos que ya estaban guardados.
 *
 * La conversion se calcula al crear o actualizar el candidato durante la
 * sincronizacion, asi que los correos detectados antes de que existiera quedaron sin
 * ella: un cargo en dolares seguia sin poder clasificarse en una cuenta en pesos.
 *
 * Es idempotente: solo toca los que le faltan la conversion, de modo que se puede
 * repetir sin duplicar trabajo ni alterar lo ya convertido. Al terminar reconcilia
 * duplicados, porque una conversion recien puesta puede revelar que dos candidatos
 * eran el mismo cargo.
 */
class BackfillEmailCandidateConversions extends Command
{
    protected $signature = 'email:backfill-conversions
        {--user= : Limitar a un usuario por id}
        {--chunk=200 : Cuántos candidatos procesar por lote}
        {--dry-run : Informar lo que haría sin escribir nada}';

    protected $description = 'Convierte a pesos los candidatos de correo que quedaron sin conversión';

    public function handle(
        UsdDopExchangeRateService $exchangeRates,
        DuplicateEmailCandidateDetector $duplicates,
    ): int {
        $dryRun = (bool) $this->option('dry-run');
        $converted = 0;
        $skipped = 0;
        $touchedUsers = [];

        $query = EmailCandidate::query()
            ->whereNotNull('currency')
            ->where('currency', '!=', DuplicateEmailCandidateDetector::BASE_CURRENCY)
            ->whereNull('converted_amount')
            ->when($this->option('user'), fn ($q, $userId) => $q->where('user_id', $userId))
            ->orderBy('id');

        $total = $query->clone()->count();
        if ($total === 0) {
            $this->info('No hay candidatos pendientes de conversión.');

            return self::SUCCESS;
        }
        $this->info("Candidatos sin conversión: {$total}");

        $query->chunkById((int) $this->option('chunk'), function (Collection $batch) use (
            $exchangeRates,
            $dryRun,
            &$converted,
            &$skipped,
            &$touchedUsers
        ): void {
            foreach ($batch as $candidate) {
                $conversion = $exchangeRates->convertFrom(
                    $candidate->currency,
                    $candidate->amount,
                    $candidate->occurred_at,
                );
                if ($conversion === null) {
                    // Sin tasa utilizable se deja como esta: volver a correr el comando
                    // mas tarde lo reintenta, y entretanto no se inventa una cifra.
                    $skipped++;

                    continue;
                }
                if (! $dryRun) {
                    $candidate->update($conversion);
                }
                $touchedUsers[$candidate->user_id] = true;
                $converted++;
            }
        });

        $this->info($dryRun
            ? "Se convertirían {$converted}; {$skipped} sin tasa disponible."
            : "Convertidos: {$converted}. Sin tasa disponible: {$skipped}.");

        if ($dryRun || $touchedUsers === []) {
            return self::SUCCESS;
        }

        // Una conversion recien puesta puede revelar que dos candidatos eran el mismo
        // cargo visto por dos buzones distintos.
        $marked = 0;
        foreach (User::query()->whereKey(array_keys($touchedUsers))->cursor() as $user) {
            $marked += $duplicates->reconcile($user);
        }
        $this->info("Duplicados marcados: {$marked}.");

        return self::SUCCESS;
    }
}
