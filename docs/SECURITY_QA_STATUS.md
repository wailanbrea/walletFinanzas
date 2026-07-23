# Security / QA Status — API v1

Fecha: 2026-07-20

## Gate local

| Control | Estado | Evidencia |
|---|---|---|
| Autenticación | Implementado | Sanctum; rutas financieras bajo `auth:sanctum` |
| BOLA/IDOR | Implementado | Consultas parten de relaciones del usuario y cuentas ajenas responden 404 |
| Precisión monetaria | Implementado | `BIGINT` y casts enteros; pruebas rechazan decimales |
| Actualización atómica de saldo | Implementado | `DB::transaction()` + `lockForUpdate()` |
| Idempotencia móvil | Implementado | UUID único, importe/fecha canónicos, PHPUnit y 30 rondas HTTP concurrentes contra dos workers |
| Aislamiento multiusuario | Implementado | Feature tests con dos usuarios |
| Validación | Implementado | Form Requests, moneda/país normalizados y estados cerrados |
| Mass assignment | Implementado | `user_id` se obtiene del token; Requests controlan campos aceptados |
| Serialización | Implementado | API Resources y fechas ISO-8601 UTC |
| Secretos | Implementado localmente | `.env` ignorado y verificado fuera del commit |
| Dependencias | Aprobado | Composer Audit sin avisos conocidos |
| Pruebas | Aprobado | PHPUnit Feature/Unit y Laravel Pint |

## Decisiones de dominio vigentes

- Los movimientos usan montos enteros **firmados** porque el contrato Android actual representa gastos como negativos e ingresos como positivos.
- La moneda del movimiento debe coincidir con la cuenta.
- El saldo inicial se permite al crear una cuenta durante este MVP; no existe endpoint para editarlo directamente.
- Las transacciones confirmadas no tienen endpoints de edición o eliminación.
- Las cuentas pueden quedar negativas; antes del VPS debe definirse explícitamente si cada tipo de cuenta permite sobregiro.

## Pendiente antes del VPS

1. Actualizar a PHP 8.4-FPM y usar un usuario MariaDB dedicado con contraseña.
2. Configurar Nginx/Apache con raíz exclusiva en `public/` y HTTPS.
3. Añadir expiración/capacidades de tokens y rate limits específicos para todas las rutas sensibles.
4. Incorporar paginación y límites máximos de consulta.
5. Definir política de saldo negativo, reversos, transferencias y conciliación.
6. Añadir backups cifrados con prueba real de restauración y rollback.
7. Ejecutar CI con pruebas, Pint, Composer Audit y build de assets.
8. Deshabilitar depuración: `APP_ENV=production`, `APP_DEBUG=false`.
