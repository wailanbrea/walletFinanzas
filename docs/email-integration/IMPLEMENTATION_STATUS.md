# Estado de implementación — Integración de correos

## Estado global

- **Fase activa:** 0 — Estabilización y auditoría.
- **Gate 0:** **BLOQUEADO**.
- **Backend Laravel:** no creado, conforme al orden obligatorio.
- **Módulo Android de correo:** no creado, conforme al orden obligatorio.

## Control de versión

- **Commit base recuperable:** `48eef59` — `chore: establish verified Android baseline` (20/07/2026).
- Credenciales, `local.properties`, `google-services.json`, outputs de build y artefactos de emulador permanecen fuera del commit por `.gitignore`.

## Cobertura documental por fase

**Completos para Fase 0:** `PROJECT_AUDIT.md`, `IMPLEMENTATION_STATUS.md`, `KNOWN_ISSUES.md`, `DECISIONS.md` y `TESTING.md`.

**No creados aún — no se consideran completados:** `ARCHITECTURE.md`, `API.md`, `SECURITY.md`, `OAUTH_SETUP.md`, `DEPLOYMENT.md` y `ROLLBACK.md`. Se crearán y verificarán antes del gate que habilite su respectivo alcance; no bloquean la auditoría documental de Fase 0, pero sí los gates posteriores de arquitectura, backend y despliegue.

## Revisión independiente de Quality — 2026-07-20

**Veredicto: BLOQUEADO (Gate 0).** El perfil `quality` confirmó que no se debe iniciar Laravel, OAuth ni el módulo de correo mientras persistan los riesgos de migración, backup y pruebas instrumentadas.

Hallazgos que siguen abiertos:

1. Ejecutar en emulador/dispositivo `WalletDatabaseMigrationTest` y `TransactionDaoTransferTest`; en esta sesión `adb` no estaba disponible en `PATH`.
2. Añadir cobertura de migraciones 3→4, 4→5, 5→6 y de la cadena soportada antes de eliminar el fallback destructivo.
3. Definir y validar reglas de backup/data extraction para datos financieros y preferencias sensibles.
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
