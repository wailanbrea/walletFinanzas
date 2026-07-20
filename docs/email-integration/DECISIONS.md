# Decisiones y ADRs — Integración de correos

## Estado

Los ADRs de la Fase 1 son obligatorios y **todavía no están aprobados**. Este archivo es el índice controlado; ningún cambio de fuente de verdad, autenticación, privacidad, coste o despliegue se implementará sin su ADR correspondiente.

## ADRs requeridos

| ADR | Decisión pendiente | Estado |
|---|---|---|
| ADR-001 | MySQL para correo/candidatos y Room para libro local durante MVP. | Pendiente |
| ADR-002 | Saga Android → Room → ACK e idempotencia. | Pendiente |
| ADR-003 | Firebase Auth como identidad única del backend. | Pendiente |
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
- Firebase Auth ya está presente, pero el arranque no exige sesión; el backend deberá rechazar recursos privados sin token Firebase válido sin cambiar el comportamiento offline existente.
- Salt Edge sandbox no forma parte de la nueva integración de correo y debe permanecer aislado.
