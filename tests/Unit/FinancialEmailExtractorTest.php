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

    public function test_a_payroll_notice_is_income_even_when_it_says_pago(): void
    {
        // "Pago de nomina" es el sueldo, no un gasto. La palabra "pago" ganaba y el
        // sueldo se restaba del balance ademas de inflar los gastos del mes.
        $cases = [
            'Pago de nómina acreditado por RD$22,173.10',
            'Se ha realizado el pago de su nómina RD$22,173.10',
            // Estos dos no casaban con ninguna de las dos listas y se descartaban.
            'Acreditación de salario completada RD$22,173.10',
            'Su sueldo quincenal fue pagado RD$22,173.10',
        ];

        foreach ($cases as $body) {
            $candidate = $this->extractor->extract('Notificaciones Banreservas', $body, null);

            $this->assertNotNull($candidate, "Se descartó: $body");
            $this->assertSame('income', $candidate['direction'], "Falló con: $body");
            $this->assertSame('Salario', $candidate['category_suggestion'], "Falló con: $body");
        }
    }

    public function test_paying_a_card_is_a_transfer_not_an_expense(): void
    {
        $candidate = $this->extractor->extract('Aviso', 'Pago de tu tarjeta de crédito realizado por RD$5,000.00', null);

        $this->assertSame('transfer', $candidate['direction']);
        $this->assertSame(FinancialEmailExtractor::CARD_PAYMENT, $candidate['event_type']);
        $this->assertNotSame('expense', $candidate['direction']);
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

    public function test_it_requires_a_definitive_execution_expression(): void
    {
        $this->assertNull($this->extractor->extract('Pago PayPal', 'Próximo pago USD 25.00 en 10 días', null));
        $this->assertNull($this->extractor->extract('Aviso de compra', 'Compra por RD$500.00', null));
        $this->assertNull($this->extractor->extract('Renovación', 'Tu pago de USD 12.00 será procesado mañana', null));

        $completed = $this->extractor->extract('Pago PayPal realizado con éxito', 'Payment completed USD 25.00', null);
        $this->assertSame(FinancialEmailExtractor::RECEIPT_CONFIRMED, $completed['event_type']);
    }

    public function test_it_classifies_the_observed_bank_event_taxonomy(): void
    {
        $cases = [
            ['Compra aprobada', 'Comercio: Amazon | RD$1,000.00', FinancialEmailExtractor::CARD_PURCHASE_APPROVED, 'expense'],
            ['Transferencia enviada satisfactoriamente', 'Monto RD$2,000.00', FinancialEmailExtractor::TRANSFER_OUT, 'transfer'],
            ['Transferencia recibida', 'Monto acreditado RD$2,000.00', FinancialEmailExtractor::TRANSFER_IN, 'income'],
            ['Transferencia entre mis productos realizada', 'Monto RD$2,000.00', FinancialEmailExtractor::INTERNAL_TRANSFER, 'transfer'],
            ['Pago de tu tarjeta realizado', 'Monto RD$5,000.00', FinancialEmailExtractor::CARD_PAYMENT, 'transfer'],
            ['Reverso realizado a cuenta por sobregiro', 'Monto RD$350.00', FinancialEmailExtractor::REFUND_REVERSAL, 'income'],
            ['Comisión descontada satisfactoriamente', 'Monto RD$175.00', FinancialEmailExtractor::BANK_FEE_TAX, 'expense'],
            ['Retiro realizado en cajero', 'Monto RD$3,000.00', FinancialEmailExtractor::CASH_WITHDRAWAL, 'transfer'],
            ['Depósito acreditado', 'Monto RD$9,000.00', FinancialEmailExtractor::DEPOSIT, 'income'],
            ['Recibo de su pago', 'Pago completado USD 15.00', FinancialEmailExtractor::RECEIPT_CONFIRMED, 'expense'],
        ];

        foreach ($cases as [$subject, $snippet, $eventType, $direction]) {
            $candidate = $this->extractor->extract($subject, $snippet, null);
            $this->assertNotNull($candidate, "Se descartó: $subject");
            $this->assertSame($eventType, $candidate['event_type'], "Tipo incorrecto: $subject");
            $this->assertSame($direction, $candidate['direction'], "Dirección incorrecta: $subject");
        }

        $outgoing = $this->extractor->extract(
            'Transferencia enviada satisfactoriamente',
            'El beneficiario la ha recibido. Monto DOP 2,000.00',
            null,
        );
        $this->assertSame(FinancialEmailExtractor::TRANSFER_OUT, $outgoing['event_type']);
        $this->assertSame('transfer', $outgoing['direction']);
    }

    public function test_it_rejects_non_transactional_bank_and_marketing_notices(): void
    {
        $cases = [
            ['Aumentamos el límite de tu tarjeta', 'Nuevo límite RD$150,000.00'],
            ['Préstamo preaprobado', 'Tienes hasta RD$500,000.00 disponibles'],
            ['Oferta cashback', 'Compra mínima RD$1,000.00 y devolución de hasta RD$500.00'],
            ['Newsletter semanal', 'Equipos desde USD 99.00'],
            ['Estado de cuenta disponible', 'Balance RD$25,000.00'],
            ['Alerta de saldo', 'Tu saldo disponible es RD$5,000.00'],
            ['Código de seguridad OTP', 'No compartas el código 123456. Monto RD$1.00'],
            ['Tarjeta activada', 'Tu límite es RD$50,000.00'],
            ['Compra declinada', 'Intento por RD$700.00'],
            ['Promoción aprobada de cashback', 'Compra desde RD$1,000.00 y recibe devolución de hasta RD$500.00'],
        ];

        foreach ($cases as [$subject, $snippet]) {
            $this->assertNull($this->extractor->extract($subject, $snippet, null), "Falso positivo: $subject");
        }
    }

    public function test_unknown_merchants_are_always_low_confidence(): void
    {
        $candidate = $this->extractor->extract(
            'Compra aprobada',
            'Comercio: NEGOCIO NUEVO | Monto RD$700.00',
            null,
        );

        $this->assertSame('Otros', $candidate['category_suggestion']);
        $this->assertSame(40, $candidate['confidence']);
    }

    public function test_it_extracts_card_last_four_from_masked_and_labeled_formats(): void
    {
        $cases = [
            'Tarjeta ****1234 compra aprobada RD$1,000.00' => '1234',
            'Tarjeta terminada en 5678 compra aprobada RD$1,000.00' => '5678',
            'Card ending in 4321 was charged USD 10.00' => '4321',
            'Tarjeta xxxx-8765 cargo aprobado por RD$500.00' => '8765',
            'Tarjeta 401234******9012 compra aprobada de RD$250.00' => '9012',
            'Tarjeta No.: 3456 pago realizado de RD$100.00' => '3456',
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

    public function test_a_qualified_symbol_decides_the_currency(): void
    {
        $cases = [
            'Compra aprobada por RD$1,500.00' => 'DOP',
            'Compra aprobada por USD 355.00' => 'USD',
            'Compra aprobada por DOP 1,500.00' => 'DOP',
            'Compra aprobada por EUR 40.00' => 'EUR',
            'Compra aprobada por €40.00' => 'EUR',
        ];

        foreach ($cases as $body => $expected) {
            $candidate = $this->extractor->extract('Consumo', $body, null);

            $this->assertNotNull($candidate, "No se extrajo: $body");
            $this->assertSame($expected, $candidate['currency'], "Fallo con: $body");
        }
    }

    public function test_a_bare_dollar_sign_uses_the_qualifier_found_in_the_text(): void
    {
        // PayPal escribe "$355.00 USD": el cualificador está al lado, no en el símbolo.
        $usd = $this->extractor->extract('Pago realizado', 'Se cobró $355.00 USD a tu cuenta', null);
        $this->assertSame('USD', $usd['currency']);

        // Un banco dominicano habla de pesos aunque escriba solo "$".
        $dop = $this->extractor->extract('Consumo aprobado', 'Compra por $1,500.00 pesos dominicanos', null);
        $this->assertSame('DOP', $dop['currency']);
    }

    public function test_an_unqualified_dollar_sign_is_never_guessed(): void
    {
        // Tomar RD$1,500 por dólares lo convertiría en unos RD$90,000. Sin una pista
        // clara se descarta en vez de desplazar el importe por un factor de sesenta.
        $this->assertNull($this->extractor->extract('Compra aprobada', 'Cargo por $1,500.00', null));
    }

    public function test_it_reads_a_merchant_it_has_never_seen_from_the_labelled_field(): void
    {
        // El caso que dejaba dos de cada tres avisos sin comercio: un negocio local no
        // esta ni puede estar en la lista de marcas conocidas.
        $body = "Compra aprobada\nComercio | FERRETERIA OCHOA SRL | Monto | RD$3,450.00";

        $result = $this->extractor->extract('Notificación de Consumo', $body, null);

        $this->assertSame('FERRETERIA OCHOA SRL', $result['merchant']);
        $this->assertSame(345000, $result['amount']);
    }

    public function test_the_label_accepts_a_colon_as_well_as_a_table_cell(): void
    {
        $result = $this->extractor->extract(
            'Consumo aprobado',
            'Establecimiento: PANADERIA DEL SUR por RD$250.00',
            null
        );

        $this->assertSame('PANADERIA DEL SUR', $result['merchant']);
    }

    public function test_a_known_brand_still_wins_over_the_raw_labelled_text(): void
    {
        // El nombre canonico es mas limpio y es el que alimenta la categorizacion.
        $result = $this->extractor->extract(
            'Compra aprobada',
            'Comercio | PAYPAL *EBAY COMMERCE | Monto | RD$1,000.00',
            null
        );

        $this->assertSame('PayPal', $result['merchant']);
    }

    public function test_an_empty_cell_does_not_turn_the_next_label_into_a_merchant(): void
    {
        $result = $this->extractor->extract(
            'Compra aprobada',
            'Comercio | | Monto | RD$500.00 | Tarjeta | ****4266',
            null
        );

        $this->assertNull($result['merchant']);
        // Pero la tarjeta sí se lee de la celda.
        $this->assertSame('4266', $result['card_last_four']);
    }

    public function test_the_word_comercio_inside_a_sentence_is_not_a_label(): void
    {
        // Aceptar un salto de linea como separador convertia esta frase en el nombre del
        // comercio: devolvia "Banco Popular Dominicano le informa".
        $result = $this->extractor->extract(
            'Consumo',
            "Compra aprobada por RD\$500.00 en el comercio\nBanco Popular Dominicano le informa",
            null
        );

        $this->assertNull($result['merchant']);
    }

    public function test_a_label_on_its_own_line_is_still_read(): void
    {
        // El mismo formato sin puntuacion, pero abriendo linea: aqui si es un campo.
        $result = $this->extractor->extract(
            'Consumo',
            "Compra aprobada\nComercio\nFERRETERIA OCHOA\nMonto RD\$500.00",
            null
        );

        $this->assertSame('FERRETERIA OCHOA', $result['merchant']);
    }

    public function test_a_real_merchant_label_wins_over_localidad(): void
    {
        // En Qik "Localidad" trae el comercio, pero en otros bancos es la ciudad. Cuando
        // el aviso trae los dos campos, el comercio manda y la ciudad no lo pisa.
        $result = $this->extractor->extract(
            'Consumo',
            'Compra aprobada RD$500.00 | Localidad | SANTO DOMINGO | Comercio | FERRETERIA OCHOA',
            null
        );

        $this->assertSame('FERRETERIA OCHOA', $result['merchant']);
    }

    public function test_a_number_is_never_taken_as_a_merchant_name(): void
    {
        $result = $this->extractor->extract(
            'Compra aprobada',
            'Comercio | 00123456 | Monto | RD$700.00',
            null
        );

        $this->assertNull($result['merchant']);
    }

    public function test_it_reads_the_card_from_a_table_cell_without_a_mask(): void
    {
        $result = $this->extractor->extract(
            'Consumo aprobado',
            'Tarjeta | 8324 | Monto | RD$1,214.35',
            null
        );

        $this->assertSame('8324', $result['card_last_four']);
    }

    public function test_it_reads_banreservas_fields_with_colon_pipe_and_two_bullets(): void
    {
        $result = $this->extractor->extract(
            'Notificaciones Banreservas',
            'Notificación de Consumo | Su tarjeta MCG-MULTIMONEDA ••4116 presenta un consumo. | Monto: | DOP 380.01 | Comercio: | HELADOS BON SM NACIONA SANTO DOMINGO DOM | Estado: | APROBADO',
            null
        );

        $this->assertSame('HELADOS BON SM NACIONA SANTO DOMINGO DOM', $result['merchant']);
        $this->assertSame('4116', $result['card_last_four']);
        $this->assertSame('Restaurantes', $result['category_suggestion']);
    }

    public function test_it_reads_a_merchant_on_the_line_after_the_label(): void
    {
        $result = $this->extractor->extract(
            'Recibo de su pago a Epic Games Commerce...',
            "Ha pagado $22.99 USD\nComercio\nEpic Games Commerce",
            null
        );

        $this->assertSame('Epic Games', $result['merchant']);
        $this->assertSame('Entretenimiento', $result['category_suggestion']);
    }

    public function test_aliexpress_receipts_use_a_specific_purchase_category(): void
    {
        $result = $this->extractor->extract(
            'Recibo de su pago a AliExpress',
            'Pago completado USD 15.00',
            null
        );

        $this->assertSame('AliExpress', $result['merchant']);
        $this->assertSame('Compras', $result['category_suggestion']);
    }

    public function test_it_reads_qik_locality_and_partially_masked_card(): void
    {
        $result = $this->extractor->extract(
            'Usaste tu tarjeta de crédito Qik',
            "Tarjeta  53*************8324\nSe hizo una transacción de RD$ 1,214.35 en OPENAI *CHATGPT SUBSCR con tu tarjeta crédito Qik\nLocalidad       OPENAI *CHATGPT SUB",
            null
        );

        $this->assertSame('OPENAI *CHATGPT SUB', $result['merchant']);
        $this->assertSame('8324', $result['card_last_four']);
        $this->assertSame('Servicios', $result['category_suggestion']);
    }
}
