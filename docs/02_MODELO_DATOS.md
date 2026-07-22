# Modelo de Datos - Wallet Finanzas Personales

## 1. Entidades Principales

### Transaction (Movimiento)
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,                    // UUID v4
    
    // Información básica
    @ColumnInfo(name = "amount_in_cents", index = true) val amountInCents: Long,  // Minor units
    @ColumnInfo(name = "currency_code") val currencyCode: CurrencyCode,            // DOP, USD, EUR
    @ColumnInfo(name = "description") val description: String,                     // Ej: "Supermercado"
    @ColumnInfo(name = "merchant") val merchant: String?,                          // Nombre comerciante
    
    // Clasificación
    @ColumnInfo(name = "category_id", index = true) val categoryId: String,        // FK a categories
    @ColumnInfo(name = "account_id", index = true) val accountId: String,          // FK a accounts
    
    // Tipo y origen
    @ColumnInfo(name = "transaction_type") val type: TransactionType,              // EXPENSE, INCOME, TRANSFER_OUT, TRANSFER_IN
    @ColumnInfo(name = "source", index = true) val source: TransactionSource,       // MANUAL, IMPORTED, SYNCED, BANK
    
    // Fecha y estado de sync
    @ColumnInfo(name = "date") val date: java.time.LocalDate,                      // Fecha del movimiento
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,            // Timestamp UTC ms
    @ColumnInfo(name = "sync_status", index = true) val syncStatus: SyncStatus,    // PENDING, SYNCED, FAILED, CONFLICT
    
    // Notas y referencias
    @ColumnInfo(name = "note") val note: String?,                                  // Nota opcional
    @ColumnInfo(name = "receipt_file_id") val receiptFileId: String?               // Referencia a comprobante
) {
    enum class TransactionType(val value: Int, val label: String) {
        EXPENSE(0, "Gasto"),
        INCOME(1, "Ingreso"),
        TRANSFER_OUT(2, "Transferencia Salida"),
        TRANSFER_IN(3, "Transferencia Entrada")
    }
    
    enum class TransactionSource(val value: Int) {
        MANUAL(0),       // Creado manualmente por usuario
        IMPORTED(1),     // Importado desde CSV
        SYNCED(2),       // Sincronizado con Supabase/Salt Edge
        BANK(3)          // Sincronizado con banco real (Salt Edge)
    }
    
    enum class SyncStatus(val value: String) {
        PENDING("pending"),         // Pendiente de sincronización
        SYNCING("syncing"),         // Actualmente sincronizando
        SYNCED("synced"),           // Sincronizado correctamente
        FAILED("failed"),           // Falló la sincronización
        CONFLICT("conflict")        // Conflict con versión remota
    }
    
    companion object {
        fun toDomain(
            id: String, amountInCents: Long, currencyCode: CurrencyCode,
            description: String, merchant: String?, categoryId: String,
            accountId: String, type: TransactionType, source: TransactionSource,
            date: java.time.LocalDate, createdAt: Long, syncStatus: SyncStatus,
            note: String?, receiptFileId: String?,
            balance: BigDecimal = BigDecimal.ZERO
        ): Transaction {
            return Transaction(
                id = id,
                amount = balance,  // Calcular desde saldo de cuenta
                currency = currencyCode,
                description = description,
                merchant = merchant,
                category = null,    // Obtener desde categories
                account = null      // Obtener desde accounts
            )
        }
    }
}

