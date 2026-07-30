<?php

namespace App\Services;

class FinancialEmailExtractor
{
    public function extract(?string $subject, ?string $snippet, mixed $occurredAt): ?array
    {
        $text = trim(($subject ?? '').' '.($snippet ?? ''));
        if ($text === '' || $this->isDefiniteNonTransaction($subject, $snippet)) {
            return null;
        }

        $expense = preg_match('/\b(compra|pago|cargo|debito|débito|consumo|purchase|payment|charged|spent)\b|usaste tu tarjeta/iu', $text) === 1;
        $income = preg_match('/\b(abono|deposito|depósito|ingreso|transferencia recibida|crédito recibido|received|deposit)\b/iu', $text) === 1;
        if ($expense === $income) {
            return null;
        }

        if (! preg_match('/(?:(RD\$|USD|DOP|EUR|\$|€)\s*)([0-9][0-9., ]{0,16})/iu', $text, $match)) {
            return null;
        }
        $currency = $this->currencyFor($match[1], $text);
        if ($currency === null) {
            // Un "$" a secas no dice qué moneda es, y equivocarse cuesta caro: tomar
            // RD$1,500 por dólares lo convierte en unos RD$90,000. Sin una pista clara
            // se descarta y el correo queda para revisión manual.
            return null;
        }
        $amount = $this->minorUnits($match[2]);
        if ($amount === null || $amount <= 0) {
            return null;
        }

        $merchant = $this->merchant($text);
        $category = $this->category($text, $expense);

        return [
            'merchant' => $merchant,
            'card_last_four' => $this->cardLastFour($text),
            'amount' => $amount,
            'currency' => $currency,
            'direction' => $expense ? 'expense' : 'income',
            'category_suggestion' => $category,
            'occurred_at' => $occurredAt ?? now(),
            'confidence' => $merchant && $category ? 90 : 80,
            'status' => 'pending',
            'subject' => $subject,
        ];
    }

    /**
     * Ultimos cuatro digitos de la tarjeta que origino el movimiento, para que la app
     * pueda preseleccionar la cuenta correcta al aceptar el correo.
     *
     * Solo se acepta cuando hay mascara ("****1234") o etiqueta explicita ("terminada
     * en 1234"). Un patron mas suelto como "tarjeta ... 1234" tomaria el importe por
     * numero de tarjeta en correos como los de Qik, donde el monto va justo despues.
     */
    private function cardLastFour(string $text): ?string
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

    /**
     * Resuelve la moneda del importe encontrado.
     *
     * Los símbolos cualificados (RD$, USD, DOP, EUR, €) se toman tal cual. Un "$" a
     * secas es ambiguo -en RD se usa tanto para pesos como para dólares-, así que se
     * busca un cualificador en el resto del texto: "US$", "dólares" o "USD" lo hacen
     * dólares; "RD$" o "pesos" lo hacen pesos. Sin ninguna pista devuelve null, porque
     * adivinar aquí desplaza el importe por un factor de sesenta.
     */
    private function currencyFor(string $symbol, string $text): ?string
    {
        $qualified = match (strtoupper($symbol)) {
            'RD$', 'DOP' => 'DOP',
            'EUR', '€' => 'EUR',
            'USD' => 'USD',
            default => null,
        };
        if ($qualified !== null) {
            return $qualified;
        }

        if (preg_match('/\b(?:USD|US\$|d[oó]lar(?:es)?)\b/iu', $text) === 1) {
            return 'USD';
        }
        if (preg_match('/\b(?:RD\$|DOP|pesos?\s+dominicanos?|pesos)\b/iu', $text) === 1) {
            return 'DOP';
        }

        return null;
    }

    public function isDefiniteNonTransaction(?string $subject, ?string $snippet): bool
    {
        $text = trim(($subject ?? '').' '.($snippet ?? ''));

        return preg_match('/\b(preaprob(?:ad[ao]|ación)|l[ií]mite disponible|recordatorio de pago|pago m[ií]nimo|payment due|saldo pendiente|budget reached|presupuesto alcanzado|declinad[ao]|rechazad[ao]|cancelad[ao]|pending|declined|rejected)\b/iu', $text) === 1;
    }

    private function merchant(string $text): ?string
    {
        $merchants = [
            'paypal' => 'PayPal',
            'amazon' => 'Amazon',
            'netflix' => 'Netflix',
            'spotify' => 'Spotify',
            'uber' => 'Uber',
            'didi' => 'DiDi',
            'indriver' => 'inDrive',
            'airbnb' => 'Airbnb',
            'pedidosya' => 'PedidosYa',
            'claro' => 'Claro',
            'altice' => 'Altice',
            'edesur' => 'Edesur',
            'edenorte' => 'Edenorte',
            'edeeste' => 'Edeeste',
            'supermercado nacional' => 'Supermercado Nacional',
            'jumbo' => 'Jumbo',
            'la sirena' => 'La Sirena',
            'super pola' => 'Super Pola',
        ];

        foreach ($merchants as $needle => $merchant) {
            if (preg_match('/(?<![\pL\pN])'.preg_quote($needle, '/').'(?![\pL\pN])/iu', $text)) {
                return $merchant;
            }
        }

        return null;
    }

    private function category(string $text, bool $expense): string
    {
        if (! $expense) {
            return preg_match('/\b(salario|sueldo|n[oó]mina|quincena|honorarios?)\b/iu', $text) ? 'Salario' : 'Otros';
        }

        $rules = [
            'Alimentación' => '/\b(supermercado|colmado|nacional|jumbo|sirena|pola|bravo|grocer|market|panader[ií]a)\b/iu',
            'Restaurantes' => '/\b(restaurante|pizza|burger|mcdonald|kfc|domino|sushi|pedidosya)\b/iu',
            'Transporte' => '/\b(uber|didi|indriver|taxi|gasolina|combustible|peaje|parqueo|metro)\b/iu',
            'Servicios' => '/\b(claro|altice|viva|edesur|edenorte|edeeste|internet|tel[eé]fono|factura|electricidad|agua)\b/iu',
            'Entretenimiento' => '/\b(netflix|spotify|hbo|disney|cine|steam|concierto|juego)\b/iu',
            'Salud' => '/\b(farmacia|cl[ií]nica|hospital|m[eé]dic|dentista|laboratorio)\b/iu',
            'Viajes' => '/\b(vuelo|hotel|airbnb|aeropuerto|arajet|jetblue|resort)\b/iu',
            'Educación' => '/\b(colegio|universidad|curso|libro|matr[ií]cula|inscripci[oó]n)\b/iu',
            'Vivienda' => '/\b(alquiler|renta|hipoteca|condominio)\b/iu',
            'Compras' => '/\b(paypal|amazon|google|apple|zara|shein|temu|tienda|shopping|ropa|calzado)\b/iu',
        ];

        foreach ($rules as $category => $pattern) {
            if (preg_match($pattern, $text)) {
                return $category;
            }
        }

        return 'Otros';
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
