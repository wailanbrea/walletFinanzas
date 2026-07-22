# Pruebas y entorno — Integración de correos

## Requisitos locales

- JDK 21 y Android SDK configurados.
- Un emulador o dispositivo Android para `connectedAndroidTest`.
- Credenciales locales sólo en `local.properties`; nunca se agregan al control de versiones.

## Línea base ejecutada el 2026-07-20

```bash
./gradlew testDebugUnitTest --tests 'com.bsolutions.wallet.presentation.dashboard.DashboardViewModelTest' --console=plain
# BUILD SUCCESSFUL

./gradlew testDebugUnitTest --console=plain
# BUILD SUCCESSFUL

./gradlew assembleDebug --console=plain
# BUILD SUCCESSFUL (antes del último cambio de fixture; repetir al cerrar la tarea)
```

## Pruebas instrumentadas ejecutadas el 2026-07-22

```bash
./gradlew connectedDebugAndroidTest --console=plain
# BUILD SUCCESSFUL: 15/15 en Pixel 9 Pro API 37
# Pruebas críticas aisladas:
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bsolutions.wallet.core.database.WalletDatabaseMigrationTest --console=plain
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bsolutions.wallet.data.local.dao.TransactionDaoTransferTest --console=plain
```

BlueStacks 5.22 Pie 64 se validó como dispositivo de smoke/UI: instalación, arranque, drawer, Más opciones y Categorías sin crash ni errores en logcat. No se usa como runner instrumentado porque Android Gradle UTP deja su conexión ADB offline durante `AndroidTestDeviceInfoPlugin`, antes de ejecutar tests; la misma suite pasa completa en el AVD oficial.

## Cobertura agregada para Room v9 y sync financiero — 2026-07-22

- Migración 8→9 validada contra schemas Room: conserva cuentas/presupuestos, asigna propietario `guest` y marca los nuevos recursos para primera sincronización.
- `WalletOwnerIsolationTest`: IDs iguales pueden existir en particiones distintas; el login reclama invitado y el logout deja inaccesible la partición autenticada.
- `CategorySyncRepositoryTest`: push y pull/tombstones de presupuestos, metas, deudas y pagos planificados.
- Backend `FinancialPlanningSyncApiTest`: autenticación, ownership, referencias cruzadas, upsert idempotente, listado y tombstones.
- Integración Retrofit→Laravel real contra `127.0.0.1:8001`: registro/login, cuenta, categoría, movimiento y CRUD/tombstones de los cuatro recursos de planificación; PASS tras aplicar la migración MySQL.
- Suite Laravel completa: 62 pruebas, 401 aserciones; Pint en verde.

## Política de pruebas para el módulo de correo

1. Unitarias deterministas para DTOs, mappers, estados, idempotencia y reglas financieras.
2. Feature/integration tests Laravel para Sanctum, ownership, OAuth state/PKCE, cifrado, locks y transacciones MySQL.
3. Fakes HTTP para Gmail/Microsoft; los tests nunca usan tokens reales.
4. Pruebas Android para repositorios, ViewModels, navegación OAuth y recuperación de ACK.
5. E2E real únicamente en entorno aislado, con cuentas de prueba y secretos fuera del repositorio.

## Criterio de Gate 0

El gate sólo se aprobará cuando build, tests unitarios, pruebas instrumentadas críticas, rutas de migración soportadas, commit base y documentación reflejen la evidencia real.
