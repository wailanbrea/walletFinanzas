# API Contract: Wallet Finanzas - Core Entities

## Overview
Definición de los modelos de datos que el backend Laravel debe exponer y que la aplicación Android consumirá.

## Global Rules
- **Amounts:** Todos los montos deben enviarse en unidades menores (ej. `Long` para centavos), nunca como `Double` o `Float`.
- **IDs:** Los IDs de transacciones y movimientos deben ser UUIDs o strings únicos de 36 caracteres.
- **Dates:** Formato ISO 8601 (`YYYY-MM-DDTHH:mm:ssZ`).

## Endpoints & Models

### 1. Accounts (Cuentas)
`GET /api/v1/accounts`
`POST /api/v1/accounts`

**Model `Account`**
```json
{
  "id": "uuid",
  "name": "Cuenta Corriente",
  "balance": 150000,
  "currency": "DOP",
  "institution_name": "Banco Popular",
  "country_code": "DO",
  "card_last_four": "1234",
  "is_active": true
}
```

### 2. Transactions (Movimientos)
`GET /api/v1/transactions?account_id=uuid`
`POST /api/v1/transactions`

**Model `Transaction`**
```json
{
  "id": "uuid",
  "account_id": "uuid",
  "amount": -5000,
  "currency": "DOP",
  "description": "Compra en Supermercado",
  "category_id": "uuid",
  "timestamp": "2026-07-20T14:30:00Z",
  "status": "completed"
}
```

### 3. Bank Connections (Conexiones Bancarias)
`GET /api/v1/bank_connections`

**Model `BankConnection`**
```json
{
  "id": "uuid",
  "provider_name": "Salt Edge",
  "provider_code": "SE_001",
  "country_code": "DO",
  "status": "connected",
  "last_sync_at": "2026-07-20T14:30:00Z"
}
```
