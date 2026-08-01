<?php

namespace App\Services;

use App\Models\EmailCandidate;
use App\Models\EmailMailbox;
use App\Models\User;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

/**
 * Empareja el mismo cargo visto por dos buzones distintos.
 *
 * PayPal avisa "USD 355" y el banco emisor avisa "RD$21,000" del mismo consumo. El
 * dedupe por mensaje no puede verlos como uno porque su clave incluye el proveedor, de
 * modo que el gasto se contaba dos veces.
 *
 * Se conserva el candidato en la divisa base (DOP) y el otro se marca como duplicado:
 * el importe en DOP es el que el banco realmente cobro, mientras el de PayPal depende
 * de una tasa estimada.
 */
class DuplicateEmailCandidateDetector
{
    /** Ventana de emparejamiento: el banco y la pasarela no siempre notifican el mismo dia. */
    private const WINDOW_HOURS = 72;

    /**
     * Tolerancia sobre el importe comparado. La conversion usa la tasa de cierre del
     * dia y el banco aplica la suya con su margen, asi que exigir igualdad exacta no
     * emparejaria nunca. Un 3% distingue el mismo cargo de dos compras parecidas.
     */
    private const AMOUNT_TOLERANCE = 0.03;

    public const BASE_CURRENCY = 'DOP';

    /** @return int Cuántos candidatos se marcaron como duplicados. */
    public function reconcile(User $user): int
    {
        $pending = EmailCandidate::query()
            ->where('user_id', $user->id)
            ->where('status', 'pending')
            ->whereNull('duplicate_of_id')
            ->with('message.connection')
            ->orderBy('occurred_at')
            ->get();

        $marked = 0;
        // Se recorre de mas antiguo a mas nuevo y solo se marca el candidato que pierde,
        // asi que un mismo par no se evalua dos veces en sentidos opuestos.
        $discarded = [];

        foreach ($pending as $candidate) {
            if (in_array($candidate->id, $discarded, true)) {
                continue;
            }
            $twin = $this->findTwin($candidate, $pending, $discarded);
            if (! $twin) {
                continue;
            }

            [$keep, $drop] = $this->decideWinner($candidate, $twin);
            $persisted = DB::transaction(function () use ($drop, $keep): bool {
                $updated = EmailCandidate::query()
                    ->whereKey($drop->id)
                    ->where('status', 'pending')
                    ->update(['status' => 'duplicate', 'duplicate_of_id' => $keep->id]);
                if ($updated !== 1) {
                    return false;
                }
                $connection = $drop->message?->connection;
                if ($connection) {
                    $mailbox = EmailMailbox::forConnection($connection);
                    $mailbox->decisions()->updateOrCreate(
                        ['provider_message_id' => $drop->message->provider_message_id],
                        ['status' => 'duplicate', 'category' => null, 'decided_at' => now()]
                    );
                }

                return true;
            });
            if (! $persisted) {
                continue;
            }
            $discarded[] = $drop->id;
            $marked++;
        }

        return $marked;
    }

    /**
     * @param  Collection<int, EmailCandidate>  $pending
     * @param  array<int, string>  $discarded
     */
    private function findTwin(EmailCandidate $candidate, Collection $pending, array $discarded): ?EmailCandidate
    {
        return $pending->first(function (EmailCandidate $other) use ($candidate, $discarded): bool {
            if ($other->id === $candidate->id || in_array($other->id, $discarded, true)) {
                return false;
            }
            // Distinto proveedor: dos avisos del mismo buzon son dos cargos distintos,
            // y para eso ya esta el dedupe por mensaje.
            if ($other->provider === $candidate->provider) {
                return false;
            }
            if ($other->direction !== $candidate->direction) {
                return false;
            }
            if ($candidate->occurred_at->diffInHours($other->occurred_at, true) > self::WINDOW_HOURS) {
                return false;
            }

            return $this->amountsMatch($candidate, $other);
        });
    }

    /**
     * Compara los dos importes en la divisa base. Un candidato en USD solo se puede
     * comparar si trae su conversion: sin ella no hay forma honesta de saber si es el
     * mismo cargo, y adivinarlo ocultaria un gasto real.
     */
    private function amountsMatch(EmailCandidate $first, EmailCandidate $second): bool
    {
        $a = $this->baseAmount($first);
        $b = $this->baseAmount($second);
        if ($a === null || $b === null) {
            return false;
        }

        $reference = max(abs($a), abs($b));
        if ($reference === 0) {
            return false;
        }

        return abs(abs($a) - abs($b)) / $reference <= self::AMOUNT_TOLERANCE;
    }

    /** Importe del candidato expresado en la divisa base, o null si no se puede. */
    private function baseAmount(EmailCandidate $candidate): ?int
    {
        if ($candidate->currency === self::BASE_CURRENCY) {
            return $candidate->amount;
        }
        if ($candidate->converted_currency === self::BASE_CURRENCY) {
            return $candidate->converted_amount;
        }

        return null;
    }

    /**
     * Gana el candidato en la divisa base: ese importe es el que el banco cobro de
     * verdad, mientras el otro depende de una tasa estimada. Si ambos estan en la
     * misma divisa, gana el mas antiguo, que es el aviso original.
     *
     * @return array{0: EmailCandidate, 1: EmailCandidate} [conservado, duplicado]
     */
    private function decideWinner(EmailCandidate $first, EmailCandidate $second): array
    {
        $firstIsBase = $first->currency === self::BASE_CURRENCY;
        $secondIsBase = $second->currency === self::BASE_CURRENCY;

        if ($firstIsBase && ! $secondIsBase) {
            return [$first, $second];
        }
        if ($secondIsBase && ! $firstIsBase) {
            return [$second, $first];
        }

        return $first->occurred_at->lessThanOrEqualTo($second->occurred_at)
            ? [$first, $second]
            : [$second, $first];
    }
}
