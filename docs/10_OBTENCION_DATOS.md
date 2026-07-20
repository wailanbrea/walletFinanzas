# Obtención de datos financieros del usuario — estrategia y mejores prácticas

> Investigado: 16 de julio de 2026. Contexto: app offline-first para República Dominicana, MVP sin costos (ver 00_REGLAS_COSTO).

## Situación del mercado

- **República Dominicana NO tiene marco regulatorio de open banking.** El Banco Central trabaja con la ABA en el desarrollo regulatorio desde 2021, pero no hay APIs bancarias estandarizadas disponibles.
- Los agregadores líderes de LatAm (**Belvo**, **Pluggy**) se concentran en México y Brasil; la cobertura de bancos dominicanos es limitada o nula. Brasil es el único mercado maduro de la región.
- Agregadores globales (Plaid, Tink, TrueLayer, Salt Edge) pueden llegar a tener conectividad parcial, pero son de pago y sin garantía de cobertura RD.

## Decisión de arquitectura (por fases)

| Fase | Vía de obtención de datos | Estado |
|------|---------------------------|--------|
| 1 (MVP) | **Registro manual** (alta rápida de gasto/ingreso/transferencia) | ✅ Hecho |
| 2 | **Importación CSV** del banco: mapeo de columnas + previsualización + deduplicación | En desarrollo |
| 3 | **Salt Edge sandbox** detrás de feature flag premium (default OFF, sin credenciales reales) | Preparado (07_SALTEDGE) |
| Futuro | Open banking RD cuando exista el marco regulatorio | Bloqueado por regulación |

Este enfoque coincide con las apps de referencia respetadas por privacidad (Actual Budget, Lunch Money): el usuario descarga su estado de cuenta y controla cuándo y qué importa, sin ceder credenciales bancarias a terceros.

## Mejores prácticas aplicadas al importador CSV

1. **Deduplicación obligatoria**: firma `fecha + monto + descripción normalizada`; los duplicados se marcan y se excluyen por defecto (re-importar el mismo archivo no duplica movimientos).
2. **Previsualización antes de confirmar**: nada se escribe en la BD hasta que el usuario revisa lo detectado.
3. **Mapeo de columnas flexible**: auto-detección por nombre de encabezado (fecha/date, monto/amount, descripción/description) con corrección manual.
4. **Formatos tolerantes**: separador `,` o `;`, fechas dd/MM/yyyy · yyyy-MM-dd · dd-MM-yyyy, montos con coma o punto decimal, signo o columna de tipo para ingreso/gasto.
5. **Todo local**: el archivo se lee vía Storage Access Framework (sin permiso de almacenamiento global) y nunca sale del dispositivo.

## Seguridad de los datos financieros (backlog priorizado)

- [ ] **Cifrado de la BD Room con SQLCipher** (AES-256): estándar de facto para datos financieros locales. Añadir `net.zetetic:android-database-sqlcipher` + `SupportFactory` en `DatabaseModule`.
- [ ] **EncryptedSharedPreferences / DataStore cifrado** para el perfil y preferencias.
- [ ] **Bloqueo biométrico** (BiometricPrompt) opcional al abrir la app — la pantalla Security ya existe como stub.
- [ ] **FLAG_SECURE** en la Activity para bloquear capturas de pantalla en pantallas con montos (opcional, configurable).
- [ ] **Sin analítica de terceros** que reciba montos o descripciones (cuando entre Crashlytics en Fase 4, filtrar breadcrumbs).
- [ ] Al llegar la sync (Fase 2): TLS + RLS en Supabase, y nunca sincronizar credenciales bancarias (no existen en el modelo).

## Fuentes

- [Open Banking in Dominican Republic — OpenBankingTracker](https://www.openbankingtracker.com/country/dominican-republic)
- [The Status of Open Finance in Latin America in 2025 — Ozone API](https://ozoneapi.com/blog/the-status-of-open-finance-in-latin-america-in-2025/)
- [Financial Aggregators & API Aggregators — OpenBankingTracker](https://www.openbankingtracker.com/api-aggregators)
- [Import Transactions — Lunch Money](https://lunchmoney.app/features/import-transactions/)
- [Actual Budget (local-first, open source)](https://actualbudget.org/)
- [Best Personal Finance Apps for Privacy in 2026 — SenticMoney](https://senticmoney.com/blog/best-personal-finance-apps-privacy-2026)
- [Security Features for Finance Mobile Apps — Glance](https://thisisglance.com/learning-centre/what-security-features-are-critical-for-finance-mobile-app-development)
