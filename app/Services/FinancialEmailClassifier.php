<?php

namespace App\Services;

use Carbon\CarbonImmutable;
use Illuminate\Support\Str;

class FinancialEmailClassifier
{
    /**
     * @param  array{subject:?string,body:string,sender_email:?string,sender_name:?string,received_at:CarbonImmutable}  $message
     * @return array<string, mixed>|null
     */
    public function classify(array $message): ?array
    {
        $subject = $this->plainText($message['subject'] ?? '');
        $body = $this->plainText($message['body']);
        $plainText = trim($subject.' '.$body);
        $normalized = Str::lower($plainText);

        $sender = Str::lower(trim((string) ($message['sender_email'] ?? '')));
        if ($this->isKnownSender($sender)) {
            $candidate = $this->classifyKnownFormat($sender, $subject, $body, $message);

            // Los ultimos cuatro digitos se extraen aqui y no dentro de cada formato:
            // el patron es identico en todos los bancos y asi no se repite ocho veces.
            return $candidate === null
                ? null
                : [...$candidate, 'card_last_four' => $this->extractCardLastFour($plainText)];
        }

        $securityWords = [
            'código de seguridad', 'codigo de seguridad', 'código de verificación',
            'codigo de verificacion', 'no lo compartas', 'one-time password', 'verification code',
        ];
        if (collect($securityWords)->contains(fn (string $word): bool => str_contains($normalized, $word))) {
            return null;
        }

        $financialWords = [
            'compra aprobada', 'compra realizada', 'consumo realizado', 'consumo por',
            'cargo realizado', 'cargo aplicado', 'cobro realizado', 'pago realizado',
            'pago aprobado', 'pagaste', 'fue debitado', 'debitado de tu cuenta',
            'retiro realizado', 'transferencia enviada', 'transferencia recibida',
            'depósito recibido', 'deposito recibido', 'recibiste un depósito', 'recibiste un deposito',
            'reembolso procesado', 'devolución procesada',
            'purchase approved', 'purchase completed', 'you paid', 'was charged',
            'payment received', 'payment completed', 'withdrawal completed',
            'transfer sent', 'transfer received', 'deposit received', 'refund issued',
        ];
        $matchedWord = collect($financialWords)->first(fn (string $word): bool => str_contains($normalized, $word));
        if (! $matchedWord) {
            return null;
        }

        $amount = $this->extractAmount($plainText);
        if (! $amount) {
            return null;
        }

        $incomeWords = ['depósito', 'deposito', 'recibiste', 'recibido', 'reembolso', 'devolución', 'deposit', 'received', 'refund'];
        $incomeWord = collect($incomeWords)->first(fn (string $word): bool => str_contains($normalized, $word));
        $direction = $incomeWord ? 'income' : 'expense';
        $signedAmount = $direction === 'income' ? $amount['minor'] : -$amount['minor'];

        $category = $this->suggestCategory($normalized);
        $reasons = [
            'financial_keyword:'.$matchedWord,
            'currency_detected:'.$amount['currency'],
            'direction_keyword:'.($incomeWord ?: 'expense_default'),
        ];
        if ($category) {
            $reasons[] = 'category_rule:'.$category;
        }

        return [
            'merchant' => $message['sender_name'] ?: $message['sender_email'],
            'amount' => $signedAmount,
            'currency' => $amount['currency'],
            'direction' => $direction,
            'category_suggestion' => $category,
            'occurred_at' => $message['received_at'],
            'confidence' => min(95, 75 + ($category ? 10 : 0) + ($message['sender_email'] ? 5 : 0)),
            'reasons' => $reasons,
            'status' => 'pending',
            'card_last_four' => $this->extractCardLastFour($plainText),
        ];
    }

