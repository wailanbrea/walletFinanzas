<?php

namespace Tests\Unit;

use App\Services\FinancialEmailClassifier;
use Carbon\CarbonImmutable;
use PHPUnit\Framework\TestCase;

class FinancialEmailClassifierTest extends TestCase
{
    public function test_prefers_total_over_subtotal_when_email_contains_multiple_amounts(): void
    {
        $candidate = (new FinancialEmailClassifier)->classify([
            'subject' => 'Compra aprobada',
            'body' => 'Subtotal RD$1,000.00. Impuestos RD$180.00. Total RD$1,180.00.',
            'sender_email' => 'alertas@banco.example',
            'sender_name' => 'Banco Example',
            'received_at' => CarbonImmutable::parse('2026-07-20T14:30:00Z'),
        ]);

        $this->assertNotNull($candidate);
        $this->assertSame(-118000, $candidate['amount']);
        $this->assertSame('DOP', $candidate['currency']);
    }

    public function test_security_code_email_does_not_create_financial_candidate(): void
    {
        $candidate = (new FinancialEmailClassifier)->classify([
            'subject' => 'Código de seguridad',
            'body' => 'Tu código 123456 autoriza un pago por RD$2,500.00. No lo compartas.',
            'sender_email' => 'seguridad@banco.example',
            'sender_name' => 'Banco Example',
            'received_at' => CarbonImmutable::parse('2026-07-20T14:30:00Z'),
        ]);

        $this->assertNull($candidate);
    }

    public function test_promotional_amazon_email_with_product_prices_is_not_a_financial_candidate(): void
    {
        $candidate = (new FinancialEmailClassifier)->classify([
            'subject' => 'Amazon encontró algo que te gustará',
            'body' => 'Compra ahora productos seleccionados desde US$29.99. Descubre nuestras recomendaciones.',
            'sender_email' => 'store-news@amazon.example',
            'sender_name' => 'Amazon',
            'received_at' => CarbonImmutable::parse('2026-07-20T14:30:00Z'),
        ]);

        $this->assertNull($candidate);
    }

    public function test_bare_dollar_symbol_is_not_assumed_to_be_usd(): void
    {
        $candidate = (new FinancialEmailClassifier)->classify([
            'subject' => 'Pago aprobado',
            'body' => 'Tu pago aprobado fue de $1,200.00.',
            'sender_email' => 'alertas@comercio.example',
            'sender_name' => 'Comercio',
            'received_at' => CarbonImmutable::parse('2026-07-20T14:30:00Z'),
        ]);

        $this->assertNull($candidate);
    }

    public function test_real_amazon_receipt_with_explicit_usd_and_unsubscribe_footer_is_detected(): void
    {
        $candidate = (new FinancialEmailClassifier)->classify([
            'subject' => 'Payment completed for your Amazon order #123-4567890',
            'body' => 'Payment completed. Order total US$29.99. Order #123-4567890. Unsubscribe from recommendations.',
            'sender_email' => 'receipts@amazon.example',
            'sender_name' => 'Amazon',
            'received_at' => CarbonImmutable::parse('2026-07-20T14:30:00Z'),
        ]);

        $this->assertNotNull($candidate);
        $this->assertSame(-2999, $candidate['amount']);
        $this->assertSame('USD', $candidate['currency']);
    }

    public function test_popular_consumption_uses_labeled_amount_merchant_and_transaction_date(): void
    {
        $candidate = $this->classify(
            subject: 'Notificación de Consumo',
            body: 'Monto Moneda Fecha Comercio Estatus RD$1,999.32 Peso dominicano 20/07/2026 03:15 PM AMAZON 1 Aprobada',
            senderEmail: 'notificaciones@popularenlinea.com',
        );

        $this->assertSame(-199932, $candidate['amount']);
        $this->assertSame('DOP', $candidate['currency']);
        $this->assertSame('AMAZON 1', $candidate['merchant']);
        $this->assertSame('2026-07-20T15:15:00-04:00', $candidate['occurred_at']->toIso8601String());
    }

    public function test_popular_historical_html_without_space_after_status_is_supported(): void
    {
        $candidate = $this->classify(
            subject: 'Notificación de Consumo',
            body: 'Monto Moneda Fecha ComercioEstatus US$19.87 Dólar estadounidense 20/07/2026 AMAZON MKTPLACE PMTS AprobadaEn caso de requerir información',
            senderEmail: 'notificaciones@popularenlinea.com',
        );

        $this->assertSame(-1987, $candidate['amount']);
        $this->assertSame('AMAZON MKTPLACE PMTS', $candidate['merchant']);
    }

