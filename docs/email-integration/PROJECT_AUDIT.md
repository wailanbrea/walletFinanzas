# Auditoría del proyecto — Integración de correos

**Fecha:** 2026-07-20  
**Alcance:** estado verificable previo a la Fase 1 de integración de correos.

## Arquitectura actual verificada

| Área | Evidencia |
|---|---|
| App | Android/Kotlin, paquete `com.bsolutions.wallet`, SDK 26–37, JVM 21. |
| UI | Jetpack Compose + Material 3, navegación Compose y ViewModels con `StateFlow`. |
| DI | Hilt; los bindings de repositorios están en `core/database/RepositoryModule.kt`. |
| Persistencia | Room v6 con SQLCipher. Entidades: cuentas, transacciones, categorías, presupuestos, metas, deudas, pagos planificados y conexiones bancarias. |
| Datos financieros | Montos en `Long` (minor units); transferencias se ejercitan en prueba instrumentada. |
| Red/Auth | Retrofit/OkHttp, Firebase Auth y Salt Edge sandbox opcional. |
| Trabajo diferido | WorkManager para pagos planificados. |

## Navegación y modelo financiero

La aplicación contiene módulos de cuentas, movimientos, categorías, presupuestos, metas, deudas, pagos planificados, reportes, CSV, perfil, seguridad y sincronización bancaria sandbox. Room/SQLCipher es la fuente de verdad del libro local. La autenticación Firebase existe, pero el inicio offline no obliga login; por tanto, ningún backend nuevo puede asumir que los datos locales ya están ligados a un usuario autenticado.

## Línea base comprobada

| Comprobación | Resultado |
|---|---|
| `./gradlew assembleDebug --console=plain` | **Éxito** (20/07/2026). |
| `./gradlew testDebugUnitTest --console=plain` | **Éxito** tras reparar el fixture obsoleto de `DashboardViewModelTest`. |
| Instrumentadas DAO/migración | Existen, pero no se ejecutaron en esta auditoría: requieren emulador/dispositivo disponible. |
| Git | Repositorio inicializado pero sin commits; los archivos aún están sin seguimiento. |
| Secretos locales | `.gitignore` ignora `local.properties`, `app/google-services.json`, keystores y Graphify. Verificado con `git check-ignore`. |

## Riesgos y bloqueos de Gate 0

1. `WalletDatabase` usa `exportSchema = false`; no hay schemas versionados para validar migraciones reproduciblemente.
2. `DatabaseModule` conserva `fallbackToDestructiveMigration()`: es incompatible con el requisito de preservar datos financieros antes de introducir el módulo de correos.
3. El manifest declara `android:allowBackup="true"` y no define reglas de `dataExtractionRules`/`fullBackupContent` para excluir base cifrada, claves y preferencias sensibles.
4. Sólo existe una prueba manual de migración 2→3; faltan pruebas de las rutas 3→4, 4→5, 5→6 y actualización encadenada soportada.
5. Gate 0 sigue bloqueado hasta ejecutar pruebas instrumentadas críticas y crear un commit base intencional.
6. El grafo Graphify existente fue extraído el 2026-07-19; debe actualizarse después de cambios de código.

## Conclusión

No se iniciará Laravel ni OAuth todavía. La siguiente tarea desbloqueada es terminar la estabilización de Room, backup y pruebas instrumentadas de Fase 0.