    /**
     * Ultimos cuatro digitos de la tarjeta que origino el movimiento, para que la app
     * pueda preseleccionar la cuenta correcta al aceptar el correo.
     *
     * Solo se acepta cuando hay mascara ("****1234") o etiqueta explicita ("terminada
     * en 1234"). Un patron mas suelto como "tarjeta ... 1234" tomaria el monto por
     * numero de tarjeta en correos como los de Qik, donde el importe va justo despues.
     */
    private function extractCardLastFour(string $text): ?string
    {
        $patterns = [
            // "terminada en 1234", "termina en 1234", "ending in 1234"
            '/\b(?:terminad[ao]s?|termina|finalizada|ending)\s+(?:en|in)\s*[:\-]?\s*(?<digits>\d{4})\b/iu',
            // Mascara antes de los digitos: "****1234", "xxxx-1234", "•••• 1234"
            '/(?:[*x•·]\s*){3,}[\s\-]*(?<digits>\d{4})\b/iu',
            // BIN visible y resto enmascarado: "401234******1234"
            '/\b\d{6}[*x]{4,}(?<digits>\d{4})\b/iu',
            // Etiqueta con separador obligatorio: "Tarjeta No.: 1234"
            '/\btarjeta\s*(?:n[uú]mero|no\.?|#)?\s*[:\-]\s*[*x•·\s\-]*(?<digits>\d{4})\b/iu',
        ];
        foreach ($patterns as $pattern) {
            if (preg_match($pattern, $text, $match) === 1) {
                return $match['digits'];
            }
        }

        return null;
    }

    private function isKnownSender(string $sender): bool
    {
        return in_array($sender, [
            'notificaciones@popularenlinea.com',
            'notificacionespopular@bpd.com.do',
            'popularteinforma@popularenlinea.com',
            'notificaciones@banreservas.com',
            'notificacionestubancoapp@banreservas.com',
            'banreservascomunicaciones@banreservas.com',
            'tubanco@banreservas.com',
            'notificaciones@qik.do',
            'promociones@mail.qik.com.do',
            'info@mail.qik.com.do',
            'cuentaqik@mail.qik.com.do',
            'no-reply-qik@qik.com.do',
            'alertas@bhd.com.do',
            'info@bhd.com.do',
            'servicios@bhd.com.do',
            'notificaciones@bhdleon.com.do',
            'service@intl.paypal.com',
            'googleplay-noreply@google.com',
            'payments-noreply@google.com',
        ], true);
    }

    /**
     * @param  array{subject:?string,body:string,sender_email:?string,sender_name:?string,received_at:CarbonImmutable}  $message
     * @return array<string, mixed>|null
     */
    private function classifyKnownFormat(string $sender, string $subject, string $body, array $message): ?array
    {
        return match ($sender) {
            'notificaciones@popularenlinea.com' => $this->classifyPopular($subject, $body, $message),
            'notificaciones@banreservas.com' => $this->classifyBanreservas($subject, $body, $message),
            'notificacionestubancoapp@banreservas.com' => null,
            'notificaciones@qik.do' => $this->classifyQik($subject, $body, $message),
            'alertas@bhd.com.do' => $this->classifyBhd($subject, $body, $message),
            'service@intl.paypal.com' => $this->classifyPaypal($subject, $body, $message),
            'googleplay-noreply@google.com' => $this->classifyGooglePlay($subject, $body, $message),
            'payments-noreply@google.com' => $this->classifyGooglePayments($subject, $body, $message),
            default => null,
        };
    }

    /** @return array<string, mixed>|null */
    private function classifyPopular(string $subject, string $body, array $message): ?array
    {
        if (! Str::of($subject)->lower()->exactly('notificación de consumo')
            || ! preg_match('/\baprobada(?=\s|en\s+caso|$)/iu', $body)) {
            return null;
        }
        $pattern = '/(?<money>(?:RD\$|US\$|DOP|USD)\s*[0-9][0-9.,\s]*)\s+'
            .'(?:peso\s+dominicano|d[oó]lar\s+estadounidense|DOP|USD)\s+'
            .'(?<date>\d{1,2}\/\d{1,2}\/\d{4}(?:\s+\d{1,2}:\d{2}\s*(?:AM|PM))?)\s+'
            .'(?<merchant>.+?)\s+aprobada(?=\s|en\s+caso|$)/iu';
        if (! preg_match($pattern, $body, $match)) {
            return null;
        }
        $amount = $this->parseMoneyToken($match['money']);
        if (! $amount) {
            return null;
        }

        return $this->knownCandidate(
            $message,
            $amount,
            'expense',
            $match['merchant'],
            'popular_consumption',
            $this->parseDate($match['date'], ['d/m/Y h:i A', 'd/m/Y'], $message['received_at']),
        );
    }

