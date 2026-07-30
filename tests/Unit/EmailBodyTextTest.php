<?php

namespace Tests\Unit;

use App\Services\EmailBodyText;
use PHPUnit\Framework\TestCase;

class EmailBodyTextTest extends TestCase
{
    private EmailBodyText $bodyText;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bodyText = new EmailBodyText;
    }

    public function test_table_cells_do_not_get_glued_together(): void
    {
        // El caso que rompe strip_tags a secas: sin separador quedaria
        // "ComercioSUPERMERCADO NACIONAL" y ninguna regla lo reconoceria.
        $html = '<table><tr><td>Comercio</td><td>SUPERMERCADO NACIONAL</td></tr>'
            .'<tr><td>Monto</td><td>RD$1,250.00</td></tr></table>';

        $text = $this->bodyText->fromHtml($html);

        $this->assertStringContainsString('Comercio | SUPERMERCADO NACIONAL', $text);
        $this->assertStringContainsString('RD$1,250.00', $text);
    }

    public function test_script_and_style_contents_are_dropped(): void
    {
        $html = '<style>.x{color:red}</style><script>var a=1;</script><p>Compra aprobada</p>';

        $text = $this->bodyText->fromHtml($html);

        $this->assertSame('Compra aprobada', $text);
    }

    public function test_entities_and_hard_spaces_become_plain_text(): void
    {
        // El espacio duro sobrevive a la decodificacion y parte los montos.
        $html = '<p>Monto:&nbsp;RD$2&#46;500&#44;00 en Caf&eacute; Sant&oacute;</p>';

        $text = $this->bodyText->fromHtml($html);

        $this->assertSame('Monto: RD$2.500,00 en Café Santó', $text);
    }

    public function test_it_prefers_the_plain_text_part_of_a_gmail_message(): void
    {
        $payload = [
            'mimeType' => 'multipart/alternative',
            'parts' => [
                ['mimeType' => 'text/html', 'body' => ['data' => $this->base64url('<p>version html</p>')]],
                ['mimeType' => 'text/plain', 'body' => ['data' => $this->base64url('version en texto')]],
            ],
        ];

        $this->assertSame('version en texto', $this->bodyText->fromGmailPayload($payload));
    }

    public function test_it_falls_back_to_the_html_part_and_digs_through_nested_parts(): void
    {
        $payload = [
            'mimeType' => 'multipart/mixed',
            'parts' => [
                ['mimeType' => 'application/pdf', 'body' => ['data' => $this->base64url('%PDF')]],
                [
                    'mimeType' => 'multipart/alternative',
                    'parts' => [
                        ['mimeType' => 'text/html', 'body' => ['data' => $this->base64url('<td>Comercio</td><td>Qik</td>')]],
                    ],
                ],
            ],
        ];

        $this->assertSame('Comercio | Qik', $this->bodyText->fromGmailPayload($payload));
    }

    public function test_empty_cells_do_not_leave_a_wall_of_separators(): void
    {
        $html = '<tr><td>Comercio</td><td></td><td></td><td>Qik</td></tr>';

        $this->assertSame('Comercio | Qik', $this->bodyText->fromHtml($html));
    }

    public function test_a_message_without_a_readable_body_returns_null(): void
    {
        $this->assertNull($this->bodyText->fromGmailPayload(null));
        $this->assertNull($this->bodyText->fromGmailPayload(['mimeType' => 'text/plain', 'body' => []]));
        $this->assertNull($this->bodyText->fromGraphBody(null));
        $this->assertNull($this->bodyText->fromGraphBody(['contentType' => 'html', 'content' => '   ']));
    }

    public function test_graph_plain_text_is_kept_as_is(): void
    {
        $body = ['contentType' => 'text', 'content' => "Consumo aprobado\n\n\nTarjeta ****8324"];

        $this->assertSame("Consumo aprobado\nTarjeta ****8324", $this->bodyText->fromGraphBody($body));
    }

    public function test_the_text_is_capped_so_a_long_footer_cannot_flood_it(): void
    {
        $long = 'RD$100.00 '.str_repeat('pie de pagina ', 2000);

        $text = $this->bodyText->fromHtml('<p>'.$long.'</p>');

        $this->assertSame(EmailBodyText::MAX_LENGTH, mb_strlen($text));
        // Lo util esta al inicio, asi que el recorte no se lo lleva.
        $this->assertStringStartsWith('RD$100.00', $text);
    }

    private function base64url(string $value): string
    {
        return strtr(base64_encode($value), '+/', '-_');
    }
}
