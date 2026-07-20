# MASTER TODO — INTEGRACIÓN DE CORREOS

Actualizado: 20 de julio de 2026  
Fuente normativa: `../../../Master Promt de desarrollo.txt`

## Leyenda

- `[ ]` pendiente.
- `[-]` en progreso; debe indicar responsable o evidencia parcial.
- `[x]` completado y verificado.
- `[!]` bloqueado; debe explicar causa, evidencia y siguiente acción.

Una tarea solo se marca `[x]` si el código existe, compila, sus pruebas aplicables pasan y la documentación está actualizada.

## Estado inicial auditado

- [x] Proyecto Android localizado en `WalletFinanzasPersonales/`.
- [x] Arquitectura Android existente identificada: Compose, MVVM, Hilt, Room, Retrofit, WorkManager, Firebase Auth y DataStore.
- [x] Módulo financiero local identificado: cuentas, transacciones, categorías, presupuestos, metas, deudas y pagos planificados.
- [x] Backend Laravel inexistente en el workspace al 20/07/2026.
- [x] Módulo de correo inexistente al 20/07/2026.
- [x] APK debug ensamblada en auditoría del 20/07/2026.
- [!] Suite unitaria bloqueada: `DashboardViewModelTest` no proporciona `UserPreferencesRepository`.
- [!] Repositorio Git sin commit inicial; todos los archivos aparecen sin seguimiento.
- [!] Room usa `fallbackToDestructiveMigration()` y `exportSchema=false`.

---

## Fase 0 — Estabilización y auditoría

### 0.1 Línea base recuperable

- [x] Revisar `git status` y separar artefactos de pruebas/emulador del código fuente. Evidencia 20/07/2026: se identificaron XML/PNG de emulador en raíz y se excluyeron mediante `.gitignore`, sin borrar archivos.
- [x] Completar `.gitignore` para Gradle, IDE, APK, archivos locales, credenciales y capturas temporales. Evidencia 20/07/2026: reglas verificadas para `local.properties`, Firebase, APK, salida Graphify y artefactos UI/emulador.
- [x] Verificar que `local.properties`, secretos y credenciales Firebase privadas no queden versionados. Evidencia 20/07/2026: `git check-ignore -v` confirma `local.properties` y `app/google-services.json`.
- [x] Crear un commit base intencional antes del módulo de correo. Evidencia 20/07/2026: commit raíz `48eef59` (`chore: establish verified Android baseline`).
- [x] Registrar hash del commit base en `IMPLEMENTATION_STATUS.md`. Evidencia: sección de control de versión agregada 20/07/2026.

### 0.2 Calidad Android actual

- [x] Corregir `DashboardViewModelTest` para la dependencia `UserPreferencesRepository`. Evidencia 20/07/2026: contrato `UserProfilePreferences` + fake determinista; prueba focal y suite pasan.
- [x] Ejecutar `gradlew testDebugUnitTest` y registrar cantidad real de pruebas. Evidencia 20/07/2026: 24 pruebas, 0 failures, 0 errors, 0 skipped.
- [x] Ejecutar `gradlew assembleDebug`. Evidencia 20/07/2026: `BUILD SUCCESSFUL` después de la corrección.
- [ ] Ejecutar pruebas instrumentadas de DAO/migración en emulador.
- [ ] Confirmar que transferencias, importación CSV y tarjetas siguen funcionando.
- [ ] Corregir `docs/99_TODO.md` si contradice los resultados reales.

### 0.3 Persistencia y seguridad Android

- [ ] Activar `exportSchema=true` y configurar directorio de schemas Room.
- [ ] Versionar schemas Room existentes o reconstruirlos de manera verificable.
- [ ] Crear pruebas completas para migraciones soportadas.
- [ ] Eliminar `fallbackToDestructiveMigration()` cuando todas las rutas estén cubiertas.
- [ ] Definir reglas de backup/data extraction para excluir BD, claves y preferencias sensibles.
- [ ] Verificar que release no incluya secretos de Salt Edge ni logging sensible.
- [ ] Documentar la decisión sobre el código Salt Edge oculto.

### 0.4 Auditoría documental