    public function test_banreservas_consumption_requires_approved_status_and_labeled_fields(): void
    {
        $candidate = $this->classify(
            subject: 'Notificaciones Banreservas',
            body: 'Notificación de Consumo. Monto: RD$2,450.00 Estado: APROBADO Comercio: SUPERMERCADO EJEMPLO Fecha de transacción: 20/07/2026 05:33 PM Número de aprobación: 123456',
            senderEmail: 'notificaciones@banreservas.com',
        );

        $this->assertSame(-245000, $candidate['amount']);
        $this->assertSame('SUPERMERCADO EJEMPLO', $candidate['merchant']);
        $this->assertSame('Alimentación', $candidate['category_suggestion']);
        $this->assertSame('2026-07-20T17:33:00-04:00', $candidate['occurred_at']->toIso8601String());
    }

    public function test_qik_purchase_does_not_confuse_available_balance_with_transaction_amount(): void
    {
        $candidate = $this->classify(
            subject: 'Usaste tu tarjeta de crédito Qik',
            body: 'Se hizo una transacción de RD$826.80 en UBER*EATS. Localidad UBER*EATS Fecha y hora 07-18-2026 02:26 PM (AST) Monto RD$826.80 Balance Disponible RD$110,877.31 Nunca te solicitaremos el código de seguridad mediante correo.',
            senderEmail: 'notificaciones@qik.do',
        );

        $this->assertSame(-82680, $candidate['amount']);
        $this->assertSame('DOP', $candidate['currency']);
        $this->assertSame('UBER*EATS', $candidate['merchant']);
        $this->assertSame('Alimentación', $candidate['category_suggestion']);
        $this->assertSame('2026-07-18T14:26:00-04:00', $candidate['occurred_at']->toIso8601String());
    }

    public function test_qik_reversal_is_income_and_allows_sender_specific_unqualified_dollar(): void
    {
        $candidate = $this->classify(
            subject: 'Se reversó una transacción en tu tarjeta de crédito Qik',
            body: 'Ha sido reversada la transacción de $1.01 en Microsoft*Store. Estatus Reversada Fecha y hora 07-19-2026 01:40 PM (AST) Monto $1.01 Lugar Microsoft*Store',
            senderEmail: 'notificaciones@qik.do',
        );

        $this->assertSame(101, $candidate['amount']);
        $this->assertSame('USD', $candidate['currency']);
        $this->assertSame('Microsoft*Store', $candidate['merchant']);
        $this->assertSame('income', $candidate['direction']);
    }

    public function test_qik_declined_attempt_is_not_a_candidate(): void
    {
        $candidate = $this->classify(
            subject: 'Se hizo una transacción con tu tarjeta de crédito Qik',
            body: 'Se intentó realizar una transacción de RD$1.86. Estatus Declinado por tarjeta bloqueada Monto RD$1.86',
            senderEmail: 'notificaciones@qik.do',
        );

        $this->assertNull($candidate);
    }

    public function test_paypal_receipt_uses_transaction_total_instead_of_converted_card_charge(): void
    {
        $candidate = $this->classify(
            subject: 'Recibo de su pago a Spotify AB',
            body: 'Fecha de la transacción 2 jul 2026 Comercio Spotify AB Subtotal $9.99 USD Total $9.99 USD Pago $9.99 USD Convertido desde: $612.00 DOP Convertido a: $9.99 USD',
            senderEmail: 'service@intl.paypal.com',
        );

        $this->assertSame(-999, $candidate['amount']);
        $this->assertSame('USD', $candidate['currency']);
        $this->assertSame('Spotify AB', $candidate['merchant']);
    }

    public function test_paypal_refund_is_income(): void
    {
        $candidate = $this->classify(
            subject: 'Su reembolso de Example Ireland está en camino',
            body: 'Su reembolso está en camino. Resumen Originalmente pagó $14.99 USD. Total del reembolso $14.99 USD Reembolso de Example Ireland.',
            senderEmail: 'service@intl.paypal.com',
        );

        $this->assertSame(1499, $candidate['amount']);
        $this->assertSame('USD', $candidate['currency']);
        $this->assertSame('Example Ireland', $candidate['merchant']);
        $this->assertSame('income', $candidate['direction']);
    }

