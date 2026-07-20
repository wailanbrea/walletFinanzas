# Problemas conocidos — Integración de correos

| ID | Severidad | Estado | Evidencia / acción requerida |
|---|---|---|---|
| KI-001 | Bloqueante de Gate 0 | Abierto | No existe commit base; `git status` muestra todo sin seguimiento. Crear una línea base intencional tras revisar secretos y artefactos. |
| KI-002 | Alta | Abierto | `WalletDatabase` tiene `exportSchema = false`; activar y versionar schemas antes de cambios de esquema del módulo de correo. |
| KI-003 | Alta | Abierto | `fallbackToDestructiveMigration()` sigue activo en `DatabaseModule`; no se puede retirar hasta probar todas las rutas de migración soportadas. |
| KI-004 | Alta | Abierto | `android:allowBackup="true"` no está acompañado por reglas de exclusión de datos sensibles. Definir y probar reglas de backup/data extraction. |
| KI-005 | Media | Bloqueado | Las pruebas instrumentadas de transferencias y migración existen pero no se ejecutaron en esta sesión. `adb` no está disponible en el PATH de la sesión, por lo que no se pudo descubrir ni ejecutar un emulador/dispositivo. |
| KI-006 | Media | Abierto | El grafo Graphify no incluye el último cambio de desacoplamiento de preferencias; actualizarlo tras la validación final. |
| KI-007 | Baja | Abierto | La compilación emite advertencias de APIs Compose deprecadas y de target de anotación Kotlin. Tratar en una tarea de mantenimiento separada, sin mezclar con el módulo de correo. |
