# Notificaciones bancarias y motor de categorización

> Plan actualizado el 2 de agosto de 2026. La Fase A está implementada; la Fase B
> tiene un extractor base pendiente de medir con corpus real; y la Fase C está
> implementada con persistencia, deduplicado y bandeja unificada. La siguiente fase
> de producto es la D, el motor de categorización medible.
> Complementa `10_OBTENCION_DATOS.md` (correo, CSV) y reutiliza el pipeline de
> `docs/email-integration/`.

## 1. Qué se quiere

Que un consumo con tarjeta aparezca en la app **sin que el usuario escriba nada**:
el banco manda su push, la app lo lee, saca monto/comercio/tarjeta, lo categoriza y
—si está seguro— lo registra solo. Lo que no esté claro cae en una bandeja de
revisión con un toque para aceptar.

Y que la categorización sea **una sola** para las tres fuentes que ya existen o van
a existir: correo, notificación y CSV.

## 2. De dónde se parte (lo que ya está hecho)

| Pieza | Dónde | Qué hace |
|---|---|---|
| `EmailMailboxScanner` | `app/Services/` | lee buzones Gmail/Microsoft |
| `FinancialEmailExtractor` | `app/Services/` | saca monto, divisa, comercio, últimos 4 dígitos, dirección |
| `DuplicateEmailCandidateDetector` | `app/Services/` | empareja el mismo cargo visto por dos buzones (±72 h, ±3 %) |
| `EmailCandidate` → `EmailCandidateController` | backend | bandeja de pendientes con `categorize`/`dismiss`/`duplicate` |
| `EmailCategorizationRule` | backend | aprende comercio → categoría al aceptar con `learn` |
| `EmailConnectionsViewModel.classify()` | Android | convierte un candidato en `Transaction` con balance, deuda y recurrente |
| `preselectedAccountId()` | Android | elige cuenta por últimos 4 dígitos y divisa compatible |
| `ExpenseCategorizer` + `CategoryRuleStore` | Android | 15 reglas integradas con ids `cat_*` + reglas del usuario en DataStore |

Es decir: el **flujo de revisión y alta ya existe y está probado**. Lo que falta es
una fuente nueva (la notificación) y subir el listón del motor que decide.

### Estado del flujo base de correo (2 de agosto de 2026)

- Al clasificar se puede corregir tipo, monto y fecha del movimiento.
- Tanto un gasto como un ingreso pueden marcarse como fijos y elegir su frecuencia.
- La ocurrencia del correo se registra una sola vez; el plan empieza en la próxima
  fecha válida y usa un identificador determinista para no duplicarse al reintentar.
- "Movimientos planificados" separa gastos e ingresos, con total activo y acciones
  semánticas para pagar o registrar un ingreso.

Esto completa la pieza reutilizable de recurrencia del correo. La captura segura de
la Fase A también está terminada; el siguiente trabajo es la Fase B, extracción
on-device medida contra el corpus real.

## 3. Decisiones de arquitectura

### D1 — La extracción de notificaciones corre en el teléfono, no en Laravel

La notificación llega al dispositivo, hay que responder en segundos, tiene que
funcionar sin datos, y su texto es lo más sensible que maneja la app. Mandarla al
backend añadiría latencia, dependería de conexión y exportaría el aviso crudo del
banco. Se procesa en local con Kotlin.

**Consecuencia:** habrá dos extractores (PHP para correo, Kotlin para push) y eso
se puede desincronizar. Se mitiga en D2.

### D2 — El léxico es un dato compartido, no código duplicado

Los patrones que sí o sí deben coincidir entre las dos fuentes —marcas conocidas,
palabras por categoría, rótulos de comercio, negativos ("preaprobado", "declinado")—
salen a un `lexicon.json` versionado:

- Android lo lee de `assets/` y acepta una versión más nueva servida por el backend.
- Laravel lo lee de `config/` y lo usa `FinancialEmailExtractor`.
- Un solo juego de fixtures (`fixtures/notices/*.json`) se corre en **ambos**
  lenguajes; si un caso pasa en PHP y falla en Kotlin, es un bug, no una diferencia.

