# Reglas de Costo y Límites del MVP

## Propósito
Este documento establece las restricciones estrictas para evitar exceder las cuotas gratuitas de servicios en el MVP de Wallet Finanzas Personales.

---

## 1. Backend Laravel — identidad canónica

> **Decisión vigente 20/07/2026 (ADR-003):** Laravel Sanctum es la única identidad
> remota. Firebase Auth fue retirado para evitar usuarios duplicados y ownership ambiguo.
> El costo del backend depende del hosting elegido y debe aprobarse antes de producción.

### Servicios vigentes
- ✅ **Laravel Sanctum** para registro, login, recuperación, sesiones y ownership remoto.
- ✅ **Room/SQLCipher** como fuente de verdad local offline-first.
- ✅ **MySQL** para datos remotos del backend Wallet.
- ⏸️ FCM, Crashlytics o Firestore no forman parte del runtime actual; requieren ADR y presupuesto propios.

### Servicios PROHIBIDOS en MVP
- ❌ **Plan Blaze** — prohibición absoluta; es el único camino a un cobro
- ❌ **Phone Auth** - No usar autenticación por SMS
- ❌ **Cloud Functions / Cloud Run** - No disponibles en Spark; la lógica debe estar local
- ❌ **Storage** - Requiere Blaze desde 2025; los comprobantes se guardan localmente

### Reglas de seguridad Firestore (obligatorias antes de activar sync)
```javascript
// Cada usuario solo lee/escribe sus propios documentos
match /users/{userId}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

### Límites Spark
```kotlin
// Máximo 25 usuarios recomendados para beta
MAX_USERS_BETA = 25L
// Firestore: presupuestar < 40k lecturas/día y < 15k escrituras/día (margen del 20%)
```

---

## 2. ~~Supabase~~ (ELIMINADO del stack — 16/07/2026)

Sustituido por Firestore (sección 1). Motivos: un solo proveedor, persistencia offline
nativa del SDK de Android (evita construir cola de sync + WorkManager + resolución de
conflictos a mano), y cero riesgo de cobro en Spark. El doc 06_SUPABASE queda obsoleto.

---

## 3. Salt Edge (PREPARADO DESACTIVADO)

### Feature Flag INICIAL
```kotlin
const val BANK_SYNC_ENABLED = false  // Desactivado por defecto

// Solo en DEBUG permitir fake provider
if (BuildConfig.DEBUG && BANK_SYNC_ENABLED) {
    useFakeBankProvider()
} else {
    bankSyncDisabled()
}
```

### Restriciones
- ❌ NO banreservas real
- ❌ NO credenciales bancarias reales
- ❌ NO refresh bancario real en producción
- ✅ Fake provider SOLO en DEBUG
- ✅ Edge function `saltedge-create-session` para sandbox testing

---

## 4. Límites de Negocio (MVP)

### Usuarios Beta
```kotlin
MAX_USERS_BETA = 25L
// Bloquear registro si: registeredUsersCount >= MAX_USERS_BETA
```

### Transacciones
```kotlin
MAX_TRANSACTIONS_PER_USER_LOCAL = 3000L
MAX_TRANSACTIONS_GLOBAL_MVP = 50000L
MAX_TRANSACTIONS_PER_DAY_PER_USER = 100L

