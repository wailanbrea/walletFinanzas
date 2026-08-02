<?php

namespace App\Services;

class FinancialEmailExtractor
{
    public const CARD_PURCHASE_APPROVED = 'CARD_PURCHASE_APPROVED';

    public const TRANSFER_OUT = 'TRANSFER_OUT';

    public const TRANSFER_IN = 'TRANSFER_IN';

    public const INTERNAL_TRANSFER = 'INTERNAL_TRANSFER';

    public const CARD_PAYMENT = 'CARD_PAYMENT';

    public const REFUND_REVERSAL = 'REFUND_REVERSAL';

    public const BANK_FEE_TAX = 'BANK_FEE_TAX';

    public const CASH_WITHDRAWAL = 'CASH_WITHDRAWAL';

    public const DEPOSIT = 'DEPOSIT';

    public const RECEIPT_CONFIRMED = 'RECEIPT_CONFIRMED';

    public function extract(?string $subject, ?string $snippet, mixed $occurredAt): ?array
    {
        $text = trim(($subject ?? '').' '.($snippet ?? ''));
        if ($text === '' || $this->isDefiniteNonTransaction($subject, $snippet)) {
            return null;
        }

        $eventType = $this->eventType($text);
        if ($eventType === null) {
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

        $direction = match ($eventType) {
            self::TRANSFER_OUT, self::INTERNAL_TRANSFER, self::CARD_PAYMENT,
            self::CASH_WITHDRAWAL => 'transfer',
            self::TRANSFER_IN, self::REFUND_REVERSAL, self::DEPOSIT => 'income',
            default => 'expense',
        };
        $merchant = $this->merchant($text);
        $category = $this->category($text, $direction, $eventType);
        // Un comercio desconocido o una categoria generica nunca merece confianza alta.
        $confidence = $merchant !== null && $category !== 'Otros' ? 90 : 40;

        return [
            'merchant' => $merchant,
            'card_last_four' => $this->cardLastFour($text),
            'amount' => $amount,
            'currency' => $currency,
            'direction' => $direction,
            'event_type' => $eventType,
            'category_suggestion' => $category,
            'occurred_at' => $occurredAt ?? now(),
            'confidence' => $confidence,
            'subject' => $subject,
        ];
    }

    private function eventType(string $text): ?string
    {
        if (! $this->hasExecutionPhrase($text)) {
            return null;
        }

        if (preg_match('/\b(n[oó]mina|salario|sueldo|quincena|honorarios?|pensi[oó]n|jubilaci[oó]n)\b/iu', $text)) {
            return self::DEPOSIT;
        }
        if (preg_match('/\b(?:pago|abono)\s+(?:de|a)\s+(?:tu|su|la)?\s*tarjeta|\bpago\s+tarjeta\b/iu', $text)) {
            return self::CARD_PAYMENT;
        }
        if (preg_match('/\b(?:revers[oa]|devoluci[oó]n|reembolso|refund|reversal)\b/iu', $text)) {
            return self::REFUND_REVERSAL;
        }
        if (preg_match('/\b(?:comisi[oó]n|impuesto|itbis|cargo bancario|sobregiro|dgii|marbete|bank fee|tax)\b/iu', $text)) {
            return self::BANK_FEE_TAX;
        }
        if (preg_match('/\b(?:retiro|cajero|atm|cash withdrawal)\b/iu', $text)) {
            return self::CASH_WITHDRAWAL;
        }
        if (preg_match('/\b(?:transferencia|transferiste|traspaso)\b/iu', $text)) {
            if (preg_match('/\b(?:entre (?:mis|tus|sus) productos|interna|internal)\b/iu', $text)) {
                return self::INTERNAL_TRANSFER;
            }
            if (preg_match('/\b(?:enviad[ao]|saliente|debitad[ao]|descontad[ao]|transferiste|sent|outgoing)\b/iu', $text)) {
                return self::TRANSFER_OUT;
            }
            if (preg_match('/\b(?:recibid[ao]|entrante|acreditad[ao]|received|incoming)\b/iu', $text)) {
                return self::TRANSFER_IN;
            }

            return null;
        }
        if (preg_match('/\b(?:dep[oó]sito|abono|ingreso|acreditaci[oó]n|cr[eé]dito recibido|deposit)\b/iu', $text)) {
            return self::DEPOSIT;
        }
        if (preg_match('/\b(?:recibo|receipt)\b|\bha pagado\b|\bpago\b.{0,40}\brealizad[ao]\b|\bpayment completed\b/iu', $text)) {
            return self::RECEIPT_CONFIRMED;
        }
        if (preg_match('/\b(?:compra|cargo|d[eé]bito|consumo|purchase|charged|spent|transacci[oó]n)\b|usaste tu tarjeta/iu', $text)) {
            return self::CARD_PURCHASE_APPROVED;
        }

        return null;
    }

    private function hasExecutionPhrase(string $text): bool
    {
        return preg_match('/\b(?:aprobad[ao]|completad[ao]|procesad[ao]|acreditad[ao]|recibid[ao]|pagad[ao]|cobrad[ao]|debitad[ao]|descontad[ao]|realizad[ao](?:\s+(?:con [eé]xito|satisfactoriamente))?|enviad[ao](?:\s+satisfactoriamente)?|successful(?:ly)?|completed|approved|charged|paid|received)\b|\bse\s+(?:hizo|realiz[oó]|ha realizado|cobr[oó]|debit[oó]|descont[oó]|acredit[oó]|recibi[oó])\b|\busaste tu tarjeta\b/iu', $text) === 1;
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
            // Prefijo visible y resto enmascarado: "53*************8324" o
            // "401234******1234". Qik solo deja visibles dos digitos al inicio.
            '/\b\d{2,6}[*x]{4,}(?<digits>\d{4})\b/iu',
            // Banreservas usa solo dos viñetas: "Su tarjeta MCG-MULTIMONEDA ••4116".
            '/\btarjeta\b[^\n|]{0,80}?(?:[*x•·]\s*){2,}[\s\-]*(?<digits>\d{4})\b/iu',
            // Etiqueta con separador obligatorio: "Tarjeta No.: 1234". La barra entra
            // porque asi quedan las celdas de tabla al convertir el cuerpo a texto.
            '/\btarjeta\s*(?:n[uú]mero|no\.?|#)?\s*[:|\-]\s*[*x•·\s\-]*(?<digits>\d{4})\b/iu',
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

        $hardNegative = preg_match('/\b(?:programad[ao]|pr[oó]ximo pago|pago futuro|renovaci[oó]n futura|recordatorio|payment due|pago m[ií]nimo|saldo pendiente|budget reached|presupuesto alcanzado|declinad[ao]|rechazad[ao]|cancelad[ao]|pendiente|pending|declined|rejected|estado de cuenta|balance alert|alerta de saldo|l[ií]mite disponible|aumentamos (?:el |tu |su )?l[ií]mite|cambio de l[ií]mite|preaprob(?:ad[ao]|aci[oó]n)|c[oó]digo (?:de seguridad|otp|de un solo uso)|clave otp|tarjeta (?:activada|bloqueada|desbloqueada|vencida))\b/iu', $text) === 1;
        if ($hardNegative) {
            return true;
        }

        $promotion = preg_match('/\b(?:oferta|promoci[oó]n|publicidad|newsletter|cashback|devoluci[oó]n de hasta|tope de devoluci[oó]n|monto m[ií]nimo|precio desde|preaprob(?:ad[ao]|aci[oó]n))\b/iu', $text) === 1;
        $promotionalSubject = preg_match('/\b(?:oferta|promoci[oó]n|publicidad|newsletter|cashback|preaprob(?:ad[ao]|aci[oó]n))\b/iu', $subject ?? '') === 1;
        $offerStructure = preg_match('/\b(?:compra|consumo|precio)\s+(?:m[ií]nim[oa]|desde)|\b(?:recibe|obt[eé]n)\b.{0,50}\b(?:hasta|cashback|devoluci[oó]n)\b/iu', $text) === 1;

        // Muchos bancos agregan un pie promocional a un consumo real. Solo manda el
        // lenguaje promocional cuando el aviso no contiene una ejecucion definitiva.
        return $promotion && ($promotionalSubject || $offerStructure || ! $this->hasExecutionPhrase($text));
    }

    private function merchant(string $text): ?string
    {
        $merchants = [
            'paypal' => 'PayPal',
            'amazon' => 'Amazon',
            'netflix' => 'Netflix',
            'spotify' => 'Spotify',
            'uber eats' => 'Uber Eats',
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
            'epic games' => 'Epic Games',
            'aliexpress' => 'AliExpress',
            'google play' => 'Google Play',
            'habbo' => 'Habbo',
            'contabo' => 'Contabo',
            'boxpaq' => 'Boxpaq',
            'itla' => 'ITLA',
            'dgii' => 'DGII',
            'mcdonald' => "McDonald's",
            'jade' => 'Jade',
            'domino' => "Domino's",
            'shell' => 'Shell',
            'totalenergies' => 'TotalEnergies',
        ];

        // Primero las marcas conocidas: dan un nombre canonico y limpio ("PayPal" y no
        // "PAYPAL *EBAY COMMERCE"), que ademas es el que alimenta la categorizacion.
        foreach ($merchants as $needle => $merchant) {
            if (preg_match('/(?<![\pL\pN])'.preg_quote($needle, '/').'(?![\pL\pN])/iu', $text)) {
                return $merchant;
            }
        }

        return $this->merchantFromLabel($text);
    }

    /**
     * Comercio tomado del campo que el propio banco etiqueta.
     *
     * La lista de marcas solo reconoce lo que ya conoce, asi que cualquier negocio local
     * quedaba como "no identificado" por mucho texto que trajera el correo. Los avisos si
     * traen el dato rotulado: "Comercio: FERRETERIA OCHOA". El separador puede ser una
     * barra porque asi quedan las celdas de tabla al convertir el cuerpo a texto.
     */
    private function merchantFromLabel(string $text): ?string
    {
        // "localidad" va aparte y de ultima: en Qik ese campo trae el comercio
        // ("Localidad  OPENAI *CHATGPT SUB"), pero en otros bancos es la ciudad. Si el
        // aviso trae un rotulo de comercio de verdad, ese gana y la localidad ni se mira.
        foreach (['comercio|establecimiento|negocio|afiliado|adquirente|merchant|lugar de consumo', 'localidad'] as $labels) {
            $found = $this->valueForLabels($labels, $text);
            if ($found !== null) {
                return $found;
            }
        }

        return null;
    }

    /** Busca el valor de cualquiera de [$labels] en [$text]. */
    private function valueForLabels(string $labels, string $text): ?string
    {

        // Con dos puntos o barra hay rotulo explicito y vale en cualquier posicion:
        // "Establecimiento: PANADERIA" o la celda "Comercio: | PANADERIA".
        $labelled = '/\b(?:'.$labels.')\b(?:\h*[:|]\h*)+\R?\h*(?<value>[^\n|]{2,60})/iu';
        // Sin puntuacion el valor va detras del rotulo o en la linea siguiente, y ahi el
        // rotulo tiene que abrir linea o celda. En prosa, "...aprobada en el comercio\n
        // Banco Popular le informa" devolvia esa frase entera como nombre del comercio.
        //
        // Los espacios de relleno de Qik ("Localidad      OPENAI") no sirven para
        // reconocerlo: al convertir el cuerpo a texto se colapsan a uno solo.
        $onItsOwnLine = '/(?:^|\n|\|)\h*(?:'.$labels.')(?:\h*\R+\h*|\h+)(?<value>[^\n|]{2,60})/iu';

        if (! preg_match($labelled, $text, $match) && ! preg_match($onItsOwnLine, $text, $match)) {
            return null;
        }
        $value = trim(preg_replace('/\s+/u', ' ', $match['value']) ?? $match['value']);
        // En una celda de tabla la barra ya acota el valor, pero en prosa el rotulo va
        // seguido del resto de la frase: "Establecimiento: PANADERIA por RD$250.00". Se
        // corta donde empieza otro dato para no quedarse con media oracion por nombre.
        $value = preg_split(
            '/\s+(?:por|monto|importe|fecha|el d[ií]a|referencia|autorizaci[oó]n|tarjeta)\b|\s*(?:RD\$|USD|DOP|EUR|€|\$)/iu',
            $value
        )[0] ?? $value;
        // Quita puntuacion de cierre que arrastra la celda siguiente.
        $value = trim($value, " .,;:-–—\t");

        // Tiene que parecer un nombre: un monto o un numero suelto no es un comercio.
        if (mb_strlen($value) < 2 || ! preg_match('/\pL{2}/u', $value)) {
            return null;
        }
        // Un rotulo seguido de otro rotulo significa que la celda venia vacia.
        if (preg_match('/^(?:'.$labels.'|monto|tarjeta|fecha|referencia|autorizaci[oó]n)\b/iu', $value)) {
            return null;
        }

        return mb_substr($value, 0, 60);
    }

    private function category(string $text, string $direction, string $eventType): string
    {
        if ($eventType === self::BANK_FEE_TAX) {
            return 'Impuestos';
        }
        if ($direction === 'transfer') {
            return 'Otros';
        }
        if ($direction === 'income') {
            return preg_match('/\b(salario|sueldo|n[oó]mina|quincena|honorarios?)\b/iu', $text) ? 'Salario' : 'Otros';
        }

        $rules = [
            'Impuestos' => '/\b(dgii|marbete|impuesto|itbis|tax)\b/iu',
            'Alimentación' => '/\b(supermercado|colmado|nacional|jumbo|sirena|pola|bravo|grocer|market|panader[ií]a)\b/iu',
            'Restaurantes' => '/\b(restaurante|pizza|burger|mcdonald|kfc|domino|jade|sushi|pedidosya|uber eats|helad(?:o|os|er[ií]a))\b/iu',
            'Combustible' => '/\b(shell|totalenergies|estaci[oó]n total|gasolina|combustible)\b/iu',
            'Transporte' => '/\b(uber|didi|indriver|taxi|peaje|parqueo|metro)\b/iu',
            'Servicios' => '/\b(claro|altice|viva|edesur|edenorte|edeeste|internet|tel[eé]fono|factura|electricidad|agua|openai|chatgpt|contabo|boxpaq|courier)\b/iu',
            'Entretenimiento' => '/\b(netflix|spotify|hbo|disney|cine|steam|epic games|google play|habbo|concierto|juego)\b/iu',
            'Salud' => '/\b(farmacia|cl[ií]nica|hospital|m[eé]dic|dentista|laboratorio)\b/iu',
            'Viajes' => '/\b(vuelo|hotel|airbnb|aeropuerto|arajet|jetblue|resort)\b/iu',
            'Educación' => '/\b(colegio|universidad|itla|curso|libro|matr[ií]cula|inscripci[oó]n)\b/iu',
            'Vivienda' => '/\b(alquiler|renta|hipoteca|condominio|ferreter[ií]a)\b/iu',
            'Compras' => '/\b(paypal|amazon|aliexpress|google|apple|zara|shein|temu|tienda|shopping|ropa|calzado)\b/iu',
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
