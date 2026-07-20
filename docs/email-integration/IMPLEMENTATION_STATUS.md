# Estado de implementación — Integración de correos

## Estado global

- **Fase activa:** 0 — Estabilización y auditoría.
- **Gate 0:** **BLOQUEADO**.
- **Backend Laravel:** no creado, conforme al orden obligatorio.
- **Módulo Android de correo:** no creado, conforme al orden obligatorio.

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
