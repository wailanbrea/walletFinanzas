<?php

namespace Tests\Feature;

use App\Models\EmailCandidate;
use App\Models\EmailConnection;
use App\Models\ProviderMessage;
use App\Models\User;
use App\Services\DuplicateEmailCandidateDetector;
use App\Services\FinancialEmailExtractor;
use App\Services\UsdDopExchangeRateService;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

/**
 * El mismo cargo llega por dos buzones: PayPal lo avisa en USD y el banco emisor en
 * DOP. Antes se contaba dos veces porque el dedupe por mensaje incluye el proveedor.
 */
class DuplicateEmailCandidateTest extends TestCase
{
    use RefreshDatabase;

    public function test_the_dop_candidate_survives_and_the_usd_one_is_marked_duplicate(): void
    {
        $user = User::factory()->create();
        // Caso real: PayPal reporta USD 355 y Qik cobra RD$21,000 por lo mismo.
        $paypal = $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z', converted: -2_100_000);
        $qik = $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z');

        $marked = (new DuplicateEmailCandidateDetector)->reconcile($user);

        $this->assertSame(1, $marked);
        // Gana el DOP: es lo que el banco cobró de verdad, no una tasa estimada.
        $this->assertSame('pending', $qik->fresh()->status);
        $this->assertSame('duplicate', $paypal->fresh()->status);
        $this->assertSame($qik->id, $paypal->fresh()->duplicate_of_id);
        $mailboxId = $paypal->message->connection->fresh()->email_mailbox_id;
        $this->assertDatabaseHas('email_message_decisions', [
            'email_mailbox_id' => $mailboxId,
            'provider_message_id' => $paypal->message->provider_message_id,
            'status' => 'duplicate',
        ]);
    }

    public function test_a_usd_charge_without_conversion_is_never_matched(): void
    {
        $user = User::factory()->create();
        $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z', converted: null);
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z');

        // Sin conversión no hay forma honesta de saber si es el mismo cargo, y
        // adivinarlo ocultaría un gasto real.
        $this->assertSame(0, (new DuplicateEmailCandidateDetector)->reconcile($user));
    }

    public function test_two_similar_charges_from_the_same_mailbox_are_not_duplicates(): void
    {
        $user = User::factory()->create();
        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T18:30:00Z');
        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T19:00:00Z');

        // Dos avisos del mismo buzón son dos cargos distintos; para eso ya está el
        // dedupe por mensaje.
        $this->assertSame(0, (new DuplicateEmailCandidateDetector)->reconcile($user));
    }

    public function test_charges_far_apart_or_of_different_size_are_not_duplicates(): void
    {
        $user = User::factory()->create();
        $detector = new DuplicateEmailCandidateDetector;

        // Fuera de la ventana de 72 horas.
        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-10T18:30:00Z');
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:30:00Z');
        $this->assertSame(0, $detector->reconcile($user));

        EmailCandidate::query()->delete();

        // Importes que se salen de la tolerancia del 3%.
        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T18:30:00Z');
        $this->candidate($user, 'microsoft', -1_500_000, 'DOP', '2026-07-20T18:35:00Z');
        $this->assertSame(0, $detector->reconcile($user));
    }

    public function test_an_income_never_matches_an_expense(): void
    {
        $user = User::factory()->create();
        $this->candidate($user, 'gmail', 2_100_000, 'DOP', '2026-07-20T18:30:00Z', direction: 'income');
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z');

        $this->assertSame(0, (new DuplicateEmailCandidateDetector)->reconcile($user));
    }

    public function test_legacy_untyped_candidates_keep_the_established_duplicate_behavior(): void
    {
        $user = User::factory()->create();
        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T18:30:00Z', eventType: null);
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z', eventType: null);

        $this->assertSame(1, (new DuplicateEmailCandidateDetector)->reconcile($user));
    }

    public function test_different_cards_events_or_known_merchants_are_not_duplicates(): void
    {
        $user = User::factory()->create();
        $detector = new DuplicateEmailCandidateDetector;

        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T18:30:00Z', cardLastFour: '2910');
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z', cardLastFour: '8980');
        $this->assertSame(0, $detector->reconcile($user));

        EmailCandidate::query()->delete();

        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T18:30:00Z');
        $this->candidate(
            $user,
            'microsoft',
            -2_100_000,
            'DOP',
            '2026-07-20T18:35:00Z',
            eventType: FinancialEmailExtractor::BANK_FEE_TAX,
        );
        $this->assertSame(0, $detector->reconcile($user));

        EmailCandidate::query()->delete();

        $this->candidate($user, 'gmail', -2_100_000, 'DOP', '2026-07-20T18:30:00Z', merchant: 'Amazon');
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z', merchant: 'Jumbo');
        $this->assertSame(0, $detector->reconcile($user));
    }