- [x] Crear `PROJECT_AUDIT.md` con arquitectura, auth, navegación, modelo financiero, red, persistencia y riesgos. Evidencia: documento creado 20/07/2026.
- [x] Crear `IMPLEMENTATION_STATUS.md` con estado por fase y evidencia. Evidencia: documento creado 20/07/2026.
- [x] Crear `KNOWN_ISSUES.md`. Evidencia: documento creado 20/07/2026.
- [x] Crear `DECISIONS.md` con índice de ADRs. Evidencia: documento creado 20/07/2026.
- [x] Crear `TESTING.md` con comandos y entorno. Evidencia: documento creado 20/07/2026.

### Gate 0

- [x] Android compila. Evidencia 20/07/2026: `./gradlew assembleDebug --console=plain` → `BUILD SUCCESSFUL`.
- [x] Suite unitaria pasa. Evidencia 20/07/2026: `./gradlew testDebugUnitTest --console=plain` → 24 pruebas, 0 failures, 0 errors, 0 skipped.
- [ ] Pruebas instrumentadas críticas pasan.
- [ ] Migraciones soportadas están verificadas.
- [ ] Existe commit base recuperable.
- [ ] Estado documentado coincide con evidencia.

No iniciar Fase 1 hasta completar Gate 0.

---

## Fase 1 — Decisiones arquitectónicas

### 1.1 ADR obligatorios

- [ ] ADR-001: fuentes de verdad — MySQL para correo/candidatos y Room para libro local durante MVP.
- [ ] ADR-002: protocolo saga Android→Room→ACK e idempotencia.
- [ ] ADR-003: Firebase Auth como identidad única del backend.
- [ ] ADR-004: OAuth backend-first, state, PKCE, callbacks y deep link.
- [ ] ADR-005: Docker Compose con MySQL 8 y Redis para desarrollo.
- [ ] ADR-006: cifrado de tokens y rotación de claves.
- [ ] ADR-007: retención y eliminación de contenido.
- [ ] ADR-008: estrategia de errores y contrato API.
- [ ] ADR-009: montos minor units, exponentes y fechas UTC.
- [ ] ADR-010: no usar Firestore en el módulo de correo.

### 1.2 Contratos

- [ ] Definir diagrama de componentes en `ARCHITECTURE.md`.
- [ ] Definir estados de cuenta conectada.
- [ ] Definir estados de mensaje, candidato, confirmación y sync run.
- [ ] Definir matriz de ownership y Policies.
- [ ] Definir códigos de error estables.
- [ ] Definir formato de paginación.
- [ ] Definir encabezado `Idempotency-Key`.
- [ ] Definir DTO financiero que Android importará a Room.
- [ ] Definir recuperación si falla antes o después del ACK.

### Gate 1A

- [ ] Todos los ADR iniciales están aprobados y no se contradicen.
- [ ] No existe una segunda autenticación ni una tercera fuente de datos.
- [ ] El protocolo de confirmación tiene casos de recuperación documentados.

---

## Fase 2 — Fundación del backend

### 2.1 Scaffold

- [ ] Crear `backend/` con Laravel estable y PHP 8.2+.
- [ ] Crear `docker-compose.yml` para MySQL 8 y Redis.
- [ ] Agregar healthchecks de contenedores.
- [ ] Crear `.env.example` sin secretos.
- [ ] Configurar PHPUnit/Pest según convención elegida.
- [ ] Configurar formatter y análisis estático.
- [ ] Crear endpoint público mínimo `/health` sin información sensible.

### 2.2 Infraestructura

- [ ] Configurar conexión MySQL.
- [ ] Configurar Redis.
- [ ] Configurar queue connection Redis.
- [ ] Configurar scheduler.
- [ ] Configurar worker con timeout y reinicio controlado.
- [ ] Configurar logs JSON con `correlation_id`.
- [ ] Configurar manejo global de excepciones y redacción.
- [ ] Configurar rate limiting base.

### 2.3 CI local

- [ ] Añadir comandos reproducibles para instalar, migrar y probar.
- [ ] Ejecutar migraciones en base limpia.
- [ ] Ejecutar test de health check.
- [ ] Ejecutar formatter/análisis estático.
- [ ] Documentar inicio y detención del entorno.

