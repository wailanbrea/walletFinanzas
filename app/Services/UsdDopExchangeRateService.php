<?php

namespace App\Services;

use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Throwable;

/**
 * Convierte importes en USD a DOP con la tasa del dia en que ocurrio el movimiento.
 *
 * Se usa la tasa historica y no la de hoy porque un gasto del mes pasado debe seguir
 * valiendo lo que valia entonces; con la tasa actual, el historial cambiaria solo.
 */
class UsdDopExchangeRateService
{
    public const SOURCE = 'fawaz-exchange-api-historical';

    /** Millonesimas: evita arrastrar errores de coma flotante en el dinero. */
    private const MICROS = 1_000_000;

    /** La tasa de un dia pasado no cambia, asi que se cachea generosamente. */
    private const CACHE_TTL_SECONDS = 86_400;

    /**
     * @return array{
     *     converted_amount:int,
     *     converted_currency:string,
     *     exchange_rate_micros:int,
     *     exchange_rate_at:CarbonImmutable,
     *     exchange_rate_source:string
     * }|null Null si no se pudo obtener una tasa utilizable: quien llama debe
     *        registrar el candidato sin conversion en vez de inventar una cifra.
     */
    public function convertMinor(int $usdMinor, CarbonImmutable $occurredAt): ?array
    {
        if ($usdMinor === 0) {
            return null;
        }

        $day = $occurredAt->utc()->startOfDay();
        $rateMicros = $this->rateMicrosFor($day);
        if ($rateMicros === null || $rateMicros <= 0) {
            return null;
        }

        // El signo se preserva: un reembolso en USD sigue siendo un ingreso en DOP.
        $sign = $usdMinor < 0 ? -1 : 1;
        $converted = intdiv(abs($usdMinor) * $rateMicros, self::MICROS);
        if ($converted === 0) {
            // Un importe que se redondea a cero no es una conversion util.
            return null;
        }

        return [
            'converted_amount' => $sign * $converted,
            'converted_currency' => 'DOP',
            'exchange_rate_micros' => $rateMicros,
            'exchange_rate_at' => $day,
            'exchange_rate_source' => self::SOURCE,
        ];
    }

    /** Tasa USD→DOP de ese dia, en millonesimas. */
    private function rateMicrosFor(CarbonImmutable $day): ?int
    {
        $key = 'usd_dop_rate_micros:'.$day->toDateString();

        $cached = Cache::get($key);
        if (is_int($cached)) {
            return $cached;
        }

        $rate = $this->fetchRate($day);
        if ($rate === null) {
            return null;
        }

        $micros = (int) round($rate * self::MICROS);
        if ($micros <= 0) {
            return null;
        }
        Cache::put($key, $micros, self::CACHE_TTL_SECONDS);

        return $micros;
    }

    private function fetchRate(CarbonImmutable $day): ?float
    {
        // Una tasa no disponible no debe tumbar la sincronizacion del correo: el
        // candidato se guarda sin conversion y se puede reintentar despues.
        try {
            $response = Http::timeout(8)->retry(2, 250)->get(sprintf(
                'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@%s/v1/currencies/usd.json',
                $day->toDateString()
            ));
            if (! $response->successful()) {
                return null;
            }
            $rate = data_get($response->json(), 'usd.dop');

            return is_numeric($rate) && (float) $rate > 0 ? (float) $rate : null;
        } catch (Throwable) {
            return null;
        }
    }
}
