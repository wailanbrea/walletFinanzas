# Wallet Finanzas Backend

API REST local para Wallet Finanzas, construida con Laravel 12, PHP 8.2, Laravel Sanctum y MariaDB de XAMPP.

## Estado verificado

- Laravel `12.64.0`
- PHP `8.2.12`
- MariaDB XAMPP `10.4.32`
- 11 pruebas automatizadas / 56 aserciones
- API real verificada contra MariaDB

## Dirección local

```text
http://127.0.0.1:8000
```

Endpoint público de salud:

```text
GET http://127.0.0.1:8000/api/v1/health
```

Respuesta esperada:

```json
{"status":"ok","service":"wallet-finanzas-api","version":"v1"}
```

## Iniciar el servidor

Desde `C:\xampp\php\www\WalletFinanzasBackend`:

```bash
php artisan serve --host=127.0.0.1 --port=8000
```

MariaDB de XAMPP debe estar iniciado. La base local configurada es `wallet_finanzas`.

## API v1

| Método | Ruta | Auth | Descripción |
|---|---|---:|---|
| GET | `/api/v1/health` | No | Salud del servicio |
| POST | `/api/v1/auth/register` | No | Registro y token Sanctum |
| POST | `/api/v1/auth/login` | No | Inicio de sesión y token |
| POST | `/api/v1/auth/logout` | Sí | Revoca el token actual |
| GET | `/api/v1/accounts` | Sí | Cuentas del usuario |
| POST | `/api/v1/accounts` | Sí | Crear cuenta |
| GET | `/api/v1/transactions?account_id={uuid}` | Sí | Movimientos de una cuenta propia |
| POST | `/api/v1/transactions` | Sí | Crear movimiento y actualizar saldo atómicamente |
| GET | `/api/v1/bank-connections` | Sí | Conexiones bancarias del usuario |

La autenticación usa:

```http
Authorization: Bearer TOKEN
Accept: application/json
```

## Prueba rápida con curl

### 1. Registrar usuario

```bash
curl -X POST http://127.0.0.1:8000/api/v1/auth/register \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{"name":"Usuario Demo","email":"demo@example.com","password":"Password123!","password_confirmation":"Password123!","device_name":"android-local"}'
```

Copia `data.token` de la respuesta.

### 2. Crear cuenta

```bash
curl -X POST http://127.0.0.1:8000/api/v1/accounts \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"name":"Cuenta principal","balance":100000,"currency":"DOP","country_code":"DO"}'
```

### 3. Ejecutar pruebas

```bash
php artisan test
vendor/bin/pint --test
php artisan migrate:status
```

## Reglas financieras implementadas

- Los montos se almacenan como `BIGINT` en unidades menores; no se usan `float` ni `double`.
- Las cuentas y movimientos usan UUIDv7.
- Un usuario no puede consultar ni modificar recursos de otro usuario.
- La moneda de un movimiento debe coincidir con la moneda de su cuenta.
- Crear un movimiento y actualizar el saldo ocurre dentro de una transacción SQL.
- Los importes decimales y el importe cero se rechazan.

## Preparación para VPS

Antes de producción:

1. Usar credenciales MariaDB dedicadas, nunca `root`.
2. Configurar `APP_ENV=production`, `APP_DEBUG=false` y `APP_URL=https://...`.
3. Servir exclusivamente `public/` mediante Nginx o Apache.
4. Activar HTTPS, firewall y copias de seguridad cifradas.
5. Ejecutar `php artisan config:cache`, `route:cache` y `view:cache` durante el despliegue.
6. Configurar supervisor/systemd para colas cuando se agreguen sincronización bancaria y correos.
7. Mantener `.env` fuera del repositorio.
