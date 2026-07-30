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

    /** Divisas de origen que el extractor puede reconocer. */
    private const SUPPORTED_SOURCES = ['usd', 'eur'];

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
        return $this->convertFrom('USD', $usdMinor, $occurredAt);
    }

    /**
     * Igual que [convertMinor] para cualquier divisa de origen soportada. Existe porque
     * el extractor tambien reconoce euros, y un cargo en EUR quedaba igual de
     * inclasificable que uno en USD.
     *
     * @return array{
     *     converted_amount:int,
     *     converted_currency:string,
     *     exchange_rate_micros:int,
     *     exchange_rate_at:CarbonImmutable,
     *     exchange_rate_source:string
     * }|null
     */
    public function convertFrom(string $currency, int $minor, CarbonImmutable $occurredAt): ?array
    {
        $from = strtolower($currency);
        if ($minor === 0 || ! in_array($from, self::SUPPORTED_SOURCES, true)) {
            return null;
        }

        $day = $occurredAt->utc()->startOfDay();
        $rateMicros = $this->rateMicrosFor($from, $day);
        if ($rateMicros === null || $rateMicros <= 0) {
            return null;
        }

        // El signo se preserva: un reembolso en USD sigue siendo un ingreso en DOP.
        $sign = $minor < 0 ? -1 : 1;
        $converted = intdiv(abs($minor) * $rateMicros, self::MICROS);
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

    /** Tasa origen→DOP de ese dia, en millonesimas. */
    private function rateMicrosFor(string $from, CarbonImmutable $day): ?int
    {
        $key = sprintf('%s_dop_rate_micros:%s', $from, $day->toDateString());

        $cached = Cache::get($key);
        if (is_int($cached)) {
            return $cached;
        }

        $rate = $this->fetchRate($from, $day);
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

    private function fetchRate(string $from, CarbonImmutable $day): ?float
    {
        // Una tasa no disponible no debe tumbar la sincronizacion del correo: el
        // candidato se guarda sin conversion y se puede reintentar despues.
        try {
            $response = Http::timeout(8)->retry(2, 250)->get(sprintf(
                'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@%s/v1/currencies/%s.json',
                $day->toDateString(),
                $from
            ));
            if (! $response->successful()) {
                return null;
            }
            $rate = data_get($response->json(), $from.'.dop');

            return is_numeric($rate) && (float) $rate > 0 ? (float) $rate : null;
        } catch (Throwable $exception) {
            report($exception);

            return null;
        }
    }
}