### Gate 2

- [ ] MySQL, Redis, queue y scheduler funcionan.
- [ ] Migraciones en base limpia funcionan.
- [ ] Tests backend base pasan.
- [ ] No hay secretos versionados.

---

## Fase 3 — Firebase Auth y autorización backend

### 3.1 Android

- [ ] Crear proveedor de Firebase ID token renovable.
- [ ] Crear interceptor OkHttp que agregue Bearer token solo al backend propio.
- [ ] No enviar token a hosts de terceros.
- [ ] Manejar usuario no autenticado y token renovado.
- [ ] Mapear 401/403 a estados de dominio claros.

### 3.2 Laravel

- [ ] Integrar verificación server-side de Firebase ID tokens.
- [ ] Crear/migrar usuario por `firebase_uid` único.
- [ ] Validar audiencia, emisor, firma y expiración.
- [ ] Definir estrategia de revocación/caché de claves.
- [ ] Crear middleware de autenticación.
- [ ] Crear Policy base de ownership.
- [ ] Prohibir confiar en `user_id` del request.

### 3.3 Pruebas

- [ ] Token válido autentica.
- [ ] Token ausente devuelve 401.
- [ ] Token expirado devuelve 401 sanitizado.
- [ ] Token de otro proyecto falla.
- [ ] Usuario A no accede a recursos de B.
- [ ] Rate limit funciona.

### Gate 3

- [ ] Toda ruta privada exige Firebase válido.
- [ ] Ownership se prueba en cada recurso inicial.
- [ ] Android maneja sesión ausente/expirada.

---

## Fase 4 — Dominio y base de datos de correo

### 4.1 Migraciones

- [ ] `users` con `firebase_uid` único.
- [ ] `connected_email_accounts`.
- [ ] `email_oauth_states`.
- [ ] `email_messages`.
- [ ] `financial_transaction_candidates`.
- [ ] `email_candidate_confirmations`.
- [ ] `email_classification_rules`.
- [ ] `email_processing_feedback`.
- [ ] `email_sync_runs`.
- [ ] `audit_logs`.
- [ ] Índices, uniques, foreign keys y borrado definidos explícitamente.

### 4.2 Dominio

- [ ] Enums de proveedor, estados, dirección, tipo, error y sync.
- [ ] Value objects para dinero, moneda, confidence y provider IDs.
- [ ] DTOs internos sin Eloquent escapando a capas externas.
- [ ] Máquina de estados para cuenta, candidato y confirmación.
- [ ] Servicios de cifrado con versión de clave.
- [ ] Servicio de auditoría sanitizada.

### 4.3 Tests

- [ ] Migraciones `up/down` o rollback documentado.
- [ ] Constraints evitan mensajes duplicados.
- [ ] Constraints evitan idempotency keys duplicadas.
- [ ] Transiciones inválidas son rechazadas.
- [ ] Tokens quedan cifrados en almacenamiento.
- [ ] Rotación de clave tiene prueba.

### Gate 4

- [ ] Modelo y estados están cubiertos por pruebas.
- [ ] Datos sensibles nunca aparecen en logs o respuestas.

---

## Fase 5 — Gmail OAuth y conexión

### 5.1 Preparación externa

- [ ] Crear/configurar proyecto Google Cloud separado por entorno.
- [ ] Configurar OAuth consent screen.
- [ ] Registrar redirect URI exacta del backend.
- [ ] Solicitar únicamente `gmail.readonly`.
- [ ] Documentar usuarios de prueba y límites.
- [ ] Iniciar checklist de verificación OAuth/política de privacidad.
- [ ] Guardar secretos únicamente en secret manager o entorno seguro.

### 5.2 Backend OAuth

