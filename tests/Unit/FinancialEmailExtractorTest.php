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
}
