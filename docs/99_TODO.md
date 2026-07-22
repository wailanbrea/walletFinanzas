# Plan de desarrollo — Wallet Finanzas Personales

> Actualizado: 22 de julio de 2026.
> Referencia visual: app "Wallet by BudgetBakers" (drawer verde, tarjetas, píldora azul de selección).
> Regla de oro: servicios con costo (hosting Laravel, correo, OAuth, Salt Edge) siempre requieren presupuesto/configuración explícita (ver 00_REGLAS_COSTO).

---

## Estado actual (resumen de la revisión de julio 2026)

- **Arquitectura**: Clean Architecture + MVVM (domain / data / presentation), Hilt, Room offline-first, Navigation Compose. Sólida para el alcance actual. `./gradlew assembleDebug` compilaba OK al 12/07/2026.
- **Hecho**: design system M3, ~15 pantallas navegables, alta rápida de gasto/ingreso desde el dashboard, DAOs y repositorios de Account/Category/Transaction/Budget, menú lateral estilo Wallet con paleta verde de marca.
- **Deuda técnica detectada en la revisión**:
  1. Hay código duplicado en la carpeta raíz `app/` del workspace (Goal, RecurringPayment, BankConnection, SyncState, UserProfile con sus DAOs/mappers/repos) que **no está dentro del proyecto Gradle** `WalletFinanzasPersonales/`. Migrarlo o eliminarlo para tener una sola fuente de verdad.
  2. Datos simulados en UI: tendencia "+2.4%", "Gastos por Categoría" con porcentajes fijos y donut decorativo.
  3. Formatos de moneda hardcodeados (`RD$`, `DOP $`) en vez de usar `CurrencyCode`/`Money`.
  4. Textos en español hardcodeados en Kotlin en vez de `strings.xml` (bloquea i18n).
  5. Login/registro navegan sin autenticación real; sin manejo de sesión.
  6. Sin tests de ningún tipo.
  7. Ítems del drawer sin funcionalidad: Premium, Inversiones, Pagos planificados, Deudas y Metas usan `PlaceholderScreen`.

---

## Fase 1 — Núcleo local pulido (v1.0) 🔴 prioridad alta

Objetivo: app 100% funcional offline con la estética Wallet, sin datos falsos.

- [x] Drawer idéntico a Wallet: header degradado verde + avatar, píldora azul del ítem activo, badge "Nuevo" en Inversiones, Estadísticas expandible
- [x] Paleta de marca: verde primario, verde ingresos (secondary), azul acento (tertiary)
- [x] Unificar código: Metas y Pagos planificados se reimplementaron dentro del proyecto Gradle; el `app/` raíz duplicado quedó renombrado a `_backup_app_duplicado/` (contiene borradores de Fase 2: BankConnection/SyncState/UserProfile; borrar cuando arranque la Fase 2) — 16/07/2026
- [x] Pantallas reales para Metas (progreso de ahorro + aportes), Pagos planificados (pagar ahora + reprogramación) y Deudas (yo debo / me deben + abonos) — 16/07/2026, Room v2 con entities `goals`, `planned_payments`, `debts`
- [x] Transferencias entre cuentas con validación de saldo; los movimientos ahora actualizan el balance de la cuenta (bug corregido: antes ingresos/gastos no afectaban el saldo) — 16/07/2026
- [x] Estadísticas con datos reales de Room: donut real (Canvas, componente `DonutChart` compartido) en Dashboard y Reportes; barras de últimos 6 meses con escala dinámica y filtro mes+año; tendencia real de gasto vs mes anterior (se eliminaron el "+2.4%" falso y los porcentajes fijos) — 16/07/2026
- [x] Detalle/edición de transacción (sheet con editar monto/categoría/nota y eliminar, revirtiendo el efecto en el balance) y de cuenta (editar nombre/tipo, eliminar con confirmación) — 16/07/2026
- [x] Detalle/edición de presupuesto (tocar tarjeta → editar límite / eliminar; bugs corregidos: el gasto sumaba todo el histórico en vez del mes en curso, y el diálogo de nuevo presupuesto solo ofrecía categorías que ya tenían uno) — 16/07/2026
- [x] CRUD completo de categorías (pantalla real con selector de icono y color; antes era un stub vacío; `categoryIcons` ampliado a 12 iconos) — 16/07/2026
- [x] Formato de moneda centralizado en `core/common/MoneyFormat` (eliminados todos los `RD$`/`DOP $` hardcodeados de las pantallas); multi-moneda pendiente para cuando CurrencyCode esté por cuenta — 16/07/2026
- [x] Mover textos a `strings.xml` — **COMPLETO 16/07/2026**: ~220 strings extraídos de TODAS las pantallas (navegación, drawer, splash, dashboard, alta rápida, transferencias, Metas, Deudas, Pagos planificados, Categorías, Importar CSV, Cuentas, Registros, Presupuestos, Reportes, Perfil, Ajustes, Seguridad, LockScreen y prompt biométrico). Formatos parametrizados `%1$s`; la pantalla de Movimientos pasó a titularse "Registros" (consistente con drawer y bottom bar). Base lista para `values-en/`
- [x] Arranque offline-first: splash de marca con degradado verde y auto-navegación directa al Dashboard; Laravel Sanctum protege únicamente recursos remotos — actualizado 20/07/2026.
- [x] Perfil local real: nombre, correo y nombre del wallet en DataStore (`UserPreferencesRepository`), editables desde Perfil; el drawer los lee vía `MainViewModel` (ya no hay nombre hardcodeado) — 16/07/2026