- [ ] Implementar `EmailProviderConnector`.
- [ ] Implementar `GmailConnector`.
- [ ] Crear state criptográficamente seguro, de un uso y con expiración.
- [ ] Implementar PKCE.
- [ ] Crear authorization endpoint.
- [ ] Crear callback backend.
- [ ] Validar state, code, replay y redirect URI.
- [ ] Intercambiar código en backend.
- [ ] Guardar tokens cifrados.
- [ ] Registrar scopes y expiración.
- [ ] Crear reconexión.
- [ ] Crear desconexión y revocación.
- [ ] Auditar conexión/desconexión sin registrar tokens.

### 5.3 Android OAuth

- [ ] Crear pantalla de cuentas conectadas con estado explícito.
- [ ] Solicitar authorization URL al backend.
- [ ] Abrir Custom Tab/navegador seguro.
- [ ] Configurar App Link/deep link de resultado de un uso.
- [ ] Manejar éxito, cancelación, state inválido y error.
- [ ] No recibir ni persistir provider tokens.

### 5.4 Tests

- [ ] Authorization URL contiene scope, state y PKCE correctos.
- [ ] State inválido/expirado/reutilizado falla.
- [ ] Callback cancelado se maneja.
- [ ] Tokens se cifran.
- [ ] Refresh funciona con fake HTTP.
- [ ] Revocación cambia estado.
- [ ] Android muestra connected/error/reconnect.

### Gate 5

- [ ] Gmail conecta y desconecta en entorno de desarrollo.
- [ ] Ningún token llega a Android o logs.
- [ ] Reconexión y cancelación funcionan.

---

## Fase 6 — Gmail sync inicial manual

### 6.1 Sync run y jobs

- [ ] Implementar `StartEmailInitialSyncJob`.
- [ ] Implementar lock Redis por cuenta.
- [ ] Limitar por defecto a 30 días; permitir 90.
- [ ] Definir batch size y paginación Gmail.
- [ ] Crear `email_sync_runs` con correlation ID y métricas.
- [ ] Implementar timeout, attempts y backoff.
- [ ] Evitar ejecución simultánea por cuenta.

### 6.2 Mensajes

- [ ] Listar mensajes por rango.
- [ ] Descargar metadata y contenido mínimo necesario.
- [ ] Normalizar MIME de texto plano/HTML.
- [ ] Sanitizar HTML y convertir a texto.
- [ ] Calcular content hash.
- [ ] Deduplicar por provider ID, internet ID y hash.
- [ ] Aplicar retención a texto normalizado.
- [ ] Manejar mensaje eliminado o inaccesible.
- [ ] No procesar adjuntos todavía; registrar metadata solamente.

### 6.3 Recuperación

- [ ] Reintentar fallos transitorios.
- [ ] Clasificar auth, rate limit, validation, permanent e internal.
- [ ] Permitir reanudar sync incompleto.
- [ ] Sanitizar errores mostrados en Android.

### 6.4 Tests

- [ ] Primera sync crea mensajes únicos.
- [ ] Repetir sync no duplica.
- [ ] Batch interrumpido se reanuda.
- [ ] Rate limit aplica backoff.
- [ ] Cuenta desconectada no sincroniza.
- [ ] Dos cuentas no se bloquean mutuamente.
- [ ] Mismo usuario no ejecuta dos sync de la misma cuenta.

### Gate 6

- [ ] Sync manual limitada funciona de extremo a extremo.
- [ ] Repetición y recuperación no duplican mensajes.
- [ ] Métricas y errores son visibles y seguros.

---

## Fase 7 — Extracción, clasificación y duplicados

### 7.1 Corpus

- [ ] Crear fixtures anonimizados de correos financieros RD/US/EU.
- [ ] Incluir Banreservas, Popular, BHD y Qik solo mediante fixtures/reglas configurables verificadas.
- [ ] Incluir compras, pagos, transferencias, retiros, reembolsos, ingresos y suscripciones.
- [ ] Incluir correos no financieros y casos ambiguos.
- [ ] Versionar expected results.

### 7.2 Extracción

- [ ] Normalizador de remitente/asunto/texto.
- [ ] Catálogo configurable de remitentes.
- [ ] Diccionario ES/EN.
- [ ] Regex DOP/RD$, USD/US$, EUR/€.
- [ ] Extractor de fechas.
- [ ] Extractor de comercio.
- [ ] Extractor de últimos cuatro dígitos.
- [ ] Extractor de referencia/autorización.
- [ ] Resolución de múltiples montos; no elegir siempre el primero.
- [ ] Strategy `FinancialEmailParser`.
- [ ] Parser genérico y parsers específicos justificados por fixtures.