@TypeConverters(Long::class)
@Converters({CurrencyCodeConverter::class, SyncStatusConverter::class})
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,                    // UUID v4
    
    // Información bancaria
    @ColumnInfo(name = "bank_name") val bankName: String,          // Ej: "Banreservas"
    @ColumnInfo(name = "account_type") val accountType: AccountType, // CURRENT, SAVINGS, CREDIT_CARD, etc.
    @ColumnInfo(name = "alias") val alias: String?,                // Alias tarjeta •••• 4589
    @ColumnInfo(name = "number") val number: String?               // Número de cuenta interna
    
    // Saldo y límites
    @ColumnInfo(name = "balance_in_cents", index = true) val balanceInCents: Long,  // Saldo en minor units
    @ColumnInfo(name = "currency_code") val currencyCode: CurrencyCode,
    
    // Tipo de cuenta (efectivo, banco, tarjeta, ahorro)
    @ColumnInfo(name = "account_category") val category: AccountCategory,     // CASH, BANK, CARD, SAVINGS, LOAN, INVESTMENT
    
    // Estado y sincronización
    @ColumnInfo(name = "is_active", index = true) val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status", index = true) val syncStatus: AccountSyncStatus = AccountSyncStatus.SYNCED,
    
    // Metadatos
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,
    @ColumnInfo(name = "updated_at", index = true) val updatedAt: Long,
    
    // Balance tracking para cambios (optimistic updates)
    @ColumnInfo(name = "balance_changed_at") val balanceChangedAt: Long?  // null si no cambió
) {
    enum class AccountType(val value: Int) {
        CURRENT(0), SAVINGS(1), CREDIT_CARD(2), CHECKING(3)
    }
    
    enum class AccountCategory(val value: String) {
        CASH("cash"),           // Billetera física
        BANK("bank"),            // Cuenta bancaria
        CARD("card"),            // Tarjeta de crédito/débito
        SAVINGS("savings"),      // Ahorro
        LOAN("loan"),            // Préstamo/Deuda
        INVESTMENT("investment") // Inversión
    }
    
    enum class AccountSyncStatus(val value: String) {
        PENDING("pending"), SYNCING("syncing"), SYNCED("synced")
    }
}
```

### Category (Categoría)
```kotlin
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val isDeleted: Boolean = false,
    val needsSync: Boolean = true
)
```

- Room v8 añade `needsSync` a categorías. Room v9 lo extiende a presupuestos, metas, deudas y pagos planificados; las filas existentes migran con valor `true` para su primera subida.
- Room v9 agrega `ownerId` y clave primaria compuesta a todas las entidades. La migración 8→9 conserva los datos existentes bajo `guest`; el primer login los mueve al propietario autenticado sin mezclarlos con otras cuentas.
- Laravel guarda un UUID interno y expone `client_id` como `id`, único por usuario. Esto permite que ids predeterminados como `cat_transporte` existan para todos los usuarios sin colisionar.
- `isDeleted`/`is_deleted` es un tombstone: se sincroniza y evita que categorías borradas reaparezcan en otro dispositivo.
- El backend limita cada usuario a 200 categorías activas y conserva las eliminadas para propagación multidispositivo.

### Budget (Presupuesto)
```kotlin
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    
    @ColumnInfo(name = "category_id", index = true) val categoryId: String,      // FK a categories
    @ColumnInfo(name = "account_id", index = true) val accountId: String,        // FK a accounts (opcional para total global)
    @ColumnInfo(name = "description") val description: String?,                   // Ej: "Alimentación Mensual"
    
    // Límites y período
    @ColumnInfo(name = "limit_in_cents", index = true) val limitInCents: Long,
    @ColumnInfo(name = "spent_in_cents") val spentInCents: Long = 0L,            // Actualizado automáticamente
    @ColumnInfo(name = "period_start", typeAffinity = ColumnType.TEXT) val periodStart: String,  // ISO date YYYY-MM-DD
    @ColumnInfo(name = "period_end", typeAffinity = ColumnType.TEXT) val periodEnd: String,    // ISO date YYYY-MM-DD
    
    // Configuración
    @ColumnInfo(name = "notification_days_before") val notificationDaysBefore: Int = 3,  // Alertas días antes de fin período
    @ColumnInfo(name = "is_active", index = true) val isActive: Boolean = true,
    
    // Metadatos
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,
    @ColumnInfo(name = "updated_at", index = true) val updatedAt: Long
)
```

### Goal (Meta de Ahorro)
```kotlin
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    
    @ColumnInfo(name = "name") val name: String,                              // Ej: "Fondo de Emergencia"
    @ColumnInfo(name = "description") val description: String?,
    
    // Meta financiera
    @ColumnInfo(name = "target_amount_in_cents", index = true) val targetAmountInCents: Long,
    @ColumnInfo(name = "current_amount_in_cents") val currentAmountInCents: Long = 0L,
    
    // Configuración
    @ColumnInfo(name = "account_id", index = true) val accountId: String,     // Cuenta de destino (opcional)
    @ColumnInfo(name = "target_date", typeAffinity = ColumnType.TEXT) val targetDate: String?,  // Fecha objetivo ISO
    @ColumnInfo(name = "auto_contribute") val autoContribute: Boolean = false,   // Auto-aportar desde ingresos
    
    // Estado
    @ColumnInfo(name = "is_active", index = true) val isActive: Boolean = true,
    
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,
    @ColumnInfo(name = "updated_at", index = true) val updatedAt: Long
)
```

### RecurringPayment (Pago Recurrente)
```kotlin
@Entity(tableName = "recurring_payments")
data class RecurringPaymentEntity(
    @PrimaryKey val id: String,
    
    @ColumnInfo(name = "name") val name: String,                              // Ej: "Netflix"
    @ColumnInfo(name = "category_id", index = true) val categoryId: String,   // FK a categories
    @ColumnInfo(name = "account_id", index = true) val accountId: String,     // Cuenta de cobro
    
    // Monto y frecuencia
    @ColumnInfo(name = "amount_in_cents") val amountInCents: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: CurrencyCode,
    
    @ColumnInfo(name = "frequency_days", index = true) val frequencyDays: Int,         // 7, 30, 365
    @ColumnInfo(name = "frequency_type", index = true) val frequencyType: FrequencyType, // DAY, MONTH, YEAR
    
    // Cronograma
    @ColumnInfo(name = "next_date", typeAffinity = ColumnType.TEXT) val nextDate: String,       // ISO date YYYY-MM-DD HH:mm
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,          // Cuando se creó el recurrente
    @ColumnInfo(name = "started_at") val startedAt: Long?,                        // Cuando empezó a cobrarse
    
    // Estado
    @ColumnInfo(name = "is_active", index = true) val isActive: Boolean = true,
    @ColumnInfo(name = "status", index = true) val status: RecurringStatus = RecurringStatus.ACTIVE
    
    enum class FrequencyType(val value: Int) { DAY(0), MONTH(1), QUARTER(2), YEAR(3) }
    enum class RecurringStatus(val value: String) { ACTIVE("active"), COMPLETED("completed"), CANCELLED("cancelled") }
}
```

### PendingOperation (Operación Pendiente Sync)
```kotlin
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val id: String,                    // UUID v4
    
    // Referencia al movimiento relacionado
    @ColumnInfo(name = "transaction_id", index = true) val transactionId: String?,  // null si es crear cuenta/categoría
    @ColumnInfo(name = "account_id") val accountId: String? = null,                  // Para crear/editar cuenta
    
    // Tipo de operación
    @ColumnInfo(name = "operation_type", index = true) val operationType: OperationType,
    
    // Payload con datos completos
    @ColumnInfo(name = "payload", typeAffinity = ColumnType.TEXT) val payload: String,  // JSON con toda la entidad + comandos
    
    // Estado de sincronización
    @ColumnInfo(name = "sync_status", index = true) val syncStatus: SyncOperationStatus,
    
    // Retries y timestamps
    @ColumnInfo(name = "retry_count", index = true) val retryCount: Int = 0,
    @ColumnInfo(name = "last_retry_at", typeAffinity = ColumnType.TEXT) val lastRetryAt: String?,  // ISO timestamp
    @ColumnInfo(name = "error_message") val errorMessage: String?,                        // Último error
    
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,
    @ColumnInfo(name = "updated_at", index = true) val updatedAt: Long
) {
    enum class OperationType(val value: String) {
        TRANSACTION_CREATE("transaction_create"),
        TRANSACTION_UPDATE("transaction_update"),
        ACCOUNT_CREATE("account_create"),
        CATEGORY_CREATE("category_create"),
        BUDGET_CREATE("budget_create")
    }
    
    enum class SyncOperationStatus(val value: String) {
        PENDING("pending"),
        SYNCING("syncing"),
        SYNCED("synced"),
        FAILED("failed"),
        DELETED("deleted")  // Borrado local para evitar duplicate en remoto
    }
}
```

### UserProfile (Perfil de Usuario)
```kotlin
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,                    // UUID v4 o ID canónico Laravel
    
    // Datos básicos
    @ColumnInfo(name = "display_name", index = true) val displayName: String?,        // Ej: "Juan Pérez"
    @ColumnInfo(name = "email", index = true) val email: String,                      // Email para contacto
    @ColumnInfo(name = "phone") val phone: String?                                    // Teléfono (opcional)
    
    // Configuración
    @ColumnInfo(name = "currency_code", index = true) val currencyCode: CurrencyCode, // Moneda principal
    @ColumnInfo(name = "timezone", typeAffinity = ColumnType.TEXT) val timezone: String,  // IANA timezone
    @ColumnInfo(name = "locale", index = true) val locale: String = "es-DO",          // Localización (region)
    
    // Preferencias
    @ColumnInfo(name = "dark_mode") val darkMode: Boolean = false,                    // Modo oscuro preferido
    @ColumnInfo(name = "notifications_enabled", index = true) val notificationsEnabled: Boolean = true,
    @ColumnInfo(name = "budget_notifications_enabled") val budgetNotificationsEnabled: Boolean = true,
    
    // Estado de autenticación local (no guardar credenciales)
    @ColumnInfo(name = "auth_provider") val authProvider: AuthProvider?,               // EMAIL, GOOGLE
    @ColumnInfo(name = "remote_user_id") val remoteUserId: String?,                   // Usuario canónico Laravel
    
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,
    @ColumnInfo(name = "updated_at", index = true) val updatedAt: Long
) {
    enum class AuthProvider(val value: String) { EMAIL("email"), GOOGLE("google") }
}
```

### SyncState (Estado de Sincronización Global)
```kotlin
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    
    @ColumnInfo(name = "last_local_sync_at", typeAffinity = ColumnType.TEXT) val lastLocalSyncAt: String?,  // ISO timestamp
    @ColumnInfo(name = "last_remote_sync_at", typeAffinity = ColumnType.TEXT) val lastRemoteSyncAt: String?,  // ISO timestamp
    
    @ColumnInfo(name = "pending_operations_count") val pendingOperationsCount: Int = 0,      // Cantidad pendientes en cola
    @ColumnInfo(name = "failed_sync_count", index = true) val failedSyncCount: Int = 0,     // Fallos recientes (rolling 24h)
    
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String?,                  // Último error de sync
    
    @ColumnInfo(name = "is_connected") val isConnected: Boolean = false,                    // ¿Conectado a internet ahora?
    
    @ColumnInfo(name = "created_at", index = true) val createdAt: Long,
    @ColumnInfo(name = "updated_at", index = true) val updatedAt: Long
)
```

---

## 2. Índices Clave

```kotlin
@Index("transactions_amount_category_account_date")
@ColumnInfo(name = "amount_in_cents") val amountInCents: Long,
@ColumnInfo(name = "category_id", index = true) val categoryId: String,
@ColumnInfo(name = "account_id", index = true) val accountId: String,
@ColumnInfo(name = "date", index = true) val date: java.time.LocalDate,