**Criterio de salida**: gestionar cuentas, registros, presupuestos, metas y deudas sin conexión y sin ningún dato simulado en pantalla.

## Fase 2 — Cuenta y sincronización (v1.1)

- [x] **Decisión reemplazada 20/07/2026:** Firebase Auth fue retirado. Registro, login, recuperación, sesión cifrada y logout usan Laravel Sanctum conforme a ADR-003; los datos locales offline se conservan.
- [x] Sincronización multidispositivo del dominio MVP: cuentas, movimientos, categorías, presupuestos, metas, deudas y pagos planificados tienen contrato Laravel/Room autenticado, idempotencia por ID de cliente, aislamiento por usuario y tombstones — 22/07/2026. Falta únicamente la validación operativa final con dos dispositivos físicos antes de publicar.
- ~~Supabase: tablas + RLS + sync incremental~~ — eliminado del stack
- [x] Núcleo sync Android↔Laravel idempotente para cuentas, movimientos y categorías; Room continúa como fuente local. Categorías se suben antes que movimientos y propagan tombstones entre dispositivos — 22/07/2026.
- [x] **Aislamiento local por usuario (Room v9):** todas las tablas usan clave compuesta `(ownerId, id)`; invitado y usuarios autenticados tienen particiones independientes. Perfil, preferencias y reglas de categorización también se separan por propietario. Los datos existentes se conservan como invitado y se reclaman transaccionalmente al primer login — 22/07/2026.
- [x] **Sync de planificación financiera:** presupuestos, metas, deudas y pagos planificados se suben y descargan con ownership Laravel, cursor pagination, validación de cuenta/categoría, actualizaciones idempotentes y tombstones — 22/07/2026.
- [ ] Validación manual final: mismo usuario en dos dispositivos físicos, edición alternada y propagación de tombstones sin duplicados.
- [x] Importación CSV completa: selector SAF, parser tolerante (separador `,`/`;`, comillas, fechas dd/MM/yyyy·yyyy-MM-dd·dd-MM-yyyy, montos con coma/punto/paréntesis contable), auto-detección y mapeo manual de columnas, previsualización con estados (válida/duplicada/inválida), deduplicación por firma fecha+monto+descripción, cuenta destino y ajuste de balance en un solo paso — 16/07/2026. Estrategia documentada en 10_OBTENCION_DATOS.md
- [x] Backup manual: "Exportar datos (CSV)" en Ajustes con SAF (fecha, descripción, monto con signo, tipo, cuenta y categoría; escape RFC 4180) — verificado end-to-end en emulador 16/07/2026. El restore es el importador CSV ya existente
- [x] Pulido visual global: donut con barrido animado, barras de progreso animadas (Metas/Deudas/Presupuestos), `GradientSummaryCard` también en Cuentas — 16/07/2026
- [x] **BD cifrada con SQLCipher** (AES-256): `net.zetetic:sqlcipher-android:4.7.2` (compatible con páginas de 16 KB de Android 15+; las 4.5/4.6 disparaban el aviso de compatibilidad), passphrase aleatoria de 256 bits en EncryptedSharedPreferences (Keystore), migración destructiva única de la BD sin cifrar (beta) — 16/07/2026
- [x] **Bloqueo biométrico opcional**: toggle en Seguridad (deshabilitado con aviso si el dispositivo no tiene huella/PIN), `LockScreen` con degradado de marca y BiometricPrompt (huella/rostro/PIN) al abrir; MainActivity ahora extiende FragmentActivity — 16/07/2026
- [ ] Seguridad pendiente: filtrado de breadcrumbs cuando entre Crashlytics. `FLAG_SECURE` opcional ya está disponible desde Seguridad (bloquea capturas y la vista previa en Recientes) — 16/07/2026