### 7.3 Clasificación

- [ ] Candidate type y direction.
- [ ] Confidence score con razones persistidas.
- [ ] Umbrales configurables.
- [ ] Autoaprobación desactivada.
- [ ] Categoría sugerida mediante reglas existentes.

### 7.4 Duplicados

- [ ] Score por monto, moneda, comercio, fecha, tarjeta y referencia.
- [ ] Ventana temporal configurable.
- [ ] Comparar con candidatos y confirmaciones previas.
- [ ] Marcar, nunca eliminar automáticamente.
- [ ] Explicar coincidencias al usuario.

### 7.5 Tests

- [ ] DOP con coma/punto.
- [ ] USD y EUR.
- [ ] Subtotal vs total.
- [ ] Reembolso e ingreso.
- [ ] Fecha ambigua.
- [ ] Correo sin monto.
- [ ] Falso positivo no crea candidato visible.
- [ ] Duplicado exacto y probable.
- [ ] Score y razones deterministas.

### Gate 7

- [ ] Corpus mínimo completo pasa.
- [ ] Extracción no usa float/double.
- [ ] Candidatos son explicables y deduplicados.

---

## Fase 8 — API móvil y confirmación idempotente

### 8.1 API

- [ ] Listar cuentas conectadas.
- [ ] Detalle de cuenta y sync status.
- [ ] Listar candidatos paginados y filtrables.
- [ ] Detalle de candidato con datos mínimos.
- [ ] Ignorar candidato.
- [ ] Marcar duplicado.
- [ ] Preparar confirmación con `Idempotency-Key`.
- [ ] ACK de importación.
- [ ] Consultar confirmaciones pendientes para recuperación.
- [ ] Form Requests, Resources y Policies en todos los endpoints.

### 8.2 Saga backend

- [ ] Bloquear candidato durante preparación.
- [ ] Revalidar ownership, estado y duplicados.
- [ ] Validar correcciones.
- [ ] Guardar feedback.
- [ ] Crear confirmation `pending_import` en transacción MySQL.
- [ ] Repetir idempotency key devuelve el mismo resultado.
- [ ] ACK repetido es seguro.
- [ ] Auditoría de preparar/importar/fallar.

### 8.3 Room

- [ ] Diseñar migración para `originType` y `originId` en Transaction.
- [ ] Crear índice único de origen externo.
- [ ] Crear DAO transaccional: insertar movimiento + actualizar saldo.
- [ ] Repetir confirmation no duplica ni altera saldo dos veces.
- [ ] Guardar estado de ACK pendiente para reintento.

### 8.4 Tests

- [ ] Ownership en cada endpoint.
- [ ] Validación de filtros/paginación.
- [ ] Confirmación feliz.
- [ ] Edición y confirmación.
- [ ] Idempotency key repetida.
- [ ] Fallo antes de Room.
- [ ] Fallo después de Room y antes de ACK.
- [ ] ACK repetido.
- [ ] Reinstalación/reinicio recupera ACK pendiente.

### Gate 8

- [ ] Confirmación end-to-end crea exactamente un movimiento local.
- [ ] Todo fallo intermedio tiene recuperación probada.

---

## Fase 9 — Android: experiencia completa del MVP

### 9.1 Data/domain

- [ ] DTOs API versionados.
- [ ] Mappers DTO→domain→UI.
- [ ] Repositorio de cuentas de correo.
- [ ] Repositorio de candidatos.
- [ ] Repositorio de configuración.
- [ ] Use cases de conectar, sincronizar, listar, confirmar, ignorar y recuperar ACK.
- [ ] Caché local solo si aporta funcionamiento offline medible.

### 9.2 Presentación

