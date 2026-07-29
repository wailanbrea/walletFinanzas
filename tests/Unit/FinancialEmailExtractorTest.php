<?php

namespace Tests\Unit;

use App\Services\FinancialEmailExtractor;
use PHPUnit\Framework\TestCase;

class FinancialEmailExtractorTest extends TestCase
{
    private FinancialEmailExtractor $extractor;

    protected function setUp(): void
    {
        parent::setUp();
        $this->extractor = new FinancialEmailExtractor;
    }

    public function test_it_rejects_preapproved_credit_promotions(): void
    {
        $this->assertNull($this->extractor->extract(
            'Tienes una tarjeta de crédito preaprobada',
            'Disfruta un límite de RD$142,000.00',
            null,
        ));
    }

    public function test_it_identifies_paypal_expenses_and_suggests_purchases(): void
    {
        $candidate = $this->extractor->extract(
            'Pago automático a través de PayPal realizado con éxito',
            'Payment completed EUR 17.86',
            null,
        );

        $this->assertSame('expense', $candidate['direction']);
        $this->assertSame('PayPal', $candidate['merchant']);
        $this->assertSame('Compras', $candidate['category_suggestion']);
        $this->assertSame(1786, $candidate['amount']);
    }

    public function test_it_identifies_salary_income(): void
    {
        $candidate = $this->extractor->extract('Depósito de nómina DOP 25,000.00', 'Ingreso recibido', null);

        $this->assertSame('income', $candidate['direction']);
        $this->assertSame('Salario', $candidate['category_suggestion']);
        $this->assertSame(2500000, $candidate['amount']);
    }

    public function test_it_rejects_payment_reminders_but_not_transactional_messages_with_promo_footers(): void
    {
        $this->assertNull($this->extractor->extract(
            'Recordatorio de pago mínimo',
            'Tu pago vence mañana USD 50.00',
            null,
        ));

        $candidate = $this->extractor->extract(
            'Compra aprobada USD 20.00',
            'Si no la reconoces, solicita el bloqueo. Conoce nuestras promociones.',
            null,
        );
        $this->assertSame('expense', $candidate['direction']);

        $this->assertNull($this->extractor->extract('Compra declinada USD 20.00', null, null));
        $this->assertNull($this->extractor->extract('Payment rejected USD 10.00', null, null));
        $this->assertNull($this->extractor->extract('150% of budget reached', 'Payment USD 3.60', null));
    }

    public function test_it_extracts_card_last_four_from_masked_and_labeled_formats(): void
    {
        $cases = [
            'Tarjeta ****1234 compra aprobada RD$1,000.00' => '1234',
            'Tarjeta terminada en 5678 compra aprobada RD$1,000.00' => '5678',
            'Card ending in 4321 was charged USD 10.00' => '4321',
            'Tarjeta xxxx-8765 cargo por RD$500.00' => '8765',
            'Tarjeta 401234******9012 compra de RD$250.00' => '9012',
            'Tarjeta No.: 3456 pago de RD$100.00' => '3456',
        ];

        foreach ($cases as $subject => $expected) {
            $candidate = $this->extractor->extract($subject, null, null);

            $this->assertNotNull($candidate, "No se extrajo: $subject");
            $this->assertSame($expected, $candidate['card_last_four'], "Fallo con: $subject");
        }
    }

    public function test_it_never_mistakes_the_amount_for_a_card_number(): void
    {
        // Qik pone el importe justo despues de "tarjeta de credito Qik": un patron
        // laxo devolveria 8268 (de RD$826.80) como numero de tarjeta.
        $candidate = $this->extractor->extract(
            'Usaste tu tarjeta de crédito Qik',
            'Se hizo una transacción de RD$826.80 en UBER*EATS',
            null,
        );

        $this->assertNotNull($candidate);
        $this->assertNull($candidate['card_last_four']);
    }
}