**Criterio de salida**: mismos datos en dos dispositivos con el mismo usuario; importar el CSV de un banco real sin duplicados.

## Fase 3 — Monetización y banca (v1.2)

- [ ] Pantalla Premium real (paywall) con Google Play Billing
- [ ] Salt Edge sandbox detrás de flag premium (ver 07_SALTEDGE_PREPARACION; default OFF)
- [ ] Inversiones: portafolio manual primero, cotizaciones por API después
- [ ] Reglas automáticas de categorización
- [x] Notificaciones locales (WorkManager cada 12h, HiltWorker, canal "Pagos planificados", permiso POST_NOTIFICATIONS en Android 13+, tap abre la app): pagos planificados vencidos o que vencen en 24h + **presupuestos excedidos del mes** (excedente en RD$). Requirió subir Hilt 2.52→2.55 (metadatos Kotlin 2.1) — 16/07/2026. Falta prueba visual de la notificación en emulador
- [x] Arranque sin flash blanco: `windowBackground` del tema = degradado de marca (drawable `splash_window_background`), el primer frame ya es verde — 16/07/2026

### Cambios del product owner (16/07/2026) — revisados, mejorados y verificados en emulador
- [x] **Rediseño del Dashboard estilo Wallet**: header verde (`primary`) con hamburguesa que abre el drawer + campana, y pestañas "Cuentas | Presupuesto y Objetivos"; **bottom bar eliminada** (la navegación ahora es drawer + tabs). Nueva paleta con primary verde `#2E7D46`
- [x] **Cuentas y tarjetas**: nuevos tipos `DEBIT_CARD`/`CREDIT_CARD` con render de **tarjeta visual** (degradado morado=crédito / verde-azul=débito, `•••• 1234`); campo `institutionName` con dropdown del **catálogo local de entidades RD** (`FinancialInstitutions`: Banreservas, Popular, BHD, APAP, etc. — offline, sin costo, NO habilita sync bancaria) y `cardLastFour` (solo 4 dígitos, nunca el número completo). Room → v4
- [x] **País financiero** en Perfil (RD / Otro) persistido en DataStore; filtra el catálogo de entidades al crear cuentas
- [x] **Bloqueo de capturas de pantalla** (FLAG_SECURE) con toggle en Seguridad — cierra el pendiente de seguridad; aplicado reactivamente en MainActivity vía SideEffect