La gramática dura (montos, divisas, últimos 4 dígitos) sí se porta a Kotlin: es
lógica, no vocabulario, y ya está bien cubierta por `FinancialEmailExtractorTest`.

### D3 — Una sola bandeja, persistida en Room ✅

Hoy los candidatos de correo viven solo en el backend y se pierden sin conexión.
Se crea `detected_movements` en Room (con `(ownerId, id)` como el resto de tablas,
Room v13) que guarda candidatos de **las dos** fuentes. La pantalla pasa a ser
"Movimientos detectados" y el bloque de correo actual se convierte en una sección.

Beneficio lateral: los candidatos de correo empiezan a sobrevivir sin conexión.

### D4 — El deduplicado cruzado corre donde se cruzan las fuentes: el teléfono ✅

Una misma compra llega como push (segundos) y como correo (minutos). Con D3 las dos
están en Room, así que ahí se emparejan, con la misma regla que ya usa
`DuplicateEmailCandidateDetector` pero afinada:

- push ↔ correo: ventana ±6 h (no 72: el push es inmediato).
- correo ↔ correo entre proveedores: se reconcilia en backend y también en Room para
  cubrir sincronizaciones parciales o llegadas fuera de orden.
- monto comparado en DOP con tolerancia 3 %; si ambos traen últimos 4 dígitos,
  **tienen que coincidir** — dígitos distintos son dos cargos distintos, no un duplicado.
- también se compara contra movimientos ya registrados a mano en las últimas 24 h,
  para no duplicar lo que el usuario ya anotó.

Implementado en Room v15 con un identificador de origen único por propietario,
agrupación canónica y evidencia conservada. Push tiene prioridad sobre correo; una
coincidencia fuerte se muestra una sola vez y los casos basados solo en monto y hora
se mantienen visibles como posibles duplicados. Los movimientos manuales nunca se
ocultan automáticamente. Clasificar un correo cierra todo su grupo canónico.

### D5 — El autoregistro es una decisión con umbral, no el comportamiento por defecto

Registrar solo un gasto equivocado es peor que no registrarlo: ensucia el balance y
el usuario pierde la confianza en la app. Va detrás de flag, apagado, hasta medir.

## 4. El motor de categorización