- [ ] Pantalla de cuentas conectadas.
- [ ] Estado loading/success/empty/error/reconnect.
- [ ] Sync manual y progreso.
- [ ] Lista paginada de candidatos.
- [ ] Tarjeta con comercio, monto, fecha, categoría, confianza y duplicado.
- [ ] Detalle seguro del candidato.
- [ ] Edición de monto, moneda, fecha, comercio, categoría, dirección, cuenta y nota.
- [ ] Confirmar, ignorar y marcar duplicado.
- [ ] Recuperación visible de importaciones pendientes.
- [ ] Configuración inicial: rango de sync, notificaciones y procesar adjuntos desactivado.
- [ ] Strings y accesibilidad.

### 9.3 Pruebas Android

- [ ] ViewModels.
- [ ] Use cases.
- [ ] Repositories con fake server.
- [ ] Mappers.
- [ ] Compose UI loading/empty/error/success.
- [ ] Navegación OAuth y retorno.
- [ ] Confirmación y edición.
- [ ] Reconexión.
- [ ] Lista paginada.
- [ ] Reinicio con ACK pendiente.
- [ ] Regresión del libro financiero existente.

### Gate 9 — MVP

- [ ] Usuario conecta Gmail.
- [ ] Ejecuta sync manual.
- [ ] Revisa candidatos.
- [ ] Edita/confirma/ignora.
- [ ] Room recibe exactamente un movimiento y saldo correcto.
- [ ] Reinicios y fallos de red no duplican.
- [ ] Pruebas backend y Android pasan.

No iniciar Gmail incremental hasta completar Gate 9.

---

## Fase 10 — Gmail incremental y Pub/Sub

- [ ] Crear topic/subscription por entorno.
- [ ] Configurar identidad y permisos de publicación Gmail.
- [ ] Implementar endpoint Pub/Sub autenticado/validado.
- [ ] Guardar `historyId` de forma segura.
- [ ] Procesar `history.list` idempotentemente.
- [ ] Manejar history ID expirado con recovery controlada.
- [ ] Implementar `watch` y guardar expiration.
- [ ] Renovar `watch` diariamente.
- [ ] Crear sincronización periódica de respaldo.
- [ ] Manejar notificaciones retrasadas, repetidas, perdidas y fuera de orden.
- [ ] Añadir métricas y alertas.
- [ ] Probar renovación, expiración y recovery.

### Gate 10

- [ ] Incremental funciona sin perder ni duplicar mensajes.
- [ ] Existe fallback periódico y runbook.

---

## Fase 11 — Microsoft Graph

- [ ] Registrar aplicación Microsoft por entorno.
- [ ] Configurar redirect URI y `Mail.Read` delegado.
- [ ] Implementar `MicrosoftGraphConnector`.
- [ ] OAuth state, PKCE, callback, refresh y desconexión.
- [ ] Sync inicial limitada.
- [ ] Delta queries y `deltaLink`.
- [ ] Webhook con `clientState`.
- [ ] Lifecycle notification endpoint.
- [ ] Renovación anticipada de subscriptions.
- [ ] Recuperar missed/removed/reauthorizationRequired.
- [ ] Probar cuenta personal Microsoft.
- [ ] Probar cuenta organizacional cuando exista entorno.
- [ ] Ejecutar suite común de conectores.

### Gate 11

- [ ] Outlook cumple los mismos contratos de seguridad, sync y candidatos que Gmail.

---

## Fase 12 — Adjuntos y OCR

### 12.1 Seguridad de archivos

- [ ] Crear tabla/migración `email_attachments`.
- [ ] Almacenamiento privado fuera de web root.
- [ ] Límite de tamaño configurable.
- [ ] Detección MIME real.
- [ ] Nombres generados y path traversal bloqueado.
- [ ] Rechazo de ejecutables y archivos peligrosos.
- [ ] Hash y deduplicación.
- [ ] Limpieza de temporales.
- [ ] Retención y borrado verificable.

### 12.2 Tika

- [ ] Desplegar Tika como servicio interno no público.
- [ ] Timeouts, límites y health check.
- [ ] Extraer PDF digital.
- [ ] No enviar secretos en logs.
- [ ] Probar PDF corrupto/protegido.

### 12.3 PaddleOCR

