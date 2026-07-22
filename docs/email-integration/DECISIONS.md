# Decisiones y ADRs — Integración de correos

## Estado

Los ADRs de la Fase 1 son obligatorios. ADR-003 fue aprobado por el product owner; los demás continúan pendientes. Ningún cambio adicional de fuente de verdad, privacidad, coste o despliegue se implementará sin su ADR correspondiente.

## ADRs requeridos

| ADR | Decisión pendiente | Estado |
|---|---|---|
| ADR-001 | MySQL para correo/candidatos y Room para libro local durante MVP. | Pendiente |
| ADR-002 | Saga Android → Room → ACK e idempotencia. | Pendiente |
| [ADR-003](ADR-003-sanctum-identity.md) | Laravel Sanctum como identidad canónica; Firebase Auth retirado. | Aprobado |
| ADR-004 | OAuth backend-first, state, PKCE, callback y deep link. | Pendiente |
| ADR-005 | Docker Compose con MySQL 8 y Redis en desarrollo. | Pendiente |
| ADR-006 | Cifrado de tokens y rotación de claves. | Pendiente |
| ADR-007 | Retención y eliminación de contenido de correo. | Pendiente |
| ADR-008 | Contrato de errores y API V1. | Pendiente |
| ADR-009 | Minor units, exponentes de moneda y UTC. | Pendiente |
| ADR-010 | No usar Firestore para el módulo de correo. | Pendiente |

## Invariantes ya verificados en Android

- Room/SQLCipher continúa como libro local offline-first.
- Los importes financieros locales usan `Long`; no se introducirán `Float`/`Double` para dinero.
- Laravel Sanctum protege todos los recursos remotos; Room/SQLCipher conserva el comportamiento offline sin login obligatorio para datos locales.
- Salt Edge sandbox no forma parte de la nueva integración de correo y debe permanecer aislado.
