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

    public function test_converts_minor_units_using_the_transaction_date_and_caches_the_historical_rate(): void
    {
        Http::fake([
            'cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@2026-07-20/v1/currencies/usd.json' => Http::response(
                '{"date":"2026-07-20","usd":{"dop":61.234567}}',
                200,
                ['Content-Type' => 'application/json']
            ),
        ]);

        $service = app(UsdDopExchangeRateService::class);
        $occurredAt = CarbonImmutable::parse('2026-07-20T14:30:00Z');
        $first = $service->convertMinor(-1999, $occurredAt);
        $second = $service->convertMinor(-1999, $occurredAt);

        $this->assertSame(-122408, $first['converted_amount']);
        $this->assertSame('DOP', $first['converted_currency']);
        $this->assertSame(61234567, $first['exchange_rate_micros']);
        $this->assertSame('2026-07-20T00:00:00+00:00', $first['exchange_rate_at']->toIso8601String());
        $this->assertSame('fawaz-exchange-api-historical', $first['exchange_rate_source']);
        $this->assertSame($first, $second);
        Http::assertSentCount(1);
    }

    public function test_uses_cloudflare_fallback_when_jsdelivr_is_unavailable(): void
    {
        Http::fake([
            'cdn.jsdelivr.net/*' => Http::response([], 503),
            '2026-07-19.currency-api.pages.dev/v1/currencies/usd.json' => Http::response(
                '{"date":"2026-07-19","usd":{"dop":58.45251497}}',
                200,
                ['Content-Type' => 'application/json']
            ),
        ]);

        $result = app(UsdDopExchangeRateService::class)->convertMinor(
            100,
            CarbonImmutable::parse('2026-07-19T18:00:00Z'),
        );

        $this->assertSame(5845, $result['converted_amount']);
        $this->assertSame(58452515, $result['exchange_rate_micros']);
        Http::assertSentCount(2);
    }
}