    /** @return array<string, mixed>|null */
    private function classifyBanreservas(string $subject, string $body, array $message): ?array
    {
        if (! Str::of($subject)->lower()->exactly('notificaciones banreservas')
            || ! str_contains(Str::lower($body), 'notificación de consumo')
            || ! preg_match('/estado\s*:\s*aprobado\b/iu', $body)) {
            return null;
        }
        $amount = $this->extractLabeledAmount($body, 'monto');
        if (! $amount
            || ! preg_match('/comercio\s*:\s*(?<merchant>.+?)\s+fecha\s+de\s+transacci[oó]n\s*:/iu', $body, $merchant)
            || ! preg_match('/fecha\s+de\s+transacci[oó]n\s*:\s*(?<date>\d{1,2}\/\d{1,2}\/\d{4}(?:\s+\d{1,2}:\d{2}\s*(?:AM|PM))?)/iu', $body, $date)) {
            return null;
        }

        return $this->knownCandidate(
            $message,
            $amount,
            'expense',
            $merchant['merchant'],
            'banreservas_consumption',
            $this->parseDate($date['date'], ['d/m/Y h:i A', 'd/m/Y'], $message['received_at']),
        );
    }

    /** @return array<string, mixed>|null */
    private function classifyQik(string $subject, string $body, array $message): ?array
    {
        $normalizedSubject = Str::lower($subject);
        $normalizedBody = Str::lower($body);
        if (str_contains($normalizedBody, 'declinado') || str_contains($normalizedBody, 'se intentó realizar')) {
            return null;
        }
        $isPurchase = in_array($normalizedSubject, [
            'usaste tu tarjeta de crédito qik',
            'se hizo una transacción con tu tarjeta de crédito qik',
        ], true);
        $isReversal = $normalizedSubject === 'se reversó una transacción en tu tarjeta de crédito qik';
        if (! $isPurchase && ! $isReversal) {
            return null;
        }
        $amount = $this->extractLabeledAmount($body, 'monto', allowBareDollarAsUsd: true);
        $merchantPattern = $isReversal
            ? '/lugar\s*(?<merchant>.+?)(?:\s+si\s+no|$)/iu'
            : '/localidad\s*(?<merchant>.+?)\s+fecha\s+y\s+hora/iu';
        if (! $amount || ! preg_match($merchantPattern, $body, $merchant)) {
            return null;
        }
        $occurredAt = $message['received_at'];
        if (preg_match('/fecha\s+y\s+hora\s*(?<date>\d{1,2}-\d{1,2}-\d{4}\s+\d{1,2}:\d{2}\s*(?:AM|PM))/iu', $body, $date)) {
            $occurredAt = $this->parseDate($date['date'], ['m-d-Y h:i A'], $message['received_at']);
        }

        return $this->knownCandidate(
            $message,
            $amount,
            $isReversal ? 'income' : 'expense',
            $merchant['merchant'],
            $isReversal ? 'qik_reversal' : 'qik_purchase',
            $occurredAt,
        );
    }

    /** @return array<string, mixed>|null */
    private function classifyBhd(string $subject, string $body, array $message): ?array
    {
        if (! Str::of($subject)->lower()->exactly('bhd notificación de transacciones')
            || ! preg_match('/\baprobada\b/iu', $body)) {
            return null;
        }
        $pattern = '/(?<date>\d{1,2}\/\d{1,2}\/\d{4})\s+'
            .'(?<time>\d{1,2}:\d{2}\s*(?:AM|PM))\s+'
            .'(?<row_currency>RD|DOP|US|USD)\s+'
            .'(?:(?:RD|US)?\s*\$\s*)?(?<number>[0-9][0-9.,\s]*)\s+'
            .'(?<merchant>.+?)\s+aprobada\b/iu';
        if (! preg_match($pattern, $body, $match)) {
            return null;
        }
        $currency = in_array(Str::upper($match['row_currency']), ['RD', 'DOP'], true) ? 'DOP' : 'USD';
        $amount = $this->parseMoneyParts($match['number'], $currency, false);
        if (! $amount) {
            return null;
        }

        return $this->knownCandidate(
            $message,
            $amount,
            'expense',
            $match['merchant'],
            'bhd_card_purchase',
            $this->parseDate($match['date'].' '.$match['time'], ['d/m/Y h:i A'], $message['received_at']),
        );
    }

