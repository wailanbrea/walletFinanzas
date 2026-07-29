<?php

namespace App\Services;

use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use RuntimeException;
use Throwable;

class UsdDopExchangeRateService
{
    private const PRIMARY_ENDPOINT = 'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@%s/v1/currencies/usd.json';

    private const FALLBACK_ENDPOINT = 'https://%s.currency-api.pages.dev/v1/currencies/usd.json';

    public const SOURCE = 'fawaz-exchange-api-historical';

    private const LOCAL_TIMEZONE = 'America/Santo_Domingo';

    private const RATE_SCALE = 1_000_000;

    /** @return array{converted_amount:int,converted_currency:string,exchange_rate_micros:int,exchange_rate_at:CarbonImmutable,exchange_rate_source:string} */
    public function convertMinor(int $usdMinor, CarbonImmutable $occurredAt): array
    {
        $quote = $this->quote($occurredAt);
        if ($usdMinor === PHP_INT_MIN) {
            throw new RuntimeException('El monto USD excede el rango soportado.');
        }

        $absolute = abs($usdMinor);
        if ($absolute > intdiv(PHP_INT_MAX, $quote['rate_micros'])) {
            throw new RuntimeException('La conversión USD/DOP excede el rango soportado.');
        }

        $converted = intdiv(($absolute * $quote['rate_micros']) + intdiv(self::RATE_SCALE, 2), self::RATE_SCALE);

        return [
            'converted_amount' => $usdMinor < 0 ? -$converted : $converted,
            'converted_currency' => 'DOP',
            'exchange_rate_micros' => $quote['rate_micros'],
            'exchange_rate_at' => $quote['quoted_at'],
            'exchange_rate_source' => self::SOURCE,
        ];
    }

    /** @return array{rate_micros:int,quoted_at:CarbonImmutable} */
    private function quote(CarbonImmutable $occurredAt): array
    {
        $date = $occurredAt->setTimezone(self::LOCAL_TIMEZONE)->toDateString();
        $today = CarbonImmutable::now(self::LOCAL_TIMEZONE)->toDateString();
        if ($date > $today) {
            throw new RuntimeException('No se puede consultar una tasa para una fecha futura.');
        }

        return Cache::remember('fx.usd_dop.historical.v1.'.$date, now()->addDays(30), function () use ($date): array {
            $urls = [
                sprintf(self::PRIMARY_ENDPOINT, $date),
                sprintf(self::FALLBACK_ENDPOINT, $date),
            ];
            $lastError = null;
            foreach ($urls as $url) {
                try {
                    $response = Http::acceptJson()->timeout(10)->get($url);
                    if (! $response->successful()) {
                        throw new RuntimeException('La fuente histórica respondió HTTP '.$response->status().'.');
                    }
                    $body = $response->body();
                    $payload = $response->json();
                    if (($payload['date'] ?? null) !== $date || ! is_array($payload['usd'] ?? null)) {
                        throw new RuntimeException('La fuente histórica devolvió una fecha o base inválida.');
                    }
                    if (! preg_match('/"dop"\s*:\s*"?(?<rate>[0-9]+(?:\.[0-9]+)?)"?/u', $body, $match)) {
                        throw new RuntimeException('La fuente histórica no incluyó la tasa DOP.');
                    }

                    return [
                        'rate_micros' => $this->decimalToMicros($match['rate']),
                        'quoted_at' => CarbonImmutable::parse($date, 'UTC')->startOfDay(),
                    ];
                } catch (Throwable $exception) {
                    $lastError = $exception;
                }
            }

            throw new RuntimeException(
                'No se pudo obtener la tasa histórica USD/DOP para '.$date.'.',
                previous: $lastError,
            );
        });
    }

    private function decimalToMicros(string $rate): int
    {
        [$whole, $fraction] = array_pad(explode('.', $rate, 2), 2, '');
        $fraction = str_pad($fraction, 7, '0');
        $micros = ((int) $whole * self::RATE_SCALE) + (int) substr($fraction, 0, 6);
        if ((int) $fraction[6] >= 5) {
            $micros++;
        }
        if ($micros <= 0) {
            throw new RuntimeException('La tasa USD/DOP debe ser positiva.');
        }

        return $micros;
    }
}
