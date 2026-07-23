<?php

namespace App\Services;

class FinancialEmailExtractor
{
    public function extract(?string $subject, ?string $snippet, mixed $occurredAt): ?array
    {
        $text = trim(($subject ?? '').' '.($snippet ?? ''));
        $expense = preg_match('/\b(compra|pago|cargo|debito|débito|purchase|payment|charged|spent)\b/iu', $text) === 1;
        $income = preg_match('/\b(abono|deposito|depósito|ingreso|credito|crédito|received|deposit)\b/iu', $text) === 1;
        if ($text === '' || $expense === $income) {
            return null;
        }

        if (! preg_match('/(?:(RD\$|USD|DOP|EUR|\$|€)\s*)([0-9][0-9., ]{0,16})/iu', $text, $match)) {
            return null;
        }
        $currency = match (strtoupper($match[1])) {
            'RD$', 'DOP' => 'DOP',
            'EUR', '€' => 'EUR',
            default => 'USD',
        };
        $amount = $this->minorUnits($match[2]);
        if ($amount === null || $amount <= 0) {
            return null;
        }

        return [
            'merchant' => null,
            'amount' => $amount,
            'currency' => $currency,
            'direction' => $expense ? 'expense' : 'income',
            'category_suggestion' => null,
            'occurred_at' => $occurredAt ?? now(),
            'confidence' => 80,
            'status' => 'pending',
            'subject' => $subject,
        ];
    }

    private function minorUnits(string $value): ?int
    {
        $value = str_replace(' ', '', trim($value));
        $lastComma = strrpos($value, ',');
        $lastDot = strrpos($value, '.');
        $separator = max($lastComma === false ? -1 : $lastComma, $lastDot === false ? -1 : $lastDot);
        $decimals = '00';
        if ($separator >= 0 && strlen($value) - $separator - 1 === 2) {
            $decimals = substr($value, $separator + 1, 2);
            $value = substr($value, 0, $separator);
        }
        $whole = str_replace([',', '.'], '', $value);
        if (! ctype_digit($whole) || ! ctype_digit($decimals) || strlen($whole) > 12) {
            return null;
        }

        return ((int) $whole * 100) + (int) $decimals;
    }
}
