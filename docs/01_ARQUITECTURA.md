# Arquitectura de la Aplicación - Wallet Finanzas Personales

## 1. Patrones de Arquitectura

### Clean Architecture + MVI/MVVM
```kotlin
┌─────────────────────────────────────────────────────────────┐
│                      PRESENTATION                             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐   │
│  │ Screens     │ │ ViewModels  │ │ NavGraph            │   │
│  │ (Compose)   │ │ (StateFlow) │ │ (Navigation)        │   │
│  └─────────────┘ └─────────────┘ └─────────────────────┘   │
│         ↑                    ↑                            │
│         │ UseCases           │ Repositories                │
├─────────┼────────────────────┼───────────────────────────┤
│                   DOMAIN LAYER                                    │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐   │
│  │ Models      │ │ Repositories│ │ UseCases            │   │
│  │ (Data Classes) │ │ (Interfaces)│ │ (Business Logic)  │   │
│  └─────────────┘ └─────────────┘ └─────────────────────┘   │
├─────────┴────────────────────┴───────────────────────────┤
│                      DATA LAYER                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐   │
│  │ Local (Room)│ │ Remote API │ │ Sync Manager        │   │
│  │ Entities    │ │ DTOs + Mappers│ │ WorkManager      │   │
│  └─────────────┘ └─────────────┘ └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Principios Clave

1. **Separación de Preocupaciones**
   - UI solo muestra estado y emite eventos
   - ViewModels exponen StateFlow (no lógica de negocio)
   - UseCases contienen reglas de negocio puros
   - Repositorios coordinan entre local y remoto

2. **Offline-First**
   - Room es la fuente de verdad principal
   - Toda operación se guarda local primero
   - Sync remota ocurre después vía WorkManager

3. **Unidireccionalidad (MVI)**
   ```kotlin
   sealed interface Event {
       data class OnCreateAccount(val account: CreateAccountCommand) : Event
       data class OnTransactionCreated(val transaction: Transaction) : Event
   }
   
   sealed interface State {
       object Idle : State
       data class Loading(val message: String = "") : State
       data class Success(val account: Account) : State
       data class Error(val cause: Throwable) : State
   }
   ```

---

## 2. Estructura de Directorios

```
com.bsolutions.wallet/
├── WalletApp.kt                 # Entry point
├── core/                         # Módulos reutilizables
│   ├── common/                   # Utilidades compartidas
│   │   └── extensions/
│   ├── database/                 # Abstracción de bases de datos
│   ├── datastore/                # DataStore preferences
│   ├── designsystem/             # Colores, tipografía, componentes
│   ├── network/                  # Retrofit interceptors
│   ├── security/                 # Encriptación, tokens
│   ├── sync/                     # Sincronización entre capas
│   ├── utils/                    # Utilidades genéricas
│   └── validation/               # Validadores reutilizables
│
├── data/                         # Implementaciones de datos
│   ├── local/
│   │   ├── dao/                  # Data Access Objects (Room)
│   │   ├── entity/               # Room entities con @Entity
│   │   └── mapper/               # Mappers DTO ↔ Entity
│   ├── remote/
│   │   ├── api/                  # Interfaces REST/SOAP
│   │   ├── dto/                  # Data Transfer Objects
│   │   └── mapper/
│   └── repository/               # Implementaciones de repositorios
│
├── domain/                       # Lógica de negocio pura
│   ├── model/                    # Domain models (no Room)
│   ├── repository/               # Interfaces de repositorios
│   └── usecase/                  # Casos de uso (business logic)
│
└── presentation/                 # Capa UI
    ├── auth/
    │   ├── login/
    │   ├── register/
    │   └── forgot-password/
    ├── onboarding/
    ├── dashboard/
    ├── accounts/
    ├── transactions/
    ├── categories/
    ├── budgets/
    ├── goals/
    ├── recurring/
    ├── reports/
    ├── bankconnection/
    ├── importcsv/
    ├── settings/
    └── shared/                   # Components compartidos
```

---

## 3. Dependencias Obligatorias

### build.gradle (app)
```gradle
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'  // Para Room y Hilt
    id 'dagger.hilt.android.plugin'
}

