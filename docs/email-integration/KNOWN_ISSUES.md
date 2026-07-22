# Problemas conocidos — Integración de correos

| ID | Severidad | Estado | Evidencia / acción requerida |
|---|---|---|---|
| KI-001 | Bloqueante de Gate 0 | Abierto | No existe commit base; `git status` muestra todo sin seguimiento. Crear una línea base intencional tras revisar secretos y artefactos. |
| KI-002 | Alta | Resuelto 22/07/2026 | `exportSchema = true`, plugin Room configurado y schema v8 versionado en `app/schemas`. |
| KI-003 | Alta | Resuelto 22/07/2026 | Retirado `fallbackToDestructiveMigrationOnDowngrade()`; una versión incompatible ahora falla de forma segura en vez de borrar datos financieros. |
| KI-004 | Alta | Resuelto 22/07/2026 | Backup y transferencia desactivados; `data_extraction_rules.xml` excluye BD, archivos y preferencias en Android 12+. |
| KI-005 | Media | Resuelto 22/07/2026 | 10/10 pruebas instrumentadas ejecutadas en Pixel 9 Pro API 37. BlueStacks Pie 64 se usa para smoke/UI porque UTP deja su ADB offline antes de ejecutar tests. |
| KI-006 | Media | Abierto | El grafo Graphify no incluye el último cambio de desacoplamiento de preferencias; actualizarlo tras la validación final. |
| KI-007 | Baja | Abierto | La compilación emite advertencias de APIs Compose deprecadas y de target de anotación Kotlin. Tratar en una tarea de mantenimiento separada, sin mezclar con el módulo de correo. |