// Cost Guard debe bloquear:
data class UsageStats(
    val totalTransactions: Long,
    val syncedTransactions: Long,
    val dailyTransactionCount: Int,
    val storageUsedBytes: Long,
    val imageFilesCount: Int,
    val syncsToday: Int,
    val bankSyncEnabled: Boolean
)
```

### Cuentas
```kotlin
MAX_ACCOUNTS_PER_USER = 20
```

### Categorías
```kotlin
MAX_CATEGORIES_PER_USER = 200
```

### Archivos (Comprobantes)
```kotlin
MAX_IMAGES_PER_USER = 100
MAX_GLOBAL_IMAGES = 1500L
MAX_IMAGE_SIZE_BYTES = 300_000L  // 300KB máximo comprimido
STORAGE_INTERNAL_LIMIT_BYTES = 500_000_000L  // 500MB
```

---

## 5. Límites de Sincronización

### Frecuencias
```kotlin
MAX_AUTO_SYNC_INTERVAL_HOURS = 12L      // Máximo cada 12 horas
MAX_MANUAL_SYNC_PER_DAY = 3             // Máximo 3 veces por día por usuario
```

### Payload Limits
```kotlin
MAX_PAYLOAD_BYTES_PER_REQUEST = 512_000L  // 512KB
MAX_CHANGES_PER_REQUEST = 500             // Máximo 500 cambios por request
```

### Backoff Exponencial
```kotlin
initialDelayMs = 30000
maxDelayMs = 12 * 60 * 60 * 1000  // 12 horas
multiplier = 2.0
maxRetries = 5
```

---

## 6. Cost Guard Implementation

### Screen: CostGuardScreen
Debe mostrar:
- ✅ Usuarios registrados (con bloqueador si >= 25)
- ✅ Transacciones locales (bloquear si >= 3000)
- ✅ Transacciones sincronizadas
- ✅ Storage estimado (alerta a 90%)
- ✅ Imágenes guardadas
- ✅ Última sincronización
- ✅ Syncs del día (bloquear si >= 3)
- ✅ Feature bank sync activa/inactiva (false por defecto)
- ✅ Estado MVP bloqueado/no bloqueado

### Bloqueo Lógico
```kotlin
data class CostGuardState(
    val isBlocked: Boolean,
    val blockReason: String? = null,
    val blockedUntilMillis: Long? = null
)

// Ejemplos de bloqueo:
if (totalTransactions >= MAX_TRANSACTIONS_PER_USER_LOCAL) {
    block("Límite de transacciones alcanzado")
}
if (storageUsedBytes > STORAGE_INTERNAL_LIMIT_BYTES * 0.9) {
    block("Storage al 90% - riesgo de exceder cuota")
}
```

---

## 7. Costos Estimados MVP

### Backend Wallet
- Laravel/Sanctum: software sin licencia; hosting, base de datos, correo y dominio pendientes de cotización.
- OAuth Gmail/Microsoft: sin coste directo de API en el alcance actual, sujeto a cuotas y verificación del proveedor.

### Hosting MVP
- ✅ Build APK: Gracis con Gradle
- ✅ Distribución: Google Play (R$25 una vez) / TestFlight (Apple ID gratuito)

**Costo Total Mensual Estimado: $0-10** (si se respetan los límites)

---

## 8. Monitoreo de Costos

### Implementación
```kotlin
class CostMonitor : Service {
    private val usageStats = DataStore<UsageStats>(...)
    
    fun trackEvent(event: UsageEvent) {
        when (event.type) {
            UsageEvent.Type.TRANSACTION_CREATED -> incrementTransactions()
            UsageEvent.Type.IMAGE_UPLOADED -> incrementStorage()
            UsageEvent.Type.SYNC_COMPLETED -> syncCounters()
        }
        
        checkLimits() // Bloquea si se excede
    }
    
    private fun checkLimits() {
        val state = usageStats.data ?: return
        
        if (state.totalTransactions >= MAX_TRANSACTIONS_PER_USER_LOCAL) {
            showCostGuardAlert()
        }
    }
}
```

---

## 9. Decisiones Clave

### ✅ Hacer
- Usar Room como fuente de verdad principal
- Laravel Sanctum como única identidad remota
- Salt Edge en sandbox/fake mode
- Implementar Cost Guard desde el inicio

### ❌ No hacer
- Activar servicios pagos sin necesidad
- Conectar bancos reales antes de tener MVP funcionando offline-first
- Subir imágenes sin comprimir
- Exceder límites sin bloqueos preventivos

---

## 10. Alertas al Usuario

```kotlin
enum class CostGuardAlertType {
    USER_LIMIT_REACHED("Has alcanzado el límite de usuarios beta"),
    TRANSACTION_LIMIT_REACHED("Límite de transacciones alcanzado: 3,000"),
    STORAGE_NEAR_CAPACITY("Almacenamiento al 90%. Revisa tus comprobantes"),
    SYNC_LIMIT_REACHED("Sincronizaste 3 veces hoy - límite diario alcanzado")
}

// Mostrar alertas con explicación y alternativas:
data class CostGuardAlert(
    val type: CostGuardAlertType,
    val message: String,
    val alternative: String? = "Continúa usando la app offline"
)
```

---

**Última actualización:** 2026-07-11  
**Responsable:** Backend Team  
**Revisar:** Cada sprint de desarrollo
