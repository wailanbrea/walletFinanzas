@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.CreateAccountRequest
import com.bsolutions.wallet.core.network.CreateCategoryRequest
import com.bsolutions.wallet.core.network.CreateTransactionRequest
import com.bsolutions.wallet.core.network.AccountDto
import com.bsolutions.wallet.core.network.BudgetSyncDto
import com.bsolutions.wallet.core.network.GoalSyncDto
import com.bsolutions.wallet.core.network.DebtSyncDto
import com.bsolutions.wallet.core.network.PlannedPaymentSyncDto
import com.bsolutions.wallet.core.network.WalletApi
import com.bsolutions.wallet.core.database.WalletOwnerScope
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.local.dao.AccountDao
import com.bsolutions.wallet.data.local.dao.CategoryDao
import com.bsolutions.wallet.data.local.dao.PendingOperationDao
import com.bsolutions.wallet.data.local.dao.TransactionDao
import com.bsolutions.wallet.data.local.dao.BudgetDao
import com.bsolutions.wallet.data.local.dao.GoalDao
import com.bsolutions.wallet.data.local.dao.DebtDao
import com.bsolutions.wallet.data.local.dao.PlannedPaymentDao
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.data.local.entity.PendingOperationEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import com.bsolutions.wallet.data.local.entity.BudgetEntity
import com.bsolutions.wallet.data.local.entity.GoalEntity
import com.bsolutions.wallet.data.local.entity.DebtEntity
import com.bsolutions.wallet.data.local.entity.PlannedPaymentEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

sealed interface SyncOutcome {
    /** Sincronizado: [pushed] operaciones subidas, [pulled] filas traídas. */
    data class Success(val pushed: Int, val pulled: Int) : SyncOutcome
    /** No hay sesión: la app queda local-only (degradación limpia). */
    data object NoSession : SyncOutcome
    data class Error(val message: String) : SyncOutcome
}