    public function test_the_api_exposes_the_conversion_instead_of_null(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $usd = $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z', converted: -2_100_000);
        $usd->update([
            'exchange_rate_micros' => 60_500_000,
            'exchange_rate_at' => CarbonImmutable::parse('2026-07-20T00:00:00Z'),
            'exchange_rate_source' => UsdDopExchangeRateService::SOURCE,
        ]);

        // Antes estos campos venían cableados a null y un cargo USD no se podía
        // clasificar en una cuenta DOP.
        $this->getJson('/api/v1/email-candidates')
            ->assertOk()
            ->assertJsonPath('data.0.converted_amount', -2_100_000)
            ->assertJsonPath('data.0.converted_currency', 'DOP')
            ->assertJsonPath('data.0.exchange_rate_micros', 60_500_000)
            ->assertJsonPath('data.0.conversion_kind', 'historical_estimate')
            ->assertJsonPath('data.0.conversion_status', 'available');
    }

    public function test_gateway_receipt_can_match_the_bank_purchase_for_the_same_charge(): void
    {
        $user = User::factory()->create();
        $gateway = $this->candidate(
            $user,
            'gmail',
            -35_500,
            'USD',
            '2026-07-20T18:30:00Z',
            converted: -2_100_000,
            eventType: FinancialEmailExtractor::RECEIPT_CONFIRMED,
            merchant: 'PayPal',
        );
        $bank = $this->candidate(
            $user,
            'microsoft',
            -2_100_000,
            'DOP',
            '2026-07-20T18:35:00Z',
            eventType: FinancialEmailExtractor::CARD_PURCHASE_APPROVED,
            merchant: 'Amazon',
        );

        $this->assertSame(1, (new DuplicateEmailCandidateDetector)->reconcile($user));
        $this->assertSame($bank->id, $gateway->fresh()->duplicate_of_id);
    }

    public function test_a_dop_candidate_reports_that_no_conversion_was_needed(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z');

        $this->getJson('/api/v1/email-candidates')
            ->assertOk()
            ->assertJsonPath('data.0.conversion_status', 'not_required');
    }

    public function test_an_unconvertible_usd_candidate_says_so(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z', converted: null);

        // El cliente distingue "no se pudo" de "no hacía falta": sin esto el cargo
        // quedaba inclasificable sin explicación.
        $this->getJson('/api/v1/email-candidates')
            ->assertOk()
            ->assertJsonPath('data.0.conversion_status', 'unavailable')
            ->assertJsonPath('data.0.conversion_kind', null);
    }

    public function test_marking_a_duplicate_by_hand_points_at_the_original(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $usd = $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z');
        $dop = $this->candidate($user, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z');

        $this->patchJson("/api/v1/email-candidates/{$usd->id}", [
            'action' => 'duplicate',
            'duplicate_of_id' => $dop->id,
            'learn' => true,
        ])->assertOk()->assertJsonPath('data.status', 'duplicate');

        $this->assertSame($dop->id, $usd->fresh()->duplicate_of_id);
        // Un duplicado no enseña nada al clasificador: el remitente sí manda avisos
        // reales, solo que ese cargo ya llegó por otro buzón.
        $this->assertDatabaseCount('email_categorization_rules', 0);
    }

    public function test_a_candidate_cannot_be_a_duplicate_of_itself(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $candidate = $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z');

        $this->patchJson("/api/v1/email-candidates/{$candidate->id}", [
            'action' => 'duplicate',
            'duplicate_of_id' => $candidate->id,
        ])->assertStatus(422);
    }

    public function test_the_original_must_belong_to_the_same_user(): void
    {
        $user = User::factory()->create();
        $stranger = User::factory()->create();
        $theirs = $this->candidate($stranger, 'microsoft', -2_100_000, 'DOP', '2026-07-20T18:35:00Z');
        Sanctum::actingAs($user);
        $mine = $this->candidate($user, 'gmail', -35_500, 'USD', '2026-07-20T18:30:00Z');

        $this->patchJson("/api/v1/email-candidates/{$mine->id}", [
            'action' => 'duplicate',
            'duplicate_of_id' => $theirs->id,
        ])->assertNotFound();
    }

    private function candidate(
        User $user,
        string $provider,
        int $amount,
        string $currency,
        string $occurredAt,
        ?int $converted = null,
        string $direction = 'expense',
        ?string $eventType = FinancialEmailExtractor::CARD_PURCHASE_APPROVED,
        ?string $cardLastFour = '1234',
        string $merchant = 'Comercio',
    ): EmailCandidate {
        // Una sola conexión por buzón: la tabla tiene unique(user_id, provider).
        $connection = EmailConnection::query()->firstOrCreate(
            ['user_id' => $user->id, 'provider' => $provider],
            [
                'email' => "{$provider}@example.com",
                'access_token' => 'token',
                'connected_at' => now(),
            ]
        );
        $message = ProviderMessage::query()->create([
            'user_id' => $user->id,
            'email_connection_id' => $connection->id,
            'provider' => $provider,
            'provider_message_id' => uniqid($provider, true),
            'subject' => 'Cargo',
            'occurred_at' => $occurredAt,
        ]);

        return EmailCandidate::query()->create([
            'user_id' => $user->id,
            'provider_message_id' => $message->id,
            'provider' => $provider,
            'merchant' => $merchant,
            'card_last_four' => $cardLastFour,
            'event_type' => $eventType,
            'amount' => $amount,
            'currency' => $currency,
            'direction' => $direction,
            'occurred_at' => CarbonImmutable::parse($occurredAt),
            'confidence' => 90,
            'status' => 'pending',
            'converted_amount' => $converted,
            'converted_currency' => $converted === null ? null : 'DOP',
        ]);
    }
}