### Correcciones de la revisión (16/07/2026)
- [x] Status bar: `window.statusBarColor` está **deprecado/ignorado en Android 15+** → migrado a `enableEdgeToEdge()`; eliminado el SideEffect duplicado de `WalletTheme` que competía con MainActivity (causaba status bar clara con iconos blancos); quitado `windowInsets(0)` del TopAppBar del dashboard (se dibujaba bajo el reloj); el Scaffold raíz usa `WindowInsets(0)` y cada pantalla gestiona sus insets
- [x] Bug: cuentas tipo `SAVINGS` mostraban "Tarjeta de Crédito" en la lista → "Ahorros"
- [x] Tipos de tarjeta añadidos a `EditAccountDialog` (antes editar una tarjeta forzaba a convertirla en banco/efectivo) y al subtítulo del detalle de cuenta (mostrando también la entidad)
- [x] Strings hardcodeados del dropdown de entidad → recursos (`accounts_institution_label/other`); chips de país del Perfil ahora en fila horizontal
- [x] **Flecha "volver" muerta en secciones principales** (Registros, Cuentas, Presupuestos, Reportes): llegan desde el drawer con la pila de navegación vacía, así que `popBackStack()` no hacía nada → ahora navegan a Inicio (`navigateHome` con restoreState). Verificado: Presupuestos → flecha → Inicio ✓
- [x] **Títulos duplicados eliminados**: Presupuestos (barra + header interno decían "Presupuestos"), Reportes ("Reportes" + "Reportes Analíticos"; se conservó el subtítulo y el selector Mensual/Anual), Cuentas ("Mis Cuentas" + sección "Cuentas")
- [x] `windowInsets(0.dp)` retirado de los TopAppBar de las 4 secciones principales (mismo solapamiento con el reloj que tenía el dashboard en edge-to-edge)
- [x] **Header verde en TODAS las pantallas**: nuevo helper `walletTopBarColors()` (presentation/common/WalletTopBar.kt, único punto de verdad) aplicado a las 10 pantallas que seguían con barra blanca o sin colores (Deudas, Metas, Pagos planificados, Categorías, Importar CSV, Notificaciones, Perfil, Seguridad, Ajustes, Sincronización y el formulario de nuevo movimiento); iconos de status bar siempre claros. Verificado en Deudas y Registros — 16/07/2026
- [x] **Pestaña "Presupuesto y Objetivos" del dashboard** (construida por el product owner, verificada): presupuestos activos con barra de progreso animada y % consumido (tocar → editar/eliminar) + sección de metas con GoalCard y aportes; botones + y empty states para crear desde la propia pestaña. Sus ~15 textos extraídos a strings.xml el 16/07/2026 (pestañas, secciones, empty states, "%% consumido")
- [x] **Navegación hamburguesa vs flecha unificada** (patrón Wallet): las 8 secciones del drawer (Inicio, Registros, Cuentas, Presupuestos, Reportes, Pagos planificados, Deudas, Metas) muestran **hamburguesa** que abre el menú — son destinos hermanos, no hay "atrás". La **flecha** queda solo en pantallas hijas apiladas (Ajustes, Perfil, Categorías, Seguridad, Sincronización, Importaciones, Notificaciones, detalle de cuenta, formulario de movimiento, auth), donde `popBackStack()` sí funciona. Metas/Deudas/Pagos entraron a `mainRoutes` (píldora activa en el drawer + gesto de borde habilitado). Verificado: Registros ☰ → drawer con "Registros" resaltado — 16/07/2026
- [x] **Visibilidad de cuentas bancarias/tarjetas (AISP) — Etapa A implementada** (16/07/2026): sandbox Salt Edge (API v6, fake providers, $0, DEBUG only). Cliente `SaltEdgeApi` + `BankSyncRepository` + Room v5 (`BankConnectionEntity`) + pantalla "Sincronización bancaria" real (Conectar banco → Chrome Custom Tab → Sincronizar → importa cuentas/movimientos fake con ids `se_*`). Credenciales en `local.properties`, gate `SaltEdgeConfig.isAvailable` (DEBUG + credenciales)
- [x] **Pantalla nativa "Encuentra tu banco"** (17/07/2026): selector país/banco (providers API), preselección con `provider.code` → widget salta directo al login del banco
- [x] **Multi-divisa completo** (17/07/2026, DB v6): saldo por cuenta en su moneda, Balance Total consolidado (solo RD$ + subtotales "Además: …"), y `Transaction.currency` (Ingresos/Gastos del mes solo RD$, filas con su divisa real)
- [x] **Modo privacidad (desenfocar montos)** (17/07/2026): toggle global de ojo en las barras de Inicio y Cuentas (preferencia `balancesHidden` en DataStore, persistente). **Desenfoca** (Modifier.blur, no puntitos — se ve que hay cifra pero borrosa): **Balance Total + subtítulo de divisas**, **Ingresos** del mes y **saldos de cuentas** (lista, tarjeta y detalle). Gastos y **movimientos quedan normales**. Helper reutilizable `presentation/common/privacyBlur(hidden, radius)`. Nota: Modifier.blur solo rinde en Android 12+; en versiones previas degrada a nítido
- [x] **Toggle protegido con biometría** (17/07/2026): al tocar el ojo se pide autenticación (huella/rostro con respaldo a PIN del dispositivo) antes de cambiar la visibilidad — helper `presentation/common/authenticateBiometric` (BiometricPrompt, BIOMETRIC_WEAK ‖ DEVICE_CREDENTIAL). Si el dispositivo no tiene ninguna seguridad configurada, no bloquea la acción. Verificado en emulador (PIN de prueba): tocar ojo → prompt del sistema → PIN correcto → revela; se aplica en ambos sentidos
- [x] ~~**Etapa B — Piloto Banreservas real**~~ **DESCARTADO (17/07/2026)**: Salt Edge informó que **no da soporte a República Dominicana** (Banreservas aparecía en el widget pero no es utilizable). Ningún agregador cubre RD (Belvo=MX/BR/CO, Plaid=EE.UU., Tink/TrueLayer=Europa; RD sin open banking). **Camino adoptado: entrada manual + importación de CSV**. El código Salt Edge (Etapa A) se **deja en el repo pero oculto**: la entrada "Sincronización bancaria" del drawer ahora se inserta condicionalmente solo si `SaltEdgeConfig.isAvailable` (DEBUG + credenciales), así el usuario final de producción no la ve, pero sigue disponible para pruebas en desarrollo. Verificado en emulador (debug la muestra; producción la oculta). Ver **11_AISP_INTEGRACION.md**

