<?php

namespace Tests\Feature;

use App\Models\EmailCandidate;
use App\Models\EmailConnection;
use App\Models\ProviderMessage;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class BackfillEmailCandidateConversionsTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Cache::flush();
    }

    public function test_it_converts_candidates_saved_before_conversion_existed(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);
        $user = User::factory()->create();
        $usd = $this->candidate($user, 'gmail', -35_500, 'USD');
        $dop = $this->candidate($user, 'microsoft', -2_130_000, 'DOP');

        $this->artisan('email:backfill-conversions')
            ->expectsOutputToContain('Candidatos sin conversión: 1')
            ->assertSuccessful();

        $usd->refresh();
        $this->assertSame(-2_130_000, $usd->converted_amount);
        $this->assertSame('DOP', $usd->converted_currency);
        $this->assertSame(60_000_000, $usd->exchange_rate_micros);

        // La conversión recién puesta revela que ambos eran el mismo cargo.
        $this->assertSame('duplicate', $usd->fresh()->status);
        $this->assertSame($dop->id, $usd->fresh()->duplicate_of_id);
    }

    public function test_running_it_twice_changes_nothing_the_second_time(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);
        $user = User::factory()->create();
        $this->candidate($user, 'gmail', -35_500, 'USD');

        $this->artisan('email:backfill-conversions')->assertSuccessful();
        // Idempotente: solo toca los que les falta la conversión.
        $this->artisan('email:backfill-conversions')
            ->expectsOutput('No hay candidatos pendientes de conversión.')
            ->assertSuccessful();
    }

    public function test_a_dry_run_writes_nothing(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);
        $user = User::factory()->create();
        $usd = $this->candidate($user, 'gmail', -35_500, 'USD');

        $this->artisan('email:backfill-conversions', ['--dry-run' => true])->assertSuccessful();

        $this->assertNull($usd->fresh()->converted_amount);
    }

    public function test_without_a_usable_rate_it_leaves_the_candidate_for_a_later_retry(): void
    {
        Http::fake(['*' => Http::response(status: 503)]);
        $user = User::factory()->create();
        $usd = $this->candidate($user, 'gmail', -35_500, 'USD');

        $this->artisan('email:backfill-conversions')
            ->expectsOutputToContain('Sin tasa disponible: 1')
            ->assertSuccessful();

        // No se inventa una cifra: se queda igual y se puede reintentar.
        $this->assertNull($usd->fresh()->converted_amount);
    }

    public function test_it_can_be_limited_to_one_user(): void
    {
        Http::fake(['*' => Http::response(['usd' => ['dop' => 60.0]])]);
        $mine = User::factory()->create();
        $theirs = User::factory()->create();
        $ours = $this->candidate($mine, 'gmail', -35_500, 'USD');
        $others = $this->candidate($theirs, 'gmail', -35_500, 'USD');

        $this->artisan('email:backfill-conversions', ['--user' => $mine->id])->assertSuccessful();

        $this->assertNotNull($ours->fresh()->converted_amount);
        $this->assertNull($others->fresh()->converted_amount);
    }

    private function candidate(User $user, string $provider, int $amount, string $currency): EmailCandidate
    {
        $connection = EmailConnection::query()->firstOrCreate(
            ['user_id' => $user->id, 'provider' => $provider],
            ['email' => "{$provider}@example.com", 'access_token' => 'token', 'connected_at' => now()]
        );
        $message = ProviderMessage::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => $provider,
            'provider_message_id' => uniqid($provider, true),
            'subject' => 'Cargo',
            'occurred_at' => '2026-07-20T18:30:00Z',
        ]);

        return EmailCandidate::query()->create([
            'user_id' => $user->id,
            'provider_message_id' => $message->id,
            'provider' => $provider,
            'merchant' => 'Comercio',
            'amount' => $amount,
            'currency' => $currency,
            'direction' => 'expense',
            'occurred_at' => CarbonImmutable::parse('2026-07-20T18:30:00Z'),
            'confidence' => 90,
            'status' => 'pending',
        ]);
    }
}