Devuelve siempre `CategorizationResult(categoryId, confidence, reason)`. El `reason`
es texto para la UI ("por tu regla 'uber'", "porque Netflix suele ir en
Entretenimiento") — sin él, una categoría automática es magia y el usuario no sabe
qué corregir.

Capas, de mayor a menor prioridad:

1. **Regla del usuario** (`CustomCategoryRule`, ya existe) → confianza 100.
2. **Memoria de comercios**: lo que el propio usuario aceptó antes para ese comercio
   normalizado. Tabla `merchant_memory(ownerId, merchant, categoryId, hits,
   lastUsedAt)`. Confianza 70 con un acierto, 95 a partir de tres.
3. **Léxico integrado** (`ExpenseCategorizer`, ids `cat_*`) → 75.
4. **Heurística de recurrencia**: mismo comercio + monto parecido + cadencia mensual
   → hereda la categoría de la vez anterior y suma 10 de confianza.
5. **Sin coincidencia** → `cat_otros`, confianza 0. Siempre a revisión.

La memoria se alimenta **de las correcciones**: aceptar tal cual refuerza, cambiar la
categoría reescribe la entrada. Es lo mismo que hoy hace `learn` en
`EmailCandidateController`, pero local, con contador y para las dos fuentes.

**Regla de autoregistro** — se registra solo si se cumple todo:

- la fuente es una notificación de un emisor de la lista blanca;
- monto, divisa y dirección salieron sin ambigüedad (un `$` pelado no basta, ya lo
  descarta `currencyFor()`);
- la cuenta se resolvió **de forma única** por últimos 4 dígitos;
- la confianza de categoría ≥ umbral (por defecto 85);
- no es duplicado de nada.

Si algo falla, va a la bandeja. Lo autoregistrado nace marcado `autoBooked`, se ve
con un chip "Automático", se deshace con un toque y entra en el resumen diario.

## 5. Fases

### Fase A — Captura y corpus real ✅

`BankNotificationListenerService` (`NotificationListenerService`), pantalla para
conceder el acceso (`ACTION_NOTIFICATION_LISTENER_SETTINGS`, no es un permiso de
manifiesto normal), y una tabla `raw_bank_notices` con paquete, título, texto,
`EXTRA_BIG_TEXT`, `postTime` y hash. **No crea ningún movimiento todavía.**

Incluye una pantalla de diagnóstico que lista lo capturado y deja marcar qué apps
son bancos: así la lista blanca de emisores sale de los bancos reales del usuario y
no de nombres de paquete inventados.

Implementado:

- acceso guiado a `ACTION_NOTIFICATION_LISTENER_SETTINGS` y estado actualizado al
  volver desde Ajustes de Android;
- fuentes descubiertas desactivadas por defecto y lista blanca explícita por usuario;
- detección proactiva, también desactivada, de siete apps instaladas conocidas de
  República Dominicana mediante visibilidad limitada por paquete: Banreservas, Qik,
  Banco Popular, Móvil Banking BHD, BDI App, gnial y Toke; no se solicita
  `QUERY_ALL_PACKAGES` y se excluye la app BHD Digital Key de autenticación;
- filtro de OTP/autenticación antes de cualquier escritura en Room;
- `notification_sources` y `raw_bank_notices` en Room v14, cifradas por SQLCipher,
  aisladas por propietario y con retención máxima de 30 días;
- captura de título, texto, `EXTRA_BIG_TEXT`/líneas, hora y hashes; identificador
  determinista para no duplicar una notificación reemitida;
- diagnóstico local, borrado y exportación JSON anonimizada para construir fixtures;
- el listener no crea `DetectedMovementEntity`, transacciones ni sincroniza el texto.

Validado en el emulador Android con acceso real del sistema, fuente no autorizada,
autorización explícita, captura y deduplicado. La migración 13→14, el aislamiento por
usuario, el descarte de OTP y la anonimización tienen pruebas automatizadas.

*Criterio de salida alcanzado:* los avisos de apps autorizadas aparecen con el texto
largo y pueden exportarse anonimizados. Para sustituir fixtures sintéticos por un
corpus representativo aún hay que usar la app con bancos reales y revisar cada JSON
antes de compartirlo.

### Fase B — Extractor on-device (base implementada; validación pendiente)

`BankNoticeExtractor` en Kotlin: monto, divisa, dirección, comercio, últimos 4
dígitos, con los negativos de `isDefiniteNonTransaction()`. Fixtures de la Fase A +
los casos que ya cubre `FinancialEmailExtractorTest`. Se mide precisión y recall por
emisor y se publica la tabla en `docs/`.

*Sale cuando:* ≥ 95 % de precisión de monto y dirección sobre el corpus real, y cero
falsos positivos sobre avisos que no son transacciones (promociones, OTP, saldos).

### Fase C — Bandeja unificada y deduplicado ✅

Room v13 con `detected_movements`, migración probada, `DetectedMovementsScreen`
reutilizando el sheet de revisión actual, y el deduplicado cruzado de D4.

Implementado:

- `detected_movements`, migración hasta Room v15 y aislamiento por propietario;
- persistencia local de correo, ingestión de pushes autorizados y deduplicado
  Gmail ↔ Microsoft ↔ push ↔ movimiento manual;
- destino principal "Movimientos detectados", con una tarjeta por raíz canónica y
  las evidencias de cada canal identificadas;
- filtro temporal sobre `occurredAt`: Hoy por defecto, Ayer, semana calendario
  actual y mes calendario actual; el contador indica cuántas detecciones quedan
  fuera del período seleccionado;
- cada tarjeta distingue una detección pendiente de confirmar de un movimiento ya
  registrado cuya confirmación remota de correo todavía debe reintentarse;
- resolución explícita de ambigüedades: conservar por separado o unir sin borrar
  la evidencia original;
- alta manual con cuenta, categoría, monto, tipo y fecha corregibles;
- identificador determinista de transacción: reintentar después de un fallo de red
  no vuelve a modificar el saldo;
- si el movimiento se guardó pero un buzón no confirmó, la tarjeta permanece como
  acción pendiente y permite reintentar solo esa confirmación;
- el autoregistro continúa apagado: ninguna detección altera el saldo sin una acción
  explícita del usuario.

*Sale cuando:* una compra que llega por push y por correo aparece una sola vez, y
aceptarla desde cualquiera de las dos deja un único movimiento.

*Criterio alcanzado con pruebas unitarias y Room real.*

### Fase D — Motor de categorización

`Categorizer` con las cinco capas, `merchant_memory`, `reason` en la UI y el
aprendizaje al aceptar/corregir. `ExpenseCategorizer` queda como la capa 3.

*Sale cuando:* sobre el corpus etiquetado, la categoría propuesta acierta ≥ 80 % y
la memoria de comercios sube ese número entre la primera y la tercera corrección.

### Fase E — Autoregistro

Flag `wallet.autoBooking` (apagado), umbral configurable con mínimo seguro,
`autoBooked` en la transacción, deshacer, resumen diario y rollback documentado.

*Sale cuando:* con el umbral por defecto, la precisión de lo autoregistrado es
≥ 98 % sobre un mes de uso real y todo lo autoregistrado se puede deshacer.

### Fase F — Aprendizaje compartido

La memoria de comercios sube al backend (`EmailCategorizationRule` extendida con
`source` y `hits`) para que el segundo dispositivo y el extractor de correo
aprovechen lo aprendido, y `FinancialEmailExtractor` pase a leer el `lexicon.json`
de D2.

## 6. Riesgos y cosas que hay que resolver antes

- **Google Play.** Usar `NotificationListenerService` exige declarar el uso y una
  política de privacidad coherente; el seguimiento de gastos es un caso aceptado,
  pero la ficha se revisa. Hay que redactarlo antes de subir a Internal Testing, no
  después.
- **Privacidad.** El texto crudo del banco es lo más sensible de la app: se guarda
  cifrado (ya hay SQLCipher), se purga a los 30 días, nunca sale del teléfono en las
  fases A–E y jamás va a logs. Sacarlo al backend requeriría un ADR aparte.
- **OTP y códigos.** Cualquier aviso con un código de un solo uso se descarta antes
  de persistirlo. No se guarda ni para depurar.
- **Avisos truncados y repetidos.** Hay que leer `EXTRA_BIG_TEXT`/`EXTRA_TEXT_LINES`,
  y una notificación que se actualiza reposta con la misma `key`: se deduplica por
  `key + hash(texto)`.
- **Rebind tras reinicio y tras revocar el acceso.** El sistema puede desatar el
  servicio; hay que reengancharlo y avisar en Ajustes cuando el acceso se cayó, igual
  que ya se hace con las conexiones de correo revocadas (commit `0e4c059`).
- **Emisores que no traen los 4 dígitos.** Sin ellos no hay cuenta única y por tanto
  no hay autoregistro: se ofrece un mapeo manual "esta app → esta cuenta", que el
  usuario configura una vez.
- **Ojo con `preselectedAccountId()`.** Ya descarta la coincidencia de dígitos si la
  divisa no cuadra; el autoregistro tiene que respetar exactamente ese criterio o
  registrará importes erróneos.

## 7. Lo que NO entra

- Nada de ML ni de servicio externo de clasificación: el léxico + memoria por
  comercio cubre el caso dominicano, y un modelo sin dataset versionado ni baseline
  no se puede evaluar (regla ya escrita en `MASTER_TODO` Fase 13).
- Leer SMS. Requiere permiso restringido de Play con una justificación mucho más
  dura y los bancos de RD ya notifican por push y correo.
- Tocar el flujo de correo existente más allá de persistir sus candidatos en Room.
