# Integración AISP — visibilidad de cuentas bancarias y tarjetas

> ⛔ **CONCLUSIÓN (17/07/2026): la sincronización bancaria automática NO es viable para
> República Dominicana.** Salt Edge informó que **no da soporte a RD** (pese a que Banreservas
> aparecía en su widget). Ningún otro agregador cubre RD: Belvo = MX/BR/CO, Plaid = EE.UU.,
> Tink/TrueLayer = Europa. RD no tiene marco de open banking. **Camino adoptado: entrada
> manual + importación de CSV** (extractos del banco), ya soportado en la app.
> El código Salt Edge (Etapa A) queda como demo apagada en producción (`SaltEdgeConfig.isAvailable`,
> DEBUG only) — pendiente decidir si se elimina.
>
> Lo siguiente es el análisis histórico previo a esa conclusión.

> Investigado: 16/07/2026. Fuentes indicadas por el product owner:
> - https://budgetbakers.com/es/products/aisp-open-banking/
> - https://www.saltedge.com/clients/dashboard
>
> Objetivo: que el usuario conecte su banco y la app muestre saldos y movimientos
> de cuentas/tarjetas automáticamente (modelo AISP: Account Information Service Provider,
> solo lectura — nunca iniciar pagos).

## 1. Qué es cada proveedor

### Salt Edge (recomendado para empezar)
- Agregador AISP global. La integración se gestiona desde su **Client Dashboard**
  (saltedge.com/clients/dashboard): se crea una "app" y se obtienen `App-Id` y `Secret`.
- **Modelo de estados** (clave para nuestro plan sin costos):
  - **Pending/Test**: gratis, acceso a **fake providers** (bancos simulados) y sandbox.
    Ideal para construir todo el flujo sin tocar bancos reales ni pagar.
  - **Live**: requiere aprobación/KYC y contrato de pago. Fuera del MVP.
- Flujo técnico típico (API Account Information):
  1. La app crea un `customer` (nuestro uid de Firebase).
  2. Se pide una **Connect Session** → Salt Edge devuelve una URL.
  3. El usuario abre esa URL (Chrome Custom Tab) y se autentica **directamente con su banco**
     — las credenciales nunca pasan por nuestra app ni backend.
  4. Al volver, consultamos `accounts` (nombre, tipo, saldo, moneda) y `transactions`.
  5. Refresh según el plan del proveedor (en test: manual/limitado).

### BudgetBakers AISP (la misma empresa de la app Wallet de referencia)
- Producto B2B de open banking construido sobre la licencia AISP europea de BudgetBakers
  (PSD2). Es la tecnología que usa su propia app Wallet para "Sincronización bancaria".
- Orientado a empresas que quieren conectividad bancaria como servicio (API/branded flow).
- **Cobertura centrada en Europa (PSD2)** — pendiente confirmar si exponen algo para LatAm.
- Sin plan gratuito publicado; el contacto es comercial (formulario). ⚠️ Por verificar en la página.

## 2. Realidad para República Dominicana
- RD **no tiene marco regulatorio de open banking** (no hay APIs bancarias oficiales).
- **PERO Salt Edge sí lista cobertura dominicana, incluido Banreservas** (confirmado por el
  product owner en el dashboard de Salt Edge, 16/07/2026). Al no existir APIs oficiales en RD,
  estas conexiones son del tipo **web/credential-based** (el usuario entrega sus credenciales
  de banca en línea a Salt Edge, que hace el scraping en su infraestructura certificada
  ISO 27001/PCI). ⚠️ Verificar en el dashboard: tipo de conexión exacto, estabilidad y si está
  disponible en estado Test.
- Implicaciones de la vía credential-based:
  - Las credenciales las custodia Salt Edge, nunca nuestra app ni nuestro backend — aceptable,
    pero hay que comunicarlo con total transparencia al usuario en el consentimiento.
  - Más frágil que una API oficial (cambios en la web del banco rompen la conexión).
  - Nuestra regla "sin credenciales reales" aplica **al MVP**: en sandbox se usan fake
    providers; Banreservas real queda para la Etapa B con consentimiento explícito.
- Con esto, el plan gana un objetivo concreto: **piloto Banreservas vía Salt Edge en Etapa B.**

## 3. Plan de integración por etapas (todo detrás de feature flag)

### Etapa A — Sandbox Salt Edge (DEBUG only, costo $0)  ✅ IMPLEMENTADA (16/07/2026)
- [x] Registro en el Client Dashboard (app "Bsolutions.dev", estado Pending; API key
      `wallet-android-sandbox`). App-Id/Secret viven en `local.properties`
      (`saltedge.appId` / `saltedge.secret`) → `BuildConfig`, NUNCA en el repo
- [x] `core/network/SaltEdgeApi.kt` (API v6: customers, connections/connect, accounts,
      transactions) + `SaltEdgeModule` (headers App-id/Secret vía interceptor; logging BASIC
      para no filtrar el Secret)
- [x] `BankSyncRepository` + `BankConnectionEntity`/`BankConnectionDao` (Room v5)
- [x] Pantalla "Sincronización bancaria" real (`SyncSettingsScreen` + `BankSyncViewModel`):
      lista de conexiones, "Conectar banco" → Connect Session en Chrome Custom Tab
      (`return_to = walletfinanzas://saltedge`, deep link en el manifest), "Sincronizar"
      → importa cuentas y movimientos fake a Room con ids `se_*` (idempotente)
- [x] Feature gate: `SaltEdgeConfig.isAvailable = DEBUG && credenciales presentes`;
      sin credenciales la pantalla muestra "Próximamente"