    public function test_google_play_receipt_prefers_total_over_item_and_tax(): void
    {
        $candidate = $this->classify(
            subject: 'El recibo de tu pedido de Google Play del 20 jul 2026',
            body: 'Compraste contenido de Example Studio en Google Play. Precio RD$100.00 Impuesto: RD$18.00 Total: RD$118.00 Forma de pago: Mastercard-1234',
            senderEmail: 'googleplay-noreply@google.com',
        );

        $this->assertSame(-11800, $candidate['amount']);
        $this->assertSame('DOP', $candidate['currency']);
        $this->assertSame('Google Play', $candidate['merchant']);
    }

    public function test_bhd_card_alert_parses_approved_table_row(): void
    {
        $candidate = $this->classify(
            subject: 'BHD Notificación de Transacciones',
            body: 'BHD Notificación de Transacciones Visa Mi País Detalle de Transacciones Fecha Moneda Monto Comercio Estado Tipo 20/07/2026 07:40 PM RD $3,200.00 RESTAURANTE EJEMPLO Aprobada Compra',
            senderEmail: 'alertas@bhd.com.do',
        );

        $this->assertSame(-320000, $candidate['amount']);
        $this->assertSame('RESTAURANTE EJEMPLO', $candidate['merchant']);
        $this->assertSame('Alimentación', $candidate['category_suggestion']);
    }

    public function test_known_promotional_security_wallet_and_internal_transfer_messages_are_rejected(): void
    {
        $this->assertNull($this->classify(
            subject: 'Celebra con 15% de devolución con tu Tarjeta BHD',
            body: 'Aprovecha la promoción por consumos desde RD$1,000.00.',
            senderEmail: 'info@bhd.com.do',
        ));
        $this->assertNull($this->classify(
            subject: 'Tu tarjeta crédito Qik ya está en Google Pay',
            body: 'Tu tarjeta fue vinculada correctamente a la Billetera de Google.',
            senderEmail: 'notificaciones@qik.do',
        ));
        $this->assertNull($this->classify(
            subject: 'Recibo de la transacción',
            body: 'Transacción: Pago de Tarjeta de Crédito Propio Monto: RD$5,000.00',
            senderEmail: 'NotificacionesTuBancoApp@banreservas.com',
        ));
        $this->assertNull($this->classify(
            subject: 'Transacciones entre mis productos',
            body: 'Monto: RD$5,000.00 Tipo de transacción: Transacciones entre mis productos',
            senderEmail: 'alertas@bhd.com.do',
        ));
    }

    public function test_extracts_card_last_four_from_masked_and_labeled_formats(): void
    {
        $cases = [
            'Tarjeta ****1234 compra aprobada' => '1234',
            'Tarjeta terminada en 5678 compra aprobada' => '5678',
            'Card ending in 4321 was charged' => '4321',
            'Tarjeta xxxx-8765 cargo realizado' => '8765',
            'Tarjeta 401234******9012 compra aprobada' => '9012',
            'Tarjeta No.: 3456 cargo aplicado' => '3456',
        ];

        foreach ($cases as $body => $expected) {
            $candidate = $this->classify(
                subject: 'Consumo',
                body: $body.' Monto RD$1,000.00',
                senderEmail: 'alertas@banco.example',
            );

            $this->assertNotNull($candidate, "No se clasifico: $body");
            $this->assertSame($expected, $candidate['card_last_four'], "Fallo con: $body");
        }
    }

    public function test_amount_is_never_mistaken_for_a_card_number(): void
    {
        // Qik pone el importe justo despues de "tarjeta de credito Qik": un patron
        // laxo devolveria 8268 (de RD$826.80) como numero de tarjeta.
        $candidate = $this->classify(
            subject: 'Usaste tu tarjeta de crédito Qik',
            body: 'Se hizo una transacción de RD$826.80 en UBER*EATS. Localidad UBER*EATS Fecha y hora 07-18-2026 02:26 PM (AST) Monto RD$826.80 Balance Disponible RD$110,877.31',
            senderEmail: 'notificaciones@qik.do',
        );

        $this->assertNull($candidate['card_last_four']);
    }

    /** @return array<string, mixed>|null */
    private function classify(string $subject, string $body, string $senderEmail): ?array
    {
        return (new FinancialEmailClassifier)->classify([
            'subject' => $subject,
            'body' => $body,
            'sender_email' => $senderEmail,
            'sender_name' => null,
            'received_at' => CarbonImmutable::parse('2026-07-21T00:00:00Z'),
        ]);
    }
}