    /** @return array<string, mixed>|null */
    private function classifyPaypal(string $subject, string $body, array $message): ?array
    {
        if (preg_match('/^recibo\s+de\s+su\s+pago\s+a\s+(?<merchant>.+)$/iu', $subject, $subjectMatch)) {
            $amount = $this->extractLabeledAmount($body, 'total');
            if (! $amount) {
                return null;
            }

            return $this->knownCandidate($message, $amount, 'expense', rtrim($subjectMatch['merchant'], '.'), 'paypal_payment');
        }
        if (preg_match('/^su\s+reembolso\s+de\s+(?<merchant>.+?)\s+est[aá]\s+en\s+camino$/iu', $subject, $subjectMatch)) {
            $amount = $this->extractLabeledAmount($body, 'total\s+del\s+reembolso');
            if (! $amount) {
                return null;
            }

            return $this->knownCandidate($message, $amount, 'income', $subjectMatch['merchant'], 'paypal_refund');
        }

        return null;
    }

    /** @return array<string, mixed>|null */
    private function classifyGooglePlay(string $subject, string $body, array $message): ?array
    {
        if (! preg_match('/^el\s+recibo\s+de\s+tu\s+pedido\s+de\s+google\s+play\b/iu', $subject)) {
            return null;
        }
        $amount = $this->extractLabeledAmount($body, 'total');

        return $amount
            ? $this->knownCandidate($message, $amount, 'expense', 'Google Play', 'google_play_receipt')
            : null;
    }

    /** @return array<string, mixed>|null */
    private function classifyGooglePayments(string $subject, string $body, array $message): ?array
    {
        if (! str_contains(Str::lower($subject), 'se entregó tu reembolso')) {
            return null;
        }
        $amount = $this->extractLabeledAmount($body, 'reembolso\s+por');

        return $amount
            ? $this->knownCandidate($message, $amount, 'income', 'Google Payments', 'google_payments_refund')
            : null;
    }

    /** @return array<string, mixed> */
    private function knownCandidate(
        array $message,
        array $amount,
        string $direction,
        string $merchant,
        string $rule,
        ?CarbonImmutable $occurredAt = null,
    ): array {
        $merchant = trim((string) preg_replace('/\s+/u', ' ', $merchant), " \t\n\r\0\x0B.:;-");
        $category = $this->suggestCategory(Str::lower($merchant));
        $reasons = ['known_sender_format:'.$rule, 'currency_detected:'.$amount['currency']];
        if ($category) {
            $reasons[] = 'category_rule:'.$category;
        }

        return [
            'merchant' => $merchant,
            'amount' => $direction === 'income' ? $amount['minor'] : -$amount['minor'],
            'currency' => $amount['currency'],
            'direction' => $direction,
            'category_suggestion' => $category,
            'occurred_at' => $occurredAt ?? $message['received_at'],
            'confidence' => 98,
            'reasons' => $reasons,
            'status' => 'pending',
        ];
    }

    /** @return array{minor:int,currency:string}|null */
    private function extractLabeledAmount(string $text, string $labelPattern, bool $allowBareDollarAsUsd = false): ?array
    {
        $number = '[0-9]+(?:[.,\s][0-9]{3})*(?:[.,][0-9]{2})|[0-9]+(?:[.,][0-9]{2})?';
        $pattern = '/(?:'.$labelPattern.')\s*:?\s*(?:'
            .'(?<currency_before>RD\$|US\$|DOP|USD|EUR|€|\$)\s*(?<amount_before>'.$number.')\s*(?<currency_after>DOP|USD|EUR)?'
            .'|(?<amount_only>'.$number.')\s*(?<currency_only>DOP|USD|EUR)'
            .')/iu';
        if (! preg_match($pattern, $text, $match)) {
            return null;
        }
        $amount = $match['amount_before'] !== '' ? $match['amount_before'] : $match['amount_only'];
        $currency = $match['currency_before'] !== '' ? $match['currency_before'] : $match['currency_only'];
        if ($currency === '$' && ($match['currency_after'] ?? '') !== '') {
            $currency = $match['currency_after'];
        }

        return $this->parseMoneyParts($amount, $currency, $allowBareDollarAsUsd);
    }

    /** @return array{minor:int,currency:string}|null */
    private function parseMoneyToken(string $token, bool $allowBareDollarAsUsd = false): ?array
    {
        if (! preg_match('/^(?<currency>RD\$|US\$|DOP|USD|EUR|€|\$)\s*(?<amount>[0-9][0-9.,\s]*)$/iu', trim($token), $match)) {
            return null;
        }

        return $this->parseMoneyParts($match['amount'], $match['currency'], $allowBareDollarAsUsd);
    }