/**
 * Sincronización offline-first con el backend Laravel. Room es la fuente de verdad
 * local; el backend es réplica. Push idempotente (id de cliente = idempotency_key) y
 * pull incremental por updated_since. Solo corre con sesión Sanctum activa.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: WalletApi,
    private val session: WalletSessionStore,
    private val pendingOps: PendingOperationDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val plannedPaymentDao: PlannedPaymentDao,
    private val ownerScope: WalletOwnerScope,
    private val preferences: UserPreferencesRepository,
    private val gson: Gson
) {
    val pendingCount: Flow<Int> = ownerScope.ownerId.flatMapLatest { ownerId ->
        combine(
            pendingOps.count(ownerId),
            categoryDao.countNeedingSync(ownerId),
            budgetDao.countNeedingSync(ownerId),
            goalDao.countNeedingSync(ownerId),
            debtDao.countNeedingSync(ownerId),
            plannedPaymentDao.countNeedingSync(ownerId)
        ) { counts -> counts.sum() }
    }

    suspend fun sync(): SyncOutcome {
        if (session.token.isNullOrBlank()) return SyncOutcome.NoSession
        return try {
            backfillLegacyOperations()
            // Las categorías se suben primero porque una transacción solo puede
            // referenciar una categoría activa del mismo usuario en el backend.
            val pushed = pushCategories() + push() + pushFinancialPlans()
            val pulled = pull()
            SyncOutcome.Success(pushed, pulled)
        } catch (e: SessionExpiredException) {
            SyncOutcome.NoSession
        } catch (e: IOException) {
            SyncOutcome.Error("Sin conexión con el servidor.")
        } catch (e: Exception) {
            SyncOutcome.Error(e.message ?: "Error de sincronización.")
        }
    }

    /**
     * Las cuentas y los movimientos solo se encolan al crearse. Todo lo que el usuario ya
     * tenía antes de que existiera la cola nunca se subió, así que en un segundo teléfono
     * no aparecía nada. Una sola vez por propietario se encola lo que falte; el push es
     * idempotente (el id del cliente es la clave), de modo que reencolar no duplica nada.
     */
    private suspend fun backfillLegacyOperations() {
        if (preferences.isSyncBackfillDone()) return
        val ownerId = ownerScope.currentOwnerId()
        for (account in accountDao.getAllAccountsOnce(ownerId)) {
            pendingOps.insert(accountOp(gson, account))
        }
        for (transaction in transactionDao.getAllTransactionsOnce(ownerId)) {
            pendingOps.insert(transactionOp(gson, transaction))
        }
        preferences.markSyncBackfillDone()
    }

    // ---------- PUSH ----------

    private suspend fun pushCategories(): Int {
        var pushed = 0
        val ownerId = ownerScope.currentOwnerId()
        for (category in categoryDao.getCategoriesNeedingSync(ownerId)) {
            try {
                api.createCategory(
                    CreateCategoryRequest(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        colorHex = category.colorHex,
                        isDeleted = category.isDeleted
                    )
                )
            } catch (exception: HttpException) {
                if (exception.code() == 401) {
                    session.clear()
                    throw SessionExpiredException()
                }
                throw exception
            }
            categoryDao.markCategorySynced(ownerId, category.id)
            pushed++
        }
        return pushed
    }

    private suspend fun push(): Int {
        var pushed = 0
        val ownerId = ownerScope.currentOwnerId()
        for (op in pendingOps.getAll(ownerId)) {
            try {
                when (op.entityType) {
                    "ACCOUNT" -> pushAccount(op)
                    "TRANSACTION" -> pushTransaction(op)
                }
                pendingOps.delete(ownerId, op.id)
                pushed++
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> { session.clear(); throw SessionExpiredException() }
                    // 4xx (salvo 409 que es "ya existe" = éxito) => operación inválida
                    409 -> { pendingOps.delete(ownerId, op.id); pushed++ }
                    in 400..499 -> pendingOps.bumpAttempts(ownerId, op.id)
                    else -> pendingOps.bumpAttempts(ownerId, op.id) // 5xx: reintentar luego
                }
            }
        }
        // Descarta lo que el servidor rechaza repetidamente (evita bucles).
        pendingOps.purgeFailed(ownerId, MAX_ATTEMPTS)
        return pushed
    }

    private suspend fun pushFinancialPlans(): Int {
        val ownerId = ownerScope.currentOwnerId()
        var pushed = 0
        try {
            for (entity in budgetDao.getNeedingSync(ownerId)) {
                api.upsertBudget(entity.toSyncDto())
                budgetDao.markSynced(ownerId, entity.id)
                pushed++
            }
            for (entity in goalDao.getNeedingSync(ownerId)) {
                api.upsertGoal(entity.toSyncDto())
                goalDao.markSynced(ownerId, entity.id)
                pushed++
            }
            for (entity in debtDao.getNeedingSync(ownerId)) {
                api.upsertDebt(entity.toSyncDto())
                debtDao.markSynced(ownerId, entity.id)
                pushed++
            }
            for (entity in plannedPaymentDao.getNeedingSync(ownerId)) {
                api.upsertPlannedPayment(entity.toSyncDto())
                plannedPaymentDao.markSynced(ownerId, entity.id)
                pushed++
            }
        } catch (exception: HttpException) {
            if (exception.code() == 401) {
                session.clear()
                throw SessionExpiredException()
            }
            throw exception
        }
        return pushed
    }

    private suspend fun pushAccount(op: PendingOperationEntity) {
        val a = gson.fromJson(op.payload, AccountEntity::class.java)
        api.createAccount(a.toCreateAccountRequest())
    }

    private suspend fun pushTransaction(op: PendingOperationEntity) {
        val t = gson.fromJson(op.payload, TransactionEntity::class.java)
        // El backend usa amount con signo; la app guarda positivo + type.
        val signedAmount = if (t.type == "INCOME") abs(t.amount) else -abs(t.amount)
        val validCategoryId = t.categoryId.takeIf { id ->
            id.isNotBlank() && categoryDao.getCategoryById(t.ownerId, id) != null
        }
        api.createTransaction(
            CreateTransactionRequest(
                idempotencyKey = t.id,
                accountId = t.accountId,
                amount = signedAmount,
                currency = t.currency,
                description = t.note.ifBlank { null },
                categoryId = validCategoryId,
                timestamp = isoUtc(t.date),
                status = "completed"
            )
        )
    }

    // ---------- PULL (delta) ----------

    private suspend fun pull(): Int {
        var pulled = 0
        val ownerId = ownerScope.currentOwnerId()

        var cursor: String? = null
        do {
            val page = api.pullCategories(updatedSince = null, cursor = cursor)
            for (dto in page.data) {
                categoryDao.insertCategory(
                    CategoryEntity(
                        id = dto.id,
                        name = dto.name,
                        icon = dto.icon,
                        colorHex = dto.colorHex,
                        isDeleted = dto.isDeleted,
                        needsSync = false,
                        ownerId = ownerId
                    )
                )
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        // Una misma cuenta real puede existir con ids distintos en el telefono y en el
        // servidor (se creo por separado en cada uno). Insertarla a ciegas la duplicaba
        // y falseaba el Balance Total, asi que primero se busca su equivalente local y,
        // si aparece, se anota para redirigir hacia ella los movimientos que llegan.
        val remoteToLocalAccount = mutableMapOf<String, String>()
        cursor = null
        do {
            val page = api.pullAccounts(updatedSince = null, cursor = cursor)
            for (dto in page.data) {
                // Room sigue siendo autoritativo: una fila local, incluso eliminada,
                // nunca se pisa ni se resucita durante el pull.
                if (accountDao.getAccountByIdIncludingDeleted(ownerId, dto.id) == null) {
                    val twin = accountDao.getAllAccountsOnce(ownerId)
                        .firstOrNull { it.matchesSameRealAccount(dto) }
                    if (twin != null) {
                        remoteToLocalAccount[dto.id] = twin.id
                    } else {
                        accountDao.insertAccount(dto.toAccountEntity(ownerId))
                    }
                }
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        cursor = null
        do {
            val page = api.pullTransactions(updatedSince = null, cursor = cursor)
            for (dto in page.data) {
                val positive = abs(dto.amount)
                val type = if (dto.amount < 0) "EXPENSE" else "INCOME"
                val validCategoryId = dto.categoryId?.takeIf { id ->
                    categoryDao.getCategoryById(ownerId, id) != null
                }.orEmpty()
                if (transactionDao.getTransactionByIdIncludingDeleted(ownerId, dto.idempotencyKey ?: dto.id) == null) transactionDao.insertTransaction(
                    TransactionEntity(
                        id = dto.idempotencyKey ?: dto.id,
                        // Si la cuenta remota se reconcilio con una local, el movimiento
                        // cuelga de la local: si no, quedaria apuntando a una cuenta
                        // que nunca se inserto.
                        accountId = remoteToLocalAccount[dto.accountId] ?: dto.accountId,
                        amount = positive,
                        type = type,
                        categoryId = validCategoryId,
                        date = parseIso(dto.timestamp),
                        note = dto.description.orEmpty(),
                        currency = dto.currency,
                        ownerId = ownerId
                    )
                )
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        cursor = null
        do {
            val page = api.pullBudgets(updatedSince = null, cursor = cursor)
            page.data.forEach { dto ->
                budgetDao.insertBudget(
                    BudgetEntity(
                        id = dto.id,
                        categoryId = dto.categoryId,
                        limitAmount = dto.limitAmount,
                        spentAmount = dto.spentAmount,
                        period = dto.period,
                        isDeleted = dto.isDeleted,
                        needsSync = false,
                        ownerId = ownerId
                    )
                )
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        cursor = null
        do {
            val page = api.pullGoals(updatedSince = null, cursor = cursor)
            page.data.forEach { dto ->
                goalDao.insertGoal(
                    GoalEntity(
                        id = dto.id,
                        name = dto.name,
                        icon = dto.icon,
                        targetAmount = dto.targetAmount,
                        savedAmount = dto.savedAmount,
                        targetDate = dto.targetDate,
                        isCompleted = dto.isCompleted,
                        isDeleted = dto.isDeleted,
                        needsSync = false,
                        ownerId = ownerId
                    )
                )
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        cursor = null
        do {
            val page = api.pullDebts(updatedSince = null, cursor = cursor)
            page.data.forEach { dto ->
                debtDao.insertDebt(
                    DebtEntity(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description,
                        direction = dto.direction,
                        totalAmount = dto.totalAmount,
                        paidAmount = dto.paidAmount,
                        dueDate = dto.dueDate,
                        isClosed = dto.isClosed,
                        isDeleted = dto.isDeleted,
                        needsSync = false,
                        ownerId = ownerId
                    )
                )
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        cursor = null
        do {
            val page = api.pullPlannedPayments(updatedSince = null, cursor = cursor)
            page.data.forEach { dto ->
                plannedPaymentDao.insertPlannedPayment(
                    PlannedPaymentEntity(
                        id = dto.id,
                        name = dto.name,
                        accountId = dto.accountId,
                        categoryId = dto.categoryId,
                        amount = dto.amount,
                        type = dto.type,
                        frequency = dto.frequency,
                        nextDueDate = dto.nextDueDate,
                        isActive = dto.isActive,
                        isDeleted = dto.isDeleted,
                        needsSync = false,
                        ownerId = ownerId
                    )
                )
                pulled++
            }
            cursor = page.meta?.nextCursor
        } while (cursor != null)

        return pulled
    }

    private class SessionExpiredException : Exception()

    companion object {
        const val MAX_ATTEMPTS = 5

        private val isoFormat: SimpleDateFormat
            get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun isoUtc(millis: Long): String = isoFormat.format(Date(millis))

        fun parseIso(value: String?): Long =
            value?.let { runCatching { isoFormat.parse(it)?.time }.getOrNull() } ?: System.currentTimeMillis()

        /** Snapshot JSON para encolar una cuenta. */
        fun accountOp(gson: Gson, account: AccountEntity): PendingOperationEntity =
            PendingOperationEntity(
                id = "ACCOUNT:${account.id}",
                entityType = "ACCOUNT",
                entityId = account.id,
                payload = gson.toJson(account),
                createdAt = System.currentTimeMillis(),
                ownerId = account.ownerId
            )

        /** Snapshot JSON para encolar un movimiento. */
        fun transactionOp(gson: Gson, transaction: TransactionEntity): PendingOperationEntity =
            PendingOperationEntity(
                id = "TRANSACTION:${transaction.id}",
                entityType = "TRANSACTION",
                entityId = transaction.id,
                payload = gson.toJson(transaction),
                createdAt = System.currentTimeMillis(),
                ownerId = transaction.ownerId
            )
    }
}

internal fun AccountEntity.toCreateAccountRequest() = CreateAccountRequest(
    id = id,
    name = name,
    balance = balance,
    currency = currency,
    institutionName = institutionName,
    countryCode = countryCode,
    cardLastFour = cardLastFour,
    type = type,
    creditLimit = creditLimit
)

/**
 * Decide si una cuenta que llega del servidor y una local son la misma cuenta real
 * creada por separado en cada dispositivo.
 *
 * Se exige que coincida la divisa y, ademas, una de dos evidencias fuertes: los cuatro
 * digitos de la tarjeta en la misma institucion, o el mismo nombre en la misma
 * institucion. Emparejar solo por nombre seria temerario —"Ahorros" existe en varios
 * bancos— y equivocarse aqui mezcla el dinero de dos cuentas distintas, que es peor
 * que dejar un duplicado a la vista.
 */
internal fun AccountEntity.matchesSameRealAccount(remote: AccountDto): Boolean {
    if (isDeleted || !currency.equals(remote.currency, ignoreCase = true)) return false

    val sameInstitution = normalizeForMatch(institutionName) == normalizeForMatch(remote.institutionName)
    if (!sameInstitution) return false

    val localDigits = cardLastFour?.takeIf { it.isNotBlank() }
    val remoteDigits = remote.cardLastFour?.takeIf { it.isNotBlank() }
    if (localDigits != null && remoteDigits != null) return localDigits == remoteDigits

    // Sin digitos que comparar, el nombre dentro de la misma institucion es lo unico
    // que queda. Una institucion vacia a ambos lados no basta como evidencia.
    if (normalizeForMatch(institutionName).isEmpty()) return false
    return normalizeForMatch(name) == normalizeForMatch(remote.name)
}

private fun normalizeForMatch(value: String?): String =
    value?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty()

internal fun AccountDto.toAccountEntity(ownerId: String) = AccountEntity(
    id = id,
    name = name,
    // Un backend sin la migracion de type no manda la clave. Traer un limite de
    // credito solo tiene sentido en una tarjeta, asi que eso basta para reconocerla;
    // sin esa pista, toda cuenta remota era bancaria antes de que existiera el campo.
    // Importa acertar: una tarjeta marcada como banco suma su saldo al Balance Total
    // como si fuera dinero propio, cuando es credito del banco.
    type = type ?: if (creditLimit != null) "CREDIT_CARD" else "BANK",
    balance = balance,
    currency = currency,
    countryCode = countryCode,
    institutionName = institutionName,
    cardLastFour = cardLastFour,
    ownerId = ownerId,
    creditLimit = creditLimit
)

private fun BudgetEntity.toSyncDto() = BudgetSyncDto(
    id = id,
    categoryId = categoryId,
    limitAmount = limitAmount,
    spentAmount = spentAmount,
    period = period,
    isDeleted = isDeleted
)

private fun GoalEntity.toSyncDto() = GoalSyncDto(
    id = id,
    name = name,
    icon = icon,
    targetAmount = targetAmount,
    savedAmount = savedAmount,
    targetDate = targetDate,
    isCompleted = isCompleted,
    isDeleted = isDeleted
)

private fun DebtEntity.toSyncDto() = DebtSyncDto(
    id = id,
    name = name,
    description = description,
    direction = direction,
    totalAmount = totalAmount,
    paidAmount = paidAmount,
    dueDate = dueDate,
    isClosed = isClosed,
    isDeleted = isDeleted
)

private fun PlannedPaymentEntity.toSyncDto() = PlannedPaymentSyncDto(
    id = id,
    name = name,
    accountId = accountId,
    categoryId = categoryId,
    amount = amount,
    type = type,
    frequency = frequency,
    nextDueDate = nextDueDate,
    isActive = isActive,
    isDeleted = isDeleted
)
