# ADR 001: Selección de Stack y Estrategia de Despliegue del Backend

## Estado
Aceptado

## Contexto
El proyecto requiere un backend para gestionar datos financieros, sincronización bancaria (Salt Edge) y envío de notificaciones/correos. El requisito inicial es un desarrollo local rápido en XAMPP, con la intención de escalar a un VPS.

## Decisión
- **Framework:** PHP con Laravel 11. Laravel proporciona un robusto sistema de ORM (Eloquent), migraciones, y autenticación lista para producción.
- **Base de Datos:** MySQL (vía XAMPP localmente, migrando a una instancia gestionada en el VPS).
- **Autenticación:** Laravel Sanctum para la gestión de tokens JWT.
- **Arquitectura API:** RESTful. Se utilizará un patrón de "Resource" para transformar los modelos de Eloquent a respuestas JSON consistentes con las unidades menores (`Long`) requeridas por el frontend.

## Consecuencias
- **Pros:** Rapidez de desarrollo, facilidad de despliegue en VPS, excelente manejo de colas (para envíos de correo).
- **Contras:** Requiere configuración inicial de Laravel y manejo de variables de entorno para la transición local $\rightarrow$ VPS.
