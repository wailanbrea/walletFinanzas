# Tech Research — Clasificación de correos financieros

Fecha: 2026-07-21 | Stack: PHP 8.2+, Laravel 12, PHPUnit 11, Gmail API, Microsoft Graph

## Perfil actual

- El backend consulta Gmail con `gmail.readonly` y Microsoft Graph con `Mail.Read`.
- Ambos proveedores ya entregan asunto, remitente y cuerpo decodificado; no se ingiere RFC822/MIME crudo.
- Los montos persistidos usan minor units enteros.
- El clasificador actual es determinista, pero genérico: busca frases financieras y selecciona un importe por proximidad a `total`/`monto`.

## Evidencia real anonimizada

Se auditaron, en modo solo lectura, conexiones activas de Gmail y Microsoft/Hotmail. Se buscaron patrones históricos de PayPal, Google Payments/Wallet, Qik, Banreservas, Banco Popular, BHD y Uber/Uber Eats. Los hallazgos se transformaron en fixtures sin nombres, cuentas, IDs, fechas ni importes reales.

Patrones importantes:

- Un mismo dominio bancario envía consumos, promociones, OTP, estados de cuenta y ofertas.
- Qik incluye monto de compra y balance disponible; seleccionar el primer/último importe sin etiquetas produce errores.
- PayPal puede incluir total en moneda del comercio, pago convertido y tasa de cambio.
- Google Play incluye precio, impuesto, subtotal y total.
- Google Wallet/Google Pay suele generar altas o vinculaciones; el consumo real llega desde el banco.
- Transferencias entre cuentas propias y pagos de tarjeta no deben convertirse automáticamente en gastos, porque duplicarían movimientos.

## Herramientas gratuitas/open source evaluadas

| Herramienta | Licencia | Uso | Veredicto |
|---|---|---|---|
| Symfony Mime | MIT | Manipulación/parsing MIME | Ya está instalado transitivamente (`v7.4.13`). Útil solo si se incorpora RFC822 crudo. |
| php-mime-mail-parser | MIT | Parser de correo PHP 8+ | Activo, pero depende de la extensión `mailparse`; no aporta valor con cuerpos ya decodificados por las APIs. |
| zbateson/mail-mime-parser | BSD-2-Clause | Parser MIME puro PHP | Mejor candidato si se añade ingestión RFC822 sin extensión nativa. No instalar ahora. |
| brick/money | MIT | Dinero y redondeos | Proyecto activo. No resuelve el riesgo actual, que es elegir el campo incorrecto; mantener minor units sin nueva dependencia. |
| Microsoft Presidio | MIT | Detección/anonimización de PII | Útil para pipelines analíticos separados, pero requiere servicio Python/ML y es excesivo para reglas bancarias deterministas. |
| fawazahmed0/exchange-api | CC0-1.0 | Histórico diario USD/DOP sin API key ni límite declarado | Elegido. Ofrece snapshots por fecha en jsDelivr y Cloudflare Pages; ambos mirrors devolvieron valores idénticos para las fechas auditadas. |
| Frankfurter | MIT / datos BCE | Histórico de divisas | Descartado para este caso: la consulta USD/DOP comprobada devolvió 404 y DOP no está disponible. |

## Recomendación de arquitectura

1. Aplicar parsers específicos por remitente y asunto verificados.
2. Exigir etiquetas estructurales del cuerpo (`Monto`, `Comercio`, `Estado`, `Total`, etc.).
3. Rechazar estados declinados, OTP, seguridad, promociones, vinculación de wallet y formatos incompletos.
4. Mantener un fallback genérico únicamente para emisores desconocidos con moneda explícita.
5. Guardar razones auditables y alta confianza solo cuando coinciden remitente + asunto + estructura.
6. Tratar formatos no reconocidos como “sin candidato”; es preferible omitir para revisión que inventar un gasto.
7. Mantener fixtures anonimizados por proveedor y ejecutar regresión al incorporar un formato nuevo.
8. Para correos en USD, consultar automáticamente el snapshot de la fecha local de `occurred_at`, guardar tasa en micros y calcular DOP exclusivamente con enteros.
9. Tratar la conversión de mercado como `historical_estimate`; un monto DOP efectivamente cobrado por el banco tiene mayor autoridad contable.
10. Usar jsDelivr como origen primario y Cloudflare Pages como fallback, validar que la fecha devuelta coincida exactamente y cachear por fecha.

## Decisiones

- No introducir ML/LLM para decidir transacciones: menor explicabilidad y riesgo de falsos positivos.
- No instalar dependencias nuevas en esta fase.
- No inferir correos directos de Uber sin una muestra real; Uber Eats puede identificarse como comercio dentro de alertas bancarias.
- No clasificar transferencias propias ni pagos de tarjeta como gastos automáticos.
- No usar la tasa “latest” para correos históricos ni convertir manualmente en Android; el backend realiza y persiste la conversión al sincronizar.

## Fuentes

- https://developers.google.com/gmail/api — Gmail API y alcance de solo lectura.
- https://learn.microsoft.com/graph/api/resources/message — Microsoft Graph Message/Mail.Read.
- https://github.com/symfony/mime — componente MIME MIT, activo.
- https://github.com/php-mime-mail-parser/php-mime-mail-parser — parser MIT basado en `mailparse`.
- https://github.com/zbateson/mail-mime-parser — parser MIME BSD puro PHP.
- https://github.com/brick/money — manejo de dinero en PHP.
- https://github.com/microsoft/presidio — anonimización local de PII.
- https://github.com/fawazahmed0/exchange-api — API histórica CC0, más de 200 monedas y mirrors documentados.
- https://api.frankfurter.app — alternativa evaluada sin soporte USD/DOP comprobado.
