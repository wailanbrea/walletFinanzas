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

## Pruebas instrumentadas pendientes

```bash
./gradlew connectedDebugAndroidTest --console=plain
# o pruebas críticas aisladas cuando exista emulador/dispositivo:
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bsolutions.wallet.core.database.WalletDatabaseMigrationTest --console=plain
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.bsolutions.wallet.data.local.dao.TransactionDaoTransferTest --console=plain
```

## Política de pruebas para el módulo de correo

1. Unitarias deterministas para DTOs, mappers, estados, idempotencia y reglas financieras.
2. Feature/integration tests Laravel para Firebase, Policies, OAuth state/PKCE, cifrado, locks y transacciones MySQL.
3. Fakes HTTP para Gmail/Microsoft; los tests nunca usan tokens reales.
4. Pruebas Android para repositorios, ViewModels, navegación OAuth y recuperación de ACK.
5. E2E real únicamente en entorno aislado, con cuentas de prueba y secretos fuera del repositorio.

## Criterio de Gate 0

El gate sólo se aprobará cuando build, tests unitarios, pruebas instrumentadas críticas, rutas de migración soportadas, commit base y documentación reflejen la evidencia real.
