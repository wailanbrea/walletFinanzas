# ADR-003 — Laravel Sanctum como identidad canónica

- **Estado:** Aprobado
- **Fecha:** 2026-07-20
- **Decisor:** Product owner

## Contexto

La aplicación tenía Firebase Auth email/password, pero el backend Laravel y el flujo Android vigente ya implementan registro, login, recuperación, rotación por dispositivo y logout mediante Laravel Sanctum. Mantener ambas identidades sin un puente formal produciría usuarios duplicados y ownership ambiguo para cuentas, transacciones y conexiones OAuth.

## Decisión

Laravel es la autoridad de identidad. Android usa exclusivamente la API Wallet y almacena el token Sanctum en preferencias cifradas. Las rutas privadas exigen tokens con capacidad `wallet`; el backend rota tokens por dispositivo, aplica expiración y resuelve ownership desde el usuario autenticado, nunca desde un `user_id` enviado por el cliente.

Firebase Auth y su repositorio Android se retiran. No se implementará una segunda autenticación ni un enlace Firebase↔Laravel. Room/SQLCipher continúa como fuente de verdad local y el login sigue siendo requisito para recursos remotos, no para consultar datos locales offline.

## Consecuencias

- La recuperación de contraseña, verificación, sesiones, administración y OAuth pertenecen a Laravel.
- El APK no necesita Firebase Auth, Google Services ni `google-services.json` para autenticación.
- Un futuro servicio Firebase distinto de Auth requerirá un ADR separado y no podrá introducir una segunda identidad.
- La URL HTTPS, el mailer y las credenciales OAuth se configuran por entorno fuera del repositorio.

## Controles

- Tokens cifrados en Android y nunca enviados a hosts de terceros.
- TTL y rotación por identificador de dispositivo.
- Limpieza global de sesión ante HTTP 401.
- Pruebas de aislamiento entre usuarios y respuestas sin tokens de proveedor.