### Corrección transversal de categorías (22/07/2026)

- [x] Motor único de autocategorización con IDs deterministas (`cat_*`), independiente del nombre visible; renombrar una categoría predeterminada ya no rompe las reglas integradas.
- [x] Alta completa y edición de movimientos permiten "Sin categoría (automática)". Las reglas personalizadas válidas tienen prioridad y las referencias eliminadas se ignoran de forma segura.
- [x] CSV, sincronización bancaria, pagos planificados y revisión de candidatos de correo usan el catálogo real y el mismo criterio de categorización; correo ya no acepta categorías de texto libre.
- [x] Borrado coherente: las reglas personalizadas asociadas se eliminan, los tombstones impiden que categorías predeterminadas borradas reaparezcan al reiniciar y las reglas huérfanas históricas se limpian al abrir su pantalla.
- [x] Dashboard y Reportes agrupan movimientos vacíos/huérfanos en "Sin categoría" sin perder montos; Presupuestos conserva visibles los asociados a una "Categoría eliminada", incluso en alertas.
- [x] Sync Android↔Laravel valida IDs recibidos/enviados para no crear referencias locales huérfanas.
- [x] Validación: `assembleDebug`, 78 pruebas unitarias (0 fallos; 1 omitida), 15 pruebas instrumentadas en Pixel 9 Pro API 37 y `lintDebug` con **0 advertencias / 0 errores**. Smoke/UI adicional en BlueStacks Pie 64: instalación, arranque, drawer, Más opciones y Categorías sin crash — 22/07/2026.
- [x] **Catálogo remoto de categorías:** API Laravel autenticada con `client_id` aislado por usuario, cursor pagination, límite de 200 activas, tombstones y validación de referencias en transacciones. Android Room v8 marca el catálogo existente para la primera subida y sincroniza cambios antes de los movimientos — 22/07/2026.

## Fase 4 — Calidad y lanzamiento

- [x] Tests automatizados — **78 unitarios ejecutados, 0 fallos y 1 omitido** (`testDebugUnitTest`, 22/07/2026), incluyendo regresiones del motor de categorías, aislamiento por propietario y tombstones; **15/15 instrumentados** en Pixel 9 Pro API 37; **62 pruebas Laravel / 401 aserciones**; `lintDebug` en **0 advertencias / 0 errores**, `assembleDebug` y Laravel Pint en verde. La prueba de integración Android→Laravel también pasa cuando se habilita con `WALLET_API_INTEGRATION_URL`.
- [ ] Configurar el endpoint HTTPS definitivo en `wallet.releaseApiBaseUrl` antes del build firmado. El build debug usa el backend local mediante `10.0.2.2:8001`; release falla de forma segura contra un dominio inválido mientras no exista una URL de producción explícita.
- [x] Accesibilidad — 16/07/2026: auditoría completa de `contentDescription` (interactivos con recurso traducible como "Mostrar u ocultar contraseña"; decorativos a `null` para no ensuciar TalkBack) y **objetivos táctiles ≥48dp** vía `minimumInteractiveComponentSize()` en los 5 text-buttons pequeños ("Ver todas", "Aportar", "Registrar abono", "Pagar ahora", "¿Olvidé mi contraseña?"). Contraste ya cubierto por el tema M3 (blanco sobre verde #2E7D46 ≈ 5.9:1). **Iniciado 16/07/2026**: pruebas de `MoneyParser`, `CsvParser` y efectos de saldo en `TransactionsViewModel`; falta cubrir Dashboard, Budgets, repositorios Room e importación end-to-end.
- [ ] Tests de UI de flujos críticos (alta de gasto, transferencia)
- [ ] Crashlytics + analítica básica
- [ ] Accesibilidad: contentDescription, tamaños táctiles, contraste
- [ ] Build firmado + Play Store internal testing (máx. 25 usuarios beta)

---

## Límites de costo (MVP gratuito)

- Usuarios beta: máx. 25 · Transacciones: 3.000/usuario (50.000 global)
- Cuentas: 20/usuario · Categorías: 200/usuario · Imágenes: máx. 300 KB c/u
- Salt Edge desactivado por defecto:

```kotlin
val bankSyncEnabled = prefs.get<Boolean>("bank_sync_enabled") ?: false // default = false
```

## Reglas transversales

- Toda pantalla nueva usa `core/designsystem`; nada de colores hex inline (excepción: los colores calcados del drawer de referencia, centralizados en `AppDrawer.kt`).
- Español como idioma base, preparado para i18n desde Fase 1.
- Actualizar este archivo al cerrar cada fase o corregir un error crítico.