@Index("transactions_sync_status_created")
@ColumnInfo(name = "sync_status", index = true) val syncStatus: SyncStatus,
@ColumnInfo(name = "created_at", index = true) val createdAt: Long,

@Index("categories_parent_type")
@ColumnInfo(name = "parent_id", index = true) val parentId: String?,
@ColumnInfo(name = "type") val type: CategoryType,
```

---

## 3. Migraciones (Declarativas desde V1)

```kotlin
// No usar migraciones destructivas
@Migration(
    version = 1,
    fromDatabaseVersion = 0,
    name = "Initial_Migration"
)
object InitialMigration {
    
    @MigrationSchema
    object Schema {
        fun create(): List<Statement> = listOf(
            // ... definiciones de las tablas arriba ...
        )
    }
}

// Si necesitas agregar columnas:
@Migration(version = 2, fromDatabaseVersion = 1, name = "AddReceiptFileId")
object AddReceiptFileId {
    
    @MigrationSchema
    object Schema {
        fun alter(): List<Statement> = listOf(
            // ... ALTER TABLE transactions ADD COLUMN receipt_file_id TEXT ...
        )
    }
}
```

---

**Última actualización:** 2026-07-11  
**Responsable:** Backend Team  
**Revisar:** Cada entidad nueva o cambio de esquema
