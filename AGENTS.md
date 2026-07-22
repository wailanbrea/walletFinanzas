# Wallet Finanzas Personales — Contexto para agentes

## Propósito
Aplicación Android offline-first de finanzas personales: cuentas, movimientos, categorías, presupuestos, metas, deudas, pagos planificados, reportes, importación CSV y sincronización bancaria sandbox mediante Salt Edge.

## Stack
| Área | Detalle |
|---|---|
| App | Kotlin 2.2.10, Jetpack Compose, Material 3, Navigation Compose |
| Paquete | `com.bsolutions.wallet` |
| SDK | compile/target 37, min 26; Java/Kotlin JVM 21 |
| Arquitectura | Capas `presentation`, `domain`, `data`, `core`; Hilt + ViewModels + Flows |
| Datos locales | Room v6 con SQLCipher; DAOs y migraciones propias |
| Seguridad | Keystore/EncryptedPreferences, biometría, `FLAG_SECURE` configurable |
| Red/Auth | Retrofit/OkHttp; Laravel Sanctum; OAuth de correo backend-first |
| Trabajo diferido | WorkManager para pagos planificados |

## Arquitectura real
| Ruta | Responsabilidad |
|---|---|
| `MainActivity.kt` | Entrada, navegación Compose, bloqueo biométrico y protección de captura |
| `WalletApp.kt` | Inicialización global de aplicación/Hilt |
| `core/database/WalletDatabase.kt` | Base Room v6: cuentas, movimientos, categorías, presupuestos, metas, pagos, deudas y conexiones bancarias |
| `core/database/DatabaseKeyProvider.kt` | Gestión de clave de base cifrada |
| `data/local/dao/` | Persistencia Room por agregado |
| `data/repository/RepositoryImpls.kt` | Implementaciones de repositorios del dominio |
| `data/repository/BankSyncRepository.kt` | Sincronización Salt Edge; Room sigue siendo fuente de verdad e IDs remotos son deterministas `se_*` |
| `core/network/SaltEdgeApi.kt` | Contrato HTTP de Salt Edge |
| `presentation/` | Pantallas Compose y ViewModels por módulo |
| `core/notifications/PlannedPaymentWorker.kt` | Recordatorios y trabajo diferido de pagos planificados |
| `docs/` | Diseño, arquitectura, modelo de datos, integración AISP y backlog; validar contra código antes de aplicar ejemplos antiguos |
| `graphify-out/` | Grafo Graphify actualizado el 2026-07-18: 3,904 nodos y 8,215 aristas |

## Invariantes financieros y de datos
- Los montos persistidos se representan en **minor units (`Long`)**; no introducir `Double`/`Float` para cálculos, saldos, presupuestos, deudas o transferencias.
- Una transferencia debe ser atómica: débito y crédito deben mantenerse consistentes o revertirse juntos.
- Room/SQLCipher es la fuente de verdad local. Los cambios de esquema requieren migración no destructiva y pruebas de migración.
- La sincronización bancaria debe ser idempotente: los datos de Salt Edge usan IDs locales deterministas `se_<remote-id>`.
- No modificar cuentas o transacciones importadas sin definir explícitamente qué sistema es autoritativo y cómo se resuelve el conflicto.

## Seguridad y privacidad
- `local.properties`, credenciales OAuth/Salt Edge, tokens, keystores, capturas con información financiera y bases de datos jamás se versionan ni se incluyen en logs.
- Salt Edge está configurado como sandbox; las credenciales se inyectan desde `local.properties` en `BuildConfig`. No mover secretos a código o recursos.
- Mantener bloqueo biométrico, protección de captura y cifrado de base; cambios deben considerar recuperación ante fallo de Keystore.
- Laravel Sanctum es la identidad canónica para recursos remotos; los datos Room locales siguen disponibles offline sin atribuirlos automáticamente a una sesión remota.

## Calidad
```bash
./gradlew test
./gradlew assembleDebug
./gradlew connectedAndroidTest  # requiere emulador/dispositivo
```

Los tests relevantes incluyen parsers de dinero/CSV, ViewModels y migración/transferencias Room instrumentadas. Todo cambio de cálculo, DAO, migración, sincronización o seguridad debe incluir pruebas específicas.

## Flujo de trabajo
1. Revisar `git status`, este archivo y el grafo Graphify para cambios transversales.
2. Para finanzas, definir invariantes y casos límite antes de implementar.
3. Ejecutar pruebas/build reales y documentar resultados.
4. Ejecutar `graphify update .` tras cambios de código.
5. Enviar el diff al perfil `quality` para revisión independiente antes de commit o release.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