dependencies {
    // AndroidX Core
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.activity:activity-compose:1.8.2'
    
    // Compose BOM
    val composeBom = platform('androidx.compose:compose-bom:2024.02.00')
    implementation composeBom
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.navigation:navigation-compose:2.7.6'
    
    // Hilt
    implementation 'com.google.dagger:hilt-android:2.50'
    kapt 'com.google.dagger:hilt-compiler:2.50'
    
    // Room
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'
    
    // WorkManager (Sync)
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
    
    // Identidad remota: Retrofit/OkHttp contra Laravel Sanctum.
    // El token se almacena cifrado y nunca se envía a hosts de terceros.
    
    // Retrofit + OkHttp
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-kotlinx-serialization:2.9.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    
    // Kotlinx Serialization
    implementation 'io.ktor:ktor-serialization-kotlinx-json:2.3.7'
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3"
    
    // Coil (Image loading)
    implementation 'io.coil-kt:coil-compose:2.5.0'
    
    // Charts (Vico o simples)
    implementation 'com.github.franmontena:pullrequests-ui:0.7.2'  // O Vico
    implementation 'co.yarolev:chart:3.1.0'  // Chart para gráficos
    
    // DataStore
    implementation "androidx.datastore:datastore-preferences:1.0.0"
    
    // Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.8.0'
    androidTestImplementation composeBom
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

---

## 4. Patrones de Implementación

### Repository Pattern (Local + Remote)
```kotlin
@InstallIn(SingletonComponent::class)
@HiltModules.Submodule(name = "AccountRepository")
interface AccountRepositoryModule {
    @Binds
    fun bindAccountRepository(
        LocalAccountRepositoryImpl: LocalAccountRepositoryImpl
    ): AccountRepository
}

interface AccountRepository {
    suspend fun create(account: CreateAccountCommand): Result<Account>
    fun observeAccounts(): Flow<List<Account>>
    suspend fun update(account: UpdateAccountCommand): Result<Unit>
}

class LocalAccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao
) : AccountRepository {
    
    override suspend fun create(command: CreateAccountCommand): Result<Account> {
        // Guardar en Room primero (offline-first)
        return try {
            val entity = command.toEntity()
            accountDao.insert(entity)
            Success(accountDao.getById(command.id())!!)
        } catch (e: Exception) {
            Failure(e)
        }
    }
    
    override fun observeAccounts(): Flow<List<Account>> = 
        accountDao.getAllAsFlow().map { it.map(Account::fromEntity) }
}
```

### UseCase Pattern
```kotlin
@HiltInstallIn(SingletonComponent::class)
interface CreateExpenseUseCaseModule {
    @Binds
    fun bindCreateExpenseUseCase(
        LocalCreateExpenseUseCaseImpl: LocalCreateExpenseUseCaseImpl
    ): CreateExpenseUseCase
}

interface CreateExpenseUseCase {
    suspend operator fun invoke(command: CreateExpenseCommand): Result<Unit>
}

class LocalCreateExpenseUseCaseImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val costGuardMonitor: CostGuardMonitor  // Verificar límites
) : CreateExpenseUseCase {
    
    override suspend operator fun invoke(command: CreateExpenseCommand): Result<Unit> {
        // 1. Validar con Cost Guard
        costGuardMonitor.checkLimits()
        
        // 2. Crear transacción
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            amount = command.amount().toMinorUnits(command.currency()),
            description = command.description(),
            merchant = command.merchant(),
            category = command.category(),
            account = command.account(),
            date = command.date(),
            type = TransactionType.EXPENSE,
            source = TransactionSource.MANUAL,
            syncStatus = SyncStatus.PENDING.value  // Pendiente de sync
        )
        
        transactionDao.insert(entity)
        
        // 3. Crear PendingOperation para sync
        val pendingOperation = PendingOperationEntity(
            transactionId = entity.id,
            operationType = "TRANSACTION_CREATE",
            payload = command.toJson(),
            status = SyncStatus.PENDING.value
        )
        pendingOperationDao.insert(pendingOperation)
        
        // 4. Guardar en Cost Guard
        costGuardMonitor.trackTransactionCreated()
        
        return Success(Unit)
    }
}
```

---

## 5. Sincronización Offline-First

### Estado implementado (22/07/2026)

`SyncRepository` ejecuta el ciclo en este orden: categorías locales pendientes → cola de cuentas/movimientos → presupuestos, metas, deudas y pagos planificados pendientes → pull de categorías → cuentas → movimientos → planificación financiera. El orden evita que una referencia llegue a Laravel antes que su cuenta o categoría. Room sigue siendo la fuente local; Laravel aplica aislamiento por usuario, upsert idempotente, cursor pagination y tombstones.

Room v9 agrega `ownerId` a todas las tablas y usa claves primarias compuestas `(ownerId, id)`. `WalletOwnerScope` selecciona dinámicamente la partición invitado o `user:<id>`; `RoomLocalDataIsolation` reclama los datos invitados en una transacción al autenticarse. DataStore aplica el mismo propietario a perfil, seguridad, país financiero, dashboard, Salt Edge y reglas personalizadas.

### Flow de Sync
```kotlin
class SyncManager @Inject constructor(
    private val transactionDao: TransactionDao,
    private val syncStateDao: SyncStateDao,
    private val workManager: WorkManager
) {
    
    fun queueSync() {
        // Marcar todos los pendientes como 'syncing'
        syncStateDao.updateStatus(SyncStatus.SYNCING.value)
        
        // Agregar job a WorkManager
        val requestBuilder = workManager.enqueue periodic(PeriodicWorkRequest.
            builder("WalletSync", 12.hours).build()) {
            setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30.seconds, WORK_BACKOFF_SCALES)
            // Lógica de sync aquí
        }
    }
}

@TypeConverters(
    String::class, UUID::class, Long::class, 
    CurrencyCode::class, SyncStatus::class
)
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long,
    @ColumnInfo(name = "pending_count") val pendingCount: Int = 0,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus
) {
    enum class SyncStatus(val value: String) {
        PENDING("pending"),
        SYNCING("syncing"),
        SYNCED("synced"),
        FAILED("failed"),
        CONFLICT("conflict")
    }
}
```

---

## 6. Principios de Datos

### Decimal para Dinero
```kotlin
// Usar String o Long (minor units) en lugar de Double/Float
@Entity(tableName = "transactions")
data class TransactionEntity(
    val amount: Long,  // Minor units: $100.50 = 10050 cents
    @ColumnInfo(name = "currency_code") val currencyCode: CurrencyCode,
    
    companion object {
        fun toMinorUnits(amountInCents: BigDecimal): Long = 
            amountInCents.multiply(BigDecimal(100)).toLong()
        
        fun toCurrency(amountInMinorUnits: Long, currency: String): BigDecimal = 
            BigDecimal(amountInMinorUnits).divide(BigDecimal(100))
    }
}
```

### Timestamps en UTC
```kotlin
val utcNow = java.time.ZoneOffset.UTC.toInstant()
val createdAt = java.time.Instant.now(ZoneOffset.UTC)
```

### Foreign Keys
```kotlin
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "category_id", foreignKey = ForeignKey(
        entity = CategoryEntity::class,
        columns = arrayOf("id"),
        references = arrayOf("id"),
        onDelete = ForeignKey.CASCADE
    )) val categoryId: String,
    // ...
)
```

---

## 7. Seguridad Móvil

### No Guardar Secretos en APK
- ✅ Tokens Sanctum con capacidad, expiración y rotación por dispositivo
- ✅ Token Sanctum en preferencias cifradas mediante Android Keystore
- ✅ Encriptar datos sensibles con Android Keystore

### Logs Seguros
```kotlin
// PROHIBIDO:
Log.d("Wallet", "user_email=${user.email}, balance=$${balance}")

// CORRECTO:
Log.d("Wallet", "Transaction created")  // Sin datos sensibles
```

---

## 8. Build y Release

### Gradle Properties
```properties
# Para release build
buildConfigField "boolean", "BANK_SYNC_ENABLED", "false"
debuggable false
minifyEnabled true
proguardFiles 'proguard-rules.pro'
```

### ProGuard Rules (Seguridad)
```proguard
# Encriptar datos sensibles
-keep class com.bsolutions.wallet.domain.model.** { *; }
-dontwarn android.os.Build\$**
 
# Mantener Hilt
-keepnames -dagger.*
-keep class * implements dagger.*

# Room
-keep class androidx.room.** { *; }
```

---

**Última actualización:** 2026-07-11  
**Responsable:** Arquitecto  
**Revisar:** Cada cambio significativo de arquitectura