- [x] Pantalla nativa "Encuentra tu banco" (`FindBankScreen`, estilo Wallet BudgetBakers):
      país (DO / sandbox XF) → lista de proveedores desde `GET /providers?country_code=`
      (Banreservas aparece para DO) → "Conectar" crea la Connect Session con
      `provider.code`, así el widget salta directo al login del banco elegido
- [x] Multi-divisa: `MoneyFormat.format(balance, account.currency)` (€/US$/GBP/RD$
      por cuenta) + **Balance Total consolidado** (`core/common/AccountBalances`):
      el total principal solo agrega cuentas en RD$; las demás divisas se muestran
      como subtotales ("Además: €2,009.70 · GBP 2,024.11 · US$-2,019.17") en Dashboard
      y Cuentas. No hay conversión FX (no procede en MVP)
- [x] **Divisa por movimiento** (DB v6): `Transaction.currency` (heredada de la cuenta
      al importar). Ingresos/Gastos y donut del mes cuentan solo movimientos en RD$;
      las filas de movimiento muestran su divisa real (-€11.00, +US$11.00, -GBP 12.89).
      Migraciones 4→5 (tabla bank_connections) y 5→6 (columna currency) registradas,
      sin pérdida de datos. Movimientos importados antes de v6 toman su divisa al re-sincronizar

### Etapa B — Piloto Banreservas real vía Salt Edge

> Estado (17/07/2026): **código de la app 100% listo**. Verificado que el proveedor real
> `banreservas_do` existe y devuelve `connect_url`. La app no distingue banco fake de real:
> misma pantalla "Encuentra tu banco", mismo `provider.code`, mismo import. El único bloqueo
> es el **estado de la cuenta Salt Edge (Pending → Test)**, que es un trámite del owner.

**Datos confirmados del proveedor** (vía `GET /providers?country_code=DO`):
- `code = banreservas_do` · `name = Banreservas` · `country_code = DO`
- `mode = web` → **conexión por credenciales** (el usuario introduce su usuario/clave de
  banca en línea de Banreservas en el widget de Salt Edge; Salt Edge hace el scraping en su
  infraestructura certificada). No es API oficial (RD no tiene open banking) ni PSD2.
- `status = active` · `regulated = false`

**Estado del trámite: SOLICITUD DE TEST ENVIADA — en revisión (17/07/2026).**
Salt Edge confirmó por correo la recepción de la solicitud de upgrade Pending→Test para
la app "Wallet Finanzas BS"; respuesta esperada en ~2 días hábiles a wailandkey@gmail.com.
Application info se completó (Personal finance manager / Account Information / 0-500 usuarios);
Company/KYC completado por el owner. Falta solo la aprobación de Salt Edge.

**Qué falta (acciones del owner en el dashboard de Salt Edge — NO puede hacerlo Claude):**
1. En estado **Pending** (actual) solo se conectan bancos fake (hasta 10 conexiones).
2. Subir a **Test**: completar *Application info* + *Company info* + *Callback URLs* válidos
   en el perfil, y luego la página **"request test access"**. Aprobación en ~2 días hábiles.
   ✅ Hecho el 17/07/2026 — en revisión.
3. **Test** = hasta **100 conexiones a bancos reales**, **gratis**, válido **90 días**.
   Suficiente para el piloto Banreservas sin costo.
4. **Live** (producción sin límites) exige HTTPS en todos los callbacks y es de pago →
   se deja para cuando el piloto valide el mercado (Fase 3 monetización, feature premium).

**Cuando Test esté activo, el piloto es inmediato (sin cambios de código):**
- Abrir "Sincronización bancaria" → "Conectar banco" → País: República Dominicana →
  seleccionar **Banreservas** → el widget abre el login de Banreservas.
- ⚠️ **El usuario introduce sus PROPIAS credenciales** de banca en línea directamente en el
  widget de Salt Edge. Claude NUNCA las escribe, ni pasan por nuestra app/backend.
- Al volver, "Sincronizar" importa cuentas/tarjetas y movimientos reales a Room.

- Cumplimiento: consentimiento explícito y transparente (conexión credential-based custodiada
  por Salt Edge), revocación desde la app, solo lectura (AISP) — nuestra app JAMÁS ve ni
  guarda credenciales bancarias
- La prohibición de 07_SALTEDGE ("no Banreservas real") aplica al MVP; se levanta formalmente
  al entrar en Etapa B con las salvaguardas anteriores

### Reglas duras (heredan de 00_REGLAS_COSTO y 07_SALTEDGE)
- ❌ Credenciales bancarias reales en MVP · ❌ costos recurrentes sin plan de ingresos
- ✅ Todo tras feature flag OFF por defecto · ✅ fake providers solo en DEBUG

## 4. Comparativa rápida

| | Salt Edge | BudgetBakers AISP |
|---|---|---|
| Sandbox gratis | ✅ (fake providers, estado Pending/Test) | ⚠️ no publicado |
| Registro self-service | ✅ dashboard | ❌ contacto comercial |
| Cobertura LatAm/RD | ✅ **Banreservas listado** (credential-based) | Europa (PSD2) |
| Encaja con MVP $0 | ✅ (solo sandbox) | ❌ |
| Camino a producción | Contrato Live (pago) | Contrato B2B |

**Decisión propuesta**: construir la Etapa A con Salt Edge sandbox (es gratis y deja todo el
flujo listo); mantener BudgetBakers como alternativa comercial si algún día se apunta a
usuarios europeos.

> ⚠️ Ítems marcados "por verificar" pendientes de confirmar contra las páginas fuente
> (la verificación web estaba temporalmente caída al redactar; confirmar antes de la Etapa A).
