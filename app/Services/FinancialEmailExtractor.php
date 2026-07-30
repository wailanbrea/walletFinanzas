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
        $income = preg_match('/\b(abono|deposito|depósito|ingreso|acreditaci[oó]n|acreditad[ao]|transferencia recibida|crédito recibido|received|deposit)\b/iu', $text) === 1;

        // Un aviso de nomina es dinero que entra aunque diga "pago": "pago de nomina" es
        // el sueldo, no un gasto. Sin esto el sueldo se restaba del balance y ademas
        // inflaba los gastos del mes. Y "sueldo quincenal fue pagado" no casaba con
        // ninguna de las dos listas, asi que el aviso se descartaba entero.
        if (preg_match('/\b(n[oó]mina|salario|sueldo|quincena|honorarios?|pensi[oó]n|jubilaci[oó]n)\b/iu', $text) === 1) {
            $expense = false;
            $income = true;
        }

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
            'epic games' => 'Epic Games',
            'aliexpress' => 'AliExpress',
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

    private function category(string $text, bool $expense): string
    {
        if (! $expense) {
            return preg_match('/\b(salario|sueldo|n[oó]mina|quincena|honorarios?)\b/iu', $text) ? 'Salario' : 'Otros';
        }

        $rules = [
            'Alimentación' => '/\b(supermercado|colmado|nacional|jumbo|sirena|pola|bravo|grocer|market|panader[ií]a)\b/iu',
            'Restaurantes' => '/\b(restaurante|pizza|burger|mcdonald|kfc|domino|sushi|pedidosya|helad(?:o|os|er[ií]a))\b/iu',
            'Transporte' => '/\b(uber|didi|indriver|taxi|gasolina|combustible|peaje|parqueo|metro)\b/iu',
            'Servicios' => '/\b(claro|altice|viva|edesur|edenorte|edeeste|internet|tel[eé]fono|factura|electricidad|agua|openai|chatgpt)\b/iu',
            'Entretenimiento' => '/\b(netflix|spotify|hbo|disney|cine|steam|epic games|concierto|juego)\b/iu',
            'Salud' => '/\b(farmacia|cl[ií]nica|hospital|m[eé]dic|dentista|laboratorio)\b/iu',
            'Viajes' => '/\b(vuelo|hotel|airbnb|aeropuerto|arajet|jetblue|resort)\b/iu',
            'Educación' => '/\b(colegio|universidad|curso|libro|matr[ií]cula|inscripci[oó]n)\b/iu',
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
