# Estado de implementación — Integración de correos

## Estado global

- **Fase activa:** conexión OAuth backend-first y administración de cuentas de correo.
- **Identidad canónica:** Laravel Sanctum, conforme a ADR-003 aprobado el 20/07/2026.
- **Backend Laravel:** implementado con panel administrativo, auth, recuperación, OAuth Gmail/Microsoft y aislamiento por usuario.
- **Módulo Android de correo:** implementado con estado, conexión por Custom Tabs, retorno/refresh y desconexión.
- **Producción:** bloqueada hasta configurar dominio HTTPS, mailer y credenciales OAuth reales. La suite Android instrumentada ya pasa 15/15 en Pixel 9 Pro API 37.
- **Aislamiento local:** Room v9 y DataStore separan invitado/usuarios mediante propietario activo; el logout cambia de partición sin borrar los datos cifrados del usuario.
- **Sync MVP:** cuentas, movimientos, categorías, presupuestos, metas, deudas y pagos planificados cuentan con push/pull y aislamiento backend por usuario.

## Control de versión

- **Commit base recuperable:** `48eef59` — `chore: establish verified Android baseline` (20/07/2026).
- Credenciales OAuth/Salt Edge, `local.properties`, outputs de build y artefactos de emulador permanecen fuera del commit por `.gitignore`.

## Cobertura documental por fase

**Completos para Fase 0:** `PROJECT_AUDIT.md`, `IMPLEMENTATION_STATUS.md`, `KNOWN_ISSUES.md`, `DECISIONS.md` y `TESTING.md`.

**No creados aún — no se consideran completados:** `ARCHITECTURE.md`, `API.md`, `SECURITY.md`, `OAUTH_SETUP.md`, `DEPLOYMENT.md` y `ROLLBACK.md`. Se crearán y verificarán antes del gate que habilite su respectivo alcance; no bloquean la auditoría documental de Fase 0, pero sí los gates posteriores de arquitectura, backend y despliegue.

## Estado verificable posterior — 2026-07-20

- Backend: `php artisan test` — 26 pruebas y 166 aserciones; Pint y Vite en verde.
- Android: `testDebugUnitTest`, `assembleDebug` y `assembleRelease` en verde.
- Gmail: scope mínimo `gmail.readonly`; identidad mediante Gmail Profile.
- Microsoft: `Mail.Read` + `User.Read`, necesario para Graph `/me`.
- Callback: página neutral sin tokens/códigos ni dependencia de cookie web; Android refresca al volver.
- Firebase Auth, Google Services y sus dependencias fueron retirados.
- AndroidX Hilt Work/Compiler 1.4.0 se alinea con Dagger/Hilt 2.60.1.
- Verificación ad hoc del comportamiento modificado: PASS; script temporal eliminado.

## Revisión independiente histórica de Gate 0 — 2026-07-20

**Veredicto histórico: BLOQUEADO (Gate 0).** Esta revisión corresponde a la instantánea previa a la implementación. Los bloqueos de schemas, fallback destructivo, backup y ejecución instrumentada se resolvieron el 22/07/2026.

Hallazgos de aquella revisión y estado actual:

1. Resuelto: `WalletDatabaseMigrationTest`, `TransactionDaoTransferTest` y el resto de la suite instrumentada pasan 10/10.
2. Resuelto para las rutas soportadas actuales: migraciones registradas, schema v8 exportado y fallback destructivo retirado.
3. Resuelto: backup/transferencia desactivados y dominios sensibles excluidos mediante `data_extraction_rules.xml`.
4. Completar regresión de transferencias, CSV y cuentas/tarjetas.

La clasificación de los artefactos raíz se corrigió antes del commit base mediante `.gitignore`; el árbol de trabajo quedó limpio tras los commits `48eef59` y `bb4ac61`.

## Evidencia de la sesión — 2026-07-20

**Objetivo:** validar la línea base real y eliminar el bloqueo de compilación de pruebas unitarias.

**Tareas del TODO:** 0.2 parcial, 0.4 parcial.

**Archivos modificados:**
- `app/src/test/.../DashboardViewModelTest.kt`
- `app/src/main/.../data/preferences/UserPreferencesRepository.kt`
- `app/src/main/.../presentation/dashboard/DashboardViewModel.kt`
- `app/src/main/.../core/database/RepositoryModule.kt`
- Documentación inicial de `docs/email-integration/`.

**Cambio realizado:** se introdujo el contrato mínimo `UserProfilePreferences`, enlazado por Hilt a `UserPreferencesRepository`. Esto desacopla el dashboard de DataStore/`Context` y permite al test usar un fake determinista. No se alteró la semántica de preferencias de producción.

**Pruebas ejecutadas:**

| Comando | Resultado |
|---|---|
| `./gradlew testDebugUnitTest --tests 'com.bsolutions.wallet.presentation.dashboard.DashboardViewModelTest' --console=plain` | Éxito. |
| `./gradlew testDebugUnitTest --console=plain` | Éxito: 24 pruebas, 0 failures, 0 errors, 0 skipped. |
| `./gradlew assembleDebug --console=plain` | Éxito después del cambio final. |

**Resultado:** la suite unitaria ya compila y pasa. Persisten advertencias de APIs Compose deprecadas y una advertencia de anotación Kotlin; no bloquearon la build.

**Riesgos/pedientes:** falta correr instrumentadas, eliminar migración destructiva sólo después de cubrir rutas, configurar schemas y backup seguro, y crear commit base.

**Siguiente tarea desbloqueada:** diseñar y probar las migraciones Room soportadas antes de remover `fallbackToDestructiveMigration()`.
