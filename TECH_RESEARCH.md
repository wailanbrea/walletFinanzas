# Tech Research — Wallet Finanzas Backend

Fecha: 2026-07-20

## Stack validado

- PHP local: 8.2.12
- Framework: Laravel 12.64.0
- Autenticación: Laravel Sanctum 4.3.2
- Base local: MariaDB 10.4.32 de XAMPP
- API: REST versionada bajo `/api/v1`

## Decisiones informadas por investigación

1. **Laravel 12, no Laravel 13:** Laravel 12 admite PHP 8.2; Laravel 13 exige PHP 8.3 o superior.
2. **Sanctum para Android:** es la opción apropiada para tokens personales en una API móvil. Passport se reserva para un servidor OAuth2 completo.
3. **Contrato y estructura moderna:** Form Requests, API Resources, controladores bajo `Api/V1` y Feature Tests.
4. **Dinero como entero:** importes y saldos se guardan en unidades menores mediante `BIGINT`; nunca `float` o `double`.
5. **Mismo motor local/VPS:** mantener MariaDB/MySQL en ambos ambientes reduce diferencias de SQL.
6. **VPS recomendado:** Ubuntu LTS, Nginx, PHP 8.4-FPM, MariaDB/MySQL, HTTPS y workers supervisados.

## Riesgos y hoja de ruta

- PHP 8.2 llega a fin de vida el 31 de diciembre de 2026. Para VPS se recomienda PHP 8.4-FPM.
- Laravel 12 recibe correcciones generales hasta agosto de 2026 y seguridad hasta febrero de 2027.
- Antes de migrar a Laravel 13 debe actualizarse PHP y validar `composer.lock` en CI.
- Apache/Nginx debe publicar exclusivamente `public/`, nunca la raíz del repositorio.
- Producción requiere `APP_DEBUG=false`, secretos fuera de Git, HTTPS, firewall y backups probados.

## Fuentes oficiales

- https://laravel.com/docs/12.x/releases
- https://laravel.com/docs/13.x/releases
- https://laravel.com/docs/12.x/deployment
- https://laravel.com/docs/12.x/routing#api-routes
- https://laravel.com/docs/12.x/sanctum
- https://laravel.com/docs/12.x/passport#passport-or-sanctum
- https://laravel.com/docs/12.x/eloquent-resources
- https://laravel.com/docs/12.x/database#introduction
- https://laravel.com/docs/12.x/testing
- https://www.php.net/supported-versions.php
