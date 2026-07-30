<?php

namespace Tests\Unit;

use App\Services\UsdDopExchangeRateService;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class UsdDopExchangeRateServiceTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        Cache::flush();
    }

    public function test_it_converts_using_the_rate_of_the_day_the_charge_happened(): void
    {
        // La tasa es la del día del cargo, no la de hoy: un gasto pasado debe seguir
        // valiendo lo que valía entonces.
        Http::fake(['*2026-07-20*' => Http::response(['usd' => ['dop' => 60.5]])]);

        $result = (new UsdDopExchangeRateService)->convertMinor(
            35_500, // USD 355.00
            CarbonImmutable::parse('2026-07-20T18:30:00Z'),
        );

        $this->assertNotNull($result);
        $this->assertSame(2_147_750, $result['converted_amount']); // RD$21,477.50
        $this->assertSame('DOP', $result['converted_currency']);
        $this->assertSame(60_500_000, $result['exchange_rate_micros']);
        $this->assertSame('fawaz-exchange-api-historical', $result['exchange_rate_source']);
        $this->assertSame('2026-07-20', $result['exchange_rate_at']->toDateString());
    }

    public function test_a_refund_keeps_its_sign(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);

        $result = (new UsdDopExchangeRateService)->convertMinor(-1_000, CarbonImmutable::parse('2026-07-20T00:00:00Z'));

        // Un reembolso en USD sigue siendo un ingreso en DOP.
        $this->assertSame(-60_000, $result['converted_amount']);
    }

    public function test_it_returns_null_instead_of_inventing_a_rate(): void
    {
        $service = new UsdDopExchangeRateService;
        $day = CarbonImmutable::parse('2026-07-20T00:00:00Z');

        Http::fake(['*' => Http::response(status: 503)]);
        $this->assertNull($service->convertMinor(35_500, $day));

        Cache::flush();
        Http::fake(['*' => Http::response(['usd' => []])]);
        $this->assertNull($service->convertMinor(35_500, $day));

        Cache::flush();
        Http::fake(['*' => Http::response(['usd' => ['dop' => 0]])]);
        $this->assertNull($service->convertMinor(35_500, $day));
    }

    public function test_a_zero_amount_has_nothing_to_convert(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);

        $this->assertNull((new UsdDopExchangeRateService)->convertMinor(0, CarbonImmutable::now()));
    }

    public function test_the_rate_of_a_past_day_is_fetched_once(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);
        $service = new UsdDopExchangeRateService;
        $day = CarbonImmutable::parse('2026-07-20T00:00:00Z');

        $service->convertMinor(1_000, $day);
        $service->convertMinor(2_000, $day);

        // La tasa de un día pasado no cambia: se cachea en vez de pedirla otra vez.
        Http::assertSentCount(1);
    }
}