- [ ] Desplegar OCR como servicio interno no público.
- [ ] Activar solo cuando Tika no produce texto suficiente o es imagen.
- [ ] Limitar CPU/memoria/tiempo.
- [ ] Procesar JPG/PNG y PDF escaneado.
- [ ] Medir precisión con corpus anonimizado.
- [ ] Manejar fallo sin bloquear todo el mensaje.

### Gate 12

- [ ] Adjuntos válidos se procesan; peligrosos se rechazan.
- [ ] Retención elimina contenido y archivos.
- [ ] OCR tiene métricas y límites operativos.

---

## Fase 13 — Feedback, reglas y automatización

- [ ] Guardar correcciones del usuario.
- [ ] Priorizar reglas del usuario sobre globales.
- [ ] CRUD de reglas con validación segura de patrones.
- [ ] Evitar regex con riesgo de ReDoS.
- [ ] Diccionario de comercios normalizados.
- [ ] Medir precisión, recall y tasa de corrección.
- [ ] Autoaprobación detrás de feature flag y desactivada por defecto.
- [ ] Umbral configurable con límites seguros.
- [ ] Rollback de autoaprobación.
- [ ] No introducir ML sin dataset versionado, baseline y métricas.
- [ ] ADR antes de fastText/Transformer/servicio Python.

---

## Fase 14 — Seguridad, observabilidad y cumplimiento

- [ ] Threat model OAuth, webhooks, adjuntos, multiusuario y notificaciones.
- [ ] Revisión OWASP API.
- [ ] Revisión de Policies/ownership.
- [ ] Revisión de cifrado y rotación.
- [ ] Revisión de logs y PII.
- [ ] Revisión de rate limits.
- [ ] Revisión de SSRF en URLs/adjuntos.
- [ ] Revisión de replay/idempotencia.
- [ ] Revisión de backup y eliminación de usuario.
- [ ] Métricas: cuentas, sync, mensajes, candidatos, duplicados, errores, latencia, OCR y tokens.
- [ ] Alertas por cola, webhooks, tokens y errores persistentes.
- [ ] Dashboard operativo.
- [ ] Procedimiento de respuesta a incidentes.
- [ ] Política de privacidad y términos alineados con Google/Microsoft.

---

## Fase 15 — QA, staging y lanzamiento

- [ ] Entorno staging aislado.
- [ ] Secret manager por entorno.
- [ ] Migración de staging desde cero y sobre versión anterior.
- [ ] Workers y scheduler supervisados.
- [ ] HTTPS y dominios verificados.
- [ ] Pub/Sub y Microsoft webhooks de staging.
- [ ] Pruebas E2E con Gmail real de prueba.
- [ ] Pruebas E2E con Outlook real de prueba.
- [ ] Pruebas de carga y rate limits.
- [ ] Pruebas de caída de Redis/MySQL/proveedor/OCR.
- [ ] Pruebas de retención y borrado.
- [ ] APK release firmada sin secretos.
- [ ] Play Internal Testing.
- [ ] `DEPLOYMENT.md` completo.
- [ ] `ROLLBACK.md` probado.
- [ ] `SECURITY.md` final.
- [ ] `TESTING.md` con evidencia.
- [ ] `KNOWN_ISSUES.md` actualizado.

### Gate final

- [ ] Todos los criterios del Master Prompt aplicables están verificados.
- [ ] Cero secretos expuestos.
- [ ] Cero endpoints privados sin autenticación y Policy.
- [ ] Cero migraciones destructivas no documentadas.
- [ ] Suites Android/backend/E2E en verde.
- [ ] Gmail y Outlook recuperan expiraciones y notificaciones perdidas.
- [ ] Confirmaciones no duplican movimientos.
- [ ] Adjuntos y OCR cumplen límites de seguridad.
- [ ] Despliegue, monitoreo y rollback están probados.

---

## Registro de ejecución

Cada sesión debe añadir una entrada breve en `IMPLEMENTATION_STATUS.md`:

```text
Fecha:
Objetivo:
Tareas del TODO:
Archivos modificados:
Migraciones:
Pruebas ejecutadas:
Resultado:
Riesgos/pedientes:
Siguiente tarea desbloqueada:
```