    /** @return array{minor:int,currency:string}|null */
    private function parseMoneyParts(string $rawAmount, string $rawCurrency, bool $allowBareDollarAsUsd): ?array
    {
        $raw = preg_replace('/\s+/', '', $rawAmount);
        $digits = preg_replace('/\D/', '', (string) $raw);
        if ($digits === '' || (int) $digits === 0) {
            return null;
        }
        $minor = preg_match('/[.,][0-9]{2}$/', (string) $raw) === 1 ? (int) $digits : (int) $digits * 100;
        $token = Str::upper(trim($rawCurrency));
        $currency = match ($token) {
            'RD$', 'DOP' => 'DOP',
            'US$', 'USD' => 'USD',
            'EUR', '€' => 'EUR',
            '$' => $allowBareDollarAsUsd ? 'USD' : null,
            default => null,
        };

        return $currency ? ['minor' => $minor, 'currency' => $currency] : null;
    }

    /** @param list<string> $formats */
    private function parseDate(string $value, array $formats, CarbonImmutable $fallback): CarbonImmutable
    {
        foreach ($formats as $format) {
            try {
                $parsed = CarbonImmutable::createFromFormat('!'.$format, trim($value), 'America/Santo_Domingo');
                if ($parsed !== false) {
                    return $parsed;
                }
            } catch (\Throwable) {
                // Try the next verified provider format.
            }
        }

        return $fallback;
    }

    /** @return array{minor:int,currency:string}|null */
    private function extractAmount(string $text): ?array
    {
        $pattern = '/(?<currency>RD\$|DOP|US\$|USD|EUR|€|\$)\s*(?<amount>[0-9]{1,3}(?:[.,\s][0-9]{3})*(?:[.,][0-9]{2})|[0-9]+(?:[.,][0-9]{2})?)/iu';
        if (! preg_match_all($pattern, $text, $matches, PREG_SET_ORDER | PREG_OFFSET_CAPTURE)) {
            return null;
        }

        $match = $matches[0];
        $bestScore = 0;
        foreach ($matches as $candidate) {
            $offset = $candidate[0][1];
            $context = Str::lower(substr($text, max(0, $offset - 40), min(40, $offset)));
            $score = preg_match('/(?:^|\W)total\s*$/u', $context) ? 100
                : (preg_match('/(?:monto|importe)\s*$/u', $context) ? 50 : 0);
            if ($score > $bestScore) {
                $match = $candidate;
                $bestScore = $score;
            }
        }

        $raw = preg_replace('/\s+/', '', $match['amount'][0]);
        $digits = preg_replace('/\D/', '', $raw);
        if ($digits === '' || (int) $digits === 0) {
            return null;
        }

        $hasDecimals = preg_match('/[.,][0-9]{2}$/', $raw) === 1;
        $minor = $hasDecimals ? (int) $digits : (int) $digits * 100;
        $currencyToken = Str::upper($match['currency'][0]);
        $currency = match ($currencyToken) {
            'RD$', 'DOP' => 'DOP',
            'US$', 'USD' => 'USD',
            'EUR', '€' => 'EUR',
            default => null,
        };
        if (! $currency) {
            return null;
        }

        return ['minor' => $minor, 'currency' => $currency];
    }

    private function suggestCategory(string $text): ?string
    {
        $rules = [
            'Alimentación' => ['supermercado', 'restaurant', 'restaurante', 'comida', 'grocery', 'uber eats', 'uber*eats', 'ubereats'],
            'Transporte' => ['uber', 'didi', 'taxi', 'gasolina', 'combustible', 'transporte'],
            'Suscripciones' => ['suscripción', 'suscripcion', 'subscription', 'netflix', 'spotify'],
            'Servicios' => ['electricidad', 'internet', 'teléfono', 'telefono', 'utility'],
            'Salud' => ['farmacia', 'clínica', 'clinica', 'hospital', 'pharmacy'],
        ];

        foreach ($rules as $category => $keywords) {
            if (collect($keywords)->contains(fn (string $keyword): bool => str_contains($text, $keyword))) {
                return $category;
            }
        }

        return null;
    }

    private function plainText(string $value): string
    {
        $value = preg_replace('~<(style|script)\b[^>]*>.*?</\1>~isu', ' ', $value) ?? $value;
        $decoded = html_entity_decode(strip_tags($value), ENT_QUOTES | ENT_HTML5, 'UTF-8');

        return trim((string) preg_replace('/\s+/u', ' ', $decoded));
    }
}
