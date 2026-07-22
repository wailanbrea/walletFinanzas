# Auditoría del proyecto — Integración de correos

**Fecha:** 2026-07-20  
**Alcance:** auditoría inicial, actualizada con la decisión de identidad del 20/07/2026.

## Arquitectura actual verificada

| Área | Evidencia |
|---|---|
| App | Android/Kotlin, paquete `com.bsolutions.wallet`, SDK 26–37, JVM 21. |
| UI | Jetpack Compose + Material 3, navegación Compose y ViewModels con `StateFlow`. |
| DI | Hilt; los bindings de repositorios están en `core/database/RepositoryModule.kt`. |
| Persistencia | Room v9 con SQLCipher y partición por propietario. Entidades: cuentas, transacciones, categorías, presupuestos, metas, deudas, pagos planificados, conexiones bancarias y cola de sync. |
| Datos financieros | Montos en `Long` (minor units); transferencias se ejercitan en prueba instrumentada. |
| Red/Auth | Retrofit/OkHttp, Laravel Sanctum y Salt Edge sandbox opcional. |
| Trabajo diferido | WorkManager para pagos planificados. |

## Navegación y modelo financiero

La aplicación contiene módulos de cuentas, movimientos, categorías, presupuestos, metas, deudas, pagos planificados, reportes, CSV, perfil, seguridad y sincronización bancaria sandbox. Room/SQLCipher es la fuente de verdad del libro local y cada fila pertenece a una partición `guest` o `user:<id>`. Laravel Sanctum es la identidad canónica de recursos remotos; al autenticar, los datos invitados se reclaman transaccionalmente y el logout cambia a una partición sin datos del usuario anterior.

## Línea base comprobada

| Comprobación | Resultado |
|---|---|
| `./gradlew assembleDebug --console=plain` | **Éxito** (20/07/2026). |
| `./gradlew testDebugUnitTest --console=plain` | **Éxito** tras reparar el fixture obsoleto de `DashboardViewModelTest`. |
| Instrumentadas DAO/migración | Existen, pero no se ejecutaron en esta auditoría: requieren emulador/dispositivo disponible. |
| Git | Repositorio inicializado pero sin commits; los archivos aún están sin seguimiento. |
| Secretos locales | `.gitignore` ignora `local.properties`, credenciales, keystores y Graphify. Verificado con `git check-ignore`. |

## Riesgos y bloqueos de Gate 0

1. Resuelto 22/07/2026: `WalletDatabase` exporta y versiona el schema v8 mediante el plugin Room.
2. Resuelto 22/07/2026: retirado el fallback destructivo; los downgrades incompatibles ya no borran datos.
3. Resuelto 22/07/2026: backup/transferencia desactivados y reglas de extracción excluyen todos los dominios con datos financieros.
4. Sólo existe una prueba manual de migración 2→3; faltan pruebas de las rutas 3→4, 4→5, 5→6 y actualización encadenada soportada.
5. Las pruebas instrumentadas críticas se ejecutaron (10/10); el commit base también existe. Gate 0 ya no está bloqueado por estas dos condiciones.
6. El grafo Graphify existente fue extraído el 2026-07-19; debe actualizarse después de cambios de código.

## Conclusión

Laravel, Sanctum y OAuth backend-first ya están implementados y probados. Siguen pendientes las credenciales OAuth, URL HTTPS, mailer y pruebas instrumentadas completas antes de producción.
