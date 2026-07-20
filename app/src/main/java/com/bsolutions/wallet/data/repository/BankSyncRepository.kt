package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.AttemptData
import com.bsolutions.wallet.core.network.ConnectSessionData
import com.bsolutions.wallet.core.network.ConnectSessionRequest
import com.bsolutions.wallet.core.network.ConsentData
import com.bsolutions.wallet.core.network.CreateCustomerRequest
import com.bsolutions.wallet.core.network.CustomerIdentifier
import com.bsolutions.wallet.core.network.ProviderDto
import com.bsolutions.wallet.core.network.ProviderRef
import com.bsolutions.wallet.core.network.SaltEdgeApi
import com.bsolutions.wallet.data.local.dao.BankConnectionDao
import com.bsolutions.wallet.data.local.entity.BankConnectionEntity
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToLong

data class BankSyncResult(
    val connections: Int,
    val accountsImported: Int,
    val transactionsImported: Int
)

/**
 * Sincronización bancaria vía Salt Edge (Etapa A: sandbox con fake providers).
 * Room sigue siendo la fuente de verdad: las cuentas/movimientos importados usan
 * ids deterministas ("se_...") para que re-sincronizar sea idempotente (REPLACE).
 */
@Singleton
class BankSyncRepository @Inject constructor(
    private val api: SaltEdgeApi,
    private val bankConnectionDao: BankConnectionDao,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val prefs: UserPreferencesRepository
) {

    fun getConnections(): Flow<List<BankConnectionEntity>> = bankConnectionDao.getAll()

    /** Crea (una sola vez) el customer en Salt Edge y devuelve su id. */
    private suspend fun ensureCustomer(): String {
        prefs.getSaltEdgeCustomerId()?.let { return it }
        val identifier = "wallet-android-${UUID.randomUUID()}"
        val customer = api.createCustomer(CreateCustomerRequest(CustomerIdentifier(identifier))).data
        prefs.setSaltEdgeCustomerId(customer.id)
        return customer.id
    }

    /** Bancos disponibles en Salt Edge para un país (DO, XF = bancos de prueba…). */
    suspend fun getProviders(countryCode: String): List<ProviderDto> =
        api.listProviders(countryCode).data.filter { it.status == "active" || it.status == null }

    /**
     * URL del widget de conexión. Con [providerCode] el widget salta la selección
     * de banco y va directo al login del proveedor elegido en la app.
     */
    suspend fun createConnectUrl(providerCode: String? = null): String {
        val customerId = ensureCustomer()
        val session = api.createConnectSession(
            ConnectSessionRequest(
                ConnectSessionData(
                    customerId = customerId,
                    consent = ConsentData(),
                    attempt = AttemptData(returnTo = "walletfinanzas://saltedge"),
                    provider = providerCode?.let { ProviderRef(it) }
                )
            )
        ).data
        return session.connectUrl
    }

    /**
     * Trae conexiones, cuentas y movimientos desde Salt Edge y los materializa
     * en Room. Idempotente: mismo id remoto → misma fila local.
     */
    suspend fun refresh(): BankSyncResult {
        val customerId = prefs.getSaltEdgeCustomerId() ?: return BankSyncResult(0, 0, 0)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val connections = api.listConnections(customerId).data
        bankConnectionDao.upsertAll(
            connections.map {
                BankConnectionEntity(
                    id = it.id,
                    providerName = it.providerName ?: it.providerCode ?: "Banco",
                    providerCode = it.providerCode.orEmpty(),
                    countryCode = it.countryCode.orEmpty(),
                    status = it.status.orEmpty(),
                    lastSyncAt = System.currentTimeMillis()
                )
            }
        )

        var accountsImported = 0
        var transactionsImported = 0

        connections.forEach { connection ->
            val accounts = api.listAccounts(connection.id).data
            accounts.forEach { remote ->
                val localType = when (remote.nature) {
                    "card", "credit_card" -> "CREDIT_CARD"
                    "debit_card" -> "DEBIT_CARD"
                    "savings" -> "SAVINGS"
                    else -> "BANK"
                }
                accountRepository.addAccount(
                    Account(
                        id = "se_${remote.id}",
                        name = remote.name ?: connection.providerName ?: "Cuenta bancaria",
                        type = localType,
                        balance = ((remote.balance ?: 0.0) * 100).roundToLong(),
                        currency = remote.currencyCode ?: "USD",
                        countryCode = connection.countryCode ?: "OTHER",
                        institutionName = connection.providerName
                    )
                )
                accountsImported++

                // v6 exige account_id: los movimientos se piden cuenta por cuenta
                val transactions = api.listTransactions(connection.id, remote.id).data
                transactions.forEach tx@{ tx ->
                    val amount = tx.amount ?: return@tx
                    val dateMillis = tx.madeOn?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() }
                        ?: System.currentTimeMillis()
                    transactionRepository.addTransaction(
                        Transaction(
                            id = "se_${tx.id}",
                            accountId = "se_${remote.id}",
                            amount = (abs(amount) * 100).roundToLong(),
                            type = if (amount < 0) "EXPENSE" else "INCOME",
                            categoryId = "",
                            date = dateMillis,
                            note = tx.description ?: tx.category ?: "",
                            currency = remote.currencyCode ?: tx.currencyCode ?: "USD"
                        )
                    )
                    transactionsImported++
                }
            }
        }

        return BankSyncResult(connections.size, accountsImported, transactionsImported)
    }

    suspend fun removeConnection(id: String) {
        bankConnectionDao.delete(id)
    }
}
