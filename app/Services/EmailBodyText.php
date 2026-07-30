<?php

namespace App\Services;

/**
 * Convierte el cuerpo de un correo en texto plano clasificable.
 *
 * Hace falta porque los bancos ponen el comercio, la tarjeta y el monto en la tabla del
 * cuerpo, no en el asunto ni en la vista previa. Clasificando solo la vista previa
 * —unos 200 caracteres— hay avisos que nunca se pueden resolver, por buenas que sean las
 * reglas o por caro que sea el modelo que se use.
 */
class EmailBodyText
{
    /**
     * Tope de caracteres que se guardan por correo.
     *
     * Acotado a proposito: el dato util de un aviso bancario esta arriba, y dejar entrar
     * el pie de pagina completo solo agrega ruido con el que las reglas pueden confundirse.
     */
    public const MAX_LENGTH = 4000;

    /**
     * Texto del cuerpo de un mensaje de Gmail (respuesta con format=full).
     *
     * Se prefiere text/plain: el HTML de un banco es una tabla anidada y su version en
     * texto viene mas limpia. Si solo hay HTML, se convierte.
     */
    public function fromGmailPayload(?array $payload): ?string
    {
        if ($payload === null) {
            return null;
        }
        $plain = $this->firstGmailPartOfType($payload, 'text/plain');
        if ($plain !== null && trim($plain) !== '') {
            return $this->normalize($plain);
        }
        $html = $this->firstGmailPartOfType($payload, 'text/html');

        return $html === null ? null : $this->fromHtml($html);
    }

    /** Texto del cuerpo de un mensaje de Microsoft Graph. */
    public function fromGraphBody(?array $body): ?string
    {
        $content = $body['content'] ?? null;
        if (! is_string($content) || trim($content) === '') {
            return null;
        }

        return strtolower((string) ($body['contentType'] ?? '')) === 'html'
            ? $this->fromHtml($content)
            : $this->normalize($content);
    }

    /**
     * HTML a texto.
     *
     * No se usa strip_tags a secas porque pegaria los valores de una tabla: "Comercio" y
     * "Nacional" en dos celdas quedarian como "ComercioNacional" y ninguna regla lo
     * reconoceria. Cada limite de celda o de bloque se vuelve un separador.
     */
    public function fromHtml(string $html): string
    {
        // El contenido de script y style no es texto del correo.
        $text = preg_replace('#<(script|style)\b[^>]*>.*?</\1>#is', ' ', $html) ?? $html;
        $text = preg_replace('#</(td|th)\s*>#i', ' | ', $text) ?? $text;
        $text = preg_replace('#<(br|/p|/div|/tr|/h[1-6]|/li)\s*/?>#i', "\n", $text) ?? $text;
        $text = strip_tags($text);
        $text = html_entity_decode($text, ENT_QUOTES | ENT_HTML5, 'UTF-8');

        return $this->normalize($text);
    }

    /** Colapsa el espacio en blanco y recorta al tope. */
    public function normalize(string $text): string
    {
        // El espacio duro sobrevive a html_entity_decode y rompe las reglas de montos.
        $text = str_replace(["\xC2\xA0", "\r"], [' ', "\n"], $text);
        $text = preg_replace('/[ \t]+/', ' ', $text) ?? $text;
        $text = preg_replace('/\n{2,}/', "\n", $text) ?? $text;
        // Las tablas de los bancos traen celdas vacias: sin colapsarlas, el texto queda
        // lleno de separadores seguidos y el dato util se pierde entre ellos.
        $text = preg_replace('/(?:\|[ \t]*){2,}/', '| ', $text) ?? $text;
        $text = trim($text, " \t\n|");

        return mb_substr($text, 0, self::MAX_LENGTH);
    }

    /** Primera parte con el mime pedido, recorriendo el arbol de partes. */
    private function firstGmailPartOfType(array $part, string $mimeType): ?string
    {
        if (strtolower((string) ($part['mimeType'] ?? '')) === $mimeType) {
            $decoded = $this->decodeGmailData($part['body']['data'] ?? null);
            if ($decoded !== null) {
                return $decoded;
            }
        }
        foreach ($part['parts'] ?? [] as $child) {
            if (is_array($child)) {
                $found = $this->firstGmailPartOfType($child, $mimeType);
                if ($found !== null) {
                    return $found;
                }
            }
        }

        return null;
    }

    /** Gmail codifica el cuerpo en base64 con alfabeto seguro para URL. */
    private function decodeGmailData(mixed $data): ?string
    {
        if (! is_string($data) || $data === '') {
            return null;
        }
        $decoded = base64_decode(strtr($data, '-_', '+/'), true);

        return $decoded === false ? null : $decoded;
    }
}
