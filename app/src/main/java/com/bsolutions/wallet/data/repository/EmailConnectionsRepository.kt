package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.EmailCandidateDto
import com.bsolutions.wallet.core.network.EmailConnectionDto
import com.bsolutions.wallet.core.network.EmailCandidateReviewRequest
import com.bsolutions.wallet.core.network.EmailSyncDto
import com.bsolutions.wallet.core.network.WalletApi
import kotlinx.coroutines.delay
import retrofit2.HttpException

enum class EmailProvider(val apiValue: String) {
    GMAIL("gmail"),
    MICROSOFT("microsoft");

    companion object {
        fun fromApi(value: String): EmailProvider =
            entries.firstOrNull { it.apiValue == value.lowercase() }
                ?: throw IllegalArgumentException("Proveedor de correo no soportado: $value")
    }
}

enum class EmailConnectionStatus { CONNECTED, ERROR, DISCONNECTED }

data class EmailConnection(
    val provider: EmailProvider,
    val displayName: String,
    val status: EmailConnectionStatus,
    val email: String?,
    val configurationReady: Boolean,
    val connectedAt: String?,
    val expiresAt: String?
)

data class EmailSyncResult(
    val messagesDiscovered: Int,
    val messagesCreated: Int,
    val candidatesCreated: Int,
    val conversionsBackfilled: Int = 0
)

data class EmailCandidate(
    val id: String,
    val provider: EmailProvider,
    val merchant: String?,
    /** Ultimos cuatro digitos de la tarjeta que origino el movimiento, si el correo los trae. */
    val cardLastFour: String? = null,
    val amount: Long,
    val currency: String,
    val direction: String,
    val categorySuggestion: String?,
    val occurredAt: String,
    val confidence: Int,
    val status: String,
    val subject: String?,
    /** Id del candidato que se conserva cuando este quedo marcado como duplicado. */
    val duplicateOfId: String? = null,
    val convertedAmount: Long? = null,
    val convertedCurrency: String? = null,
    val exchangeRateMicros: Long? = null,
    val exchangeRateAt: String? = null,
    val exchangeRateSource: String? = null,
    val conversionKind: String? = null,
    val conversionStatus: String? = null
)

interface EmailConnectionsRepository {
    suspend fun getConnections(): List<EmailConnection>
    suspend fun getCandidates(): List<EmailCandidate>
    suspend fun getAuthorizationUrl(provider: EmailProvider): String
    suspend fun sync(provider: EmailProvider): EmailSyncResult
    suspend fun reviewCandidate(
        id: String,
        action: String,
        category: String?,
        duplicateOfId: String? = null
    ): EmailCandidate
    suspend fun disconnect(provider: EmailProvider)
}

class EmailSessionExpiredException : Exception("La sesión venció.")

/**
 * El sync sigue en la cola del servidor y se agotó la espera. Se distingue del fallo
 * porque no hay nada roto: falta que un worker procese el trabajo. Antes se devolvía
 * el run con sus contadores en cero y la pantalla lo mostraba como un éxito sin
 * correos, que es justo lo contrario de lo que pasaba.
 */
class EmailSyncStillQueuedException : Exception("La sincronización sigue en cola en el servidor.")

/** El sync de correos encolado terminó en estado 'failed'. */
class EmailSyncFailedException(val errorCode: String?) :
    Exception("La sincronización falló${errorCode?.let { " ($it)" } ?: ""}.")

private const val MAX_SYNC_POLLS = 30
private const val SYNC_POLL_DELAY_MS = 1_500L

class DefaultEmailConnectionsRepository(
    private val api: WalletApi,
    private val session: WalletSessionStore
) : EmailConnectionsRepository {
    override suspend fun getConnections(): List<EmailConnection> =
        authenticatedCall { api.emailConnections().data.map(EmailConnectionDto::toDomain) }

    override suspend fun getCandidates(): List<EmailCandidate> =
        authenticatedCall { api.emailCandidates().data.map(EmailCandidateDto::toDomain) }

    override suspend fun getAuthorizationUrl(provider: EmailProvider): String =
        authenticatedCall { api.emailAuthorizationUrl(provider.apiValue).data.authorizationUrl }

    override suspend fun sync(provider: EmailProvider): EmailSyncResult = authenticatedCall {
        // El backend encola el sync (202) y devuelve un run; sondeamos su estado hasta
        // que termine. Con QUEUE_CONNECTION=sync el run ya llega 'completed' sin sondeo.
        var run = api.syncEmailConnection(provider.apiValue).data
        var attempts = 0
        while (run.status != "completed" && run.status != "failed" && attempts < MAX_SYNC_POLLS) {
            delay(SYNC_POLL_DELAY_MS)
            run = api.emailSyncRun(provider.apiValue, run.syncRunId).data
            attempts++
        }
        if (run.status == "failed") {
            throw EmailSyncFailedException(run.errorCode)
        }
        if (run.status != "completed") {
            throw EmailSyncStillQueuedException()
        }
        run.toDomain()
    }

    override suspend fun reviewCandidate(
        id: String,
        action: String,
        category: String?,
        duplicateOfId: String?
    ): EmailCandidate = authenticatedCall {
        api.reviewEmailCandidate(
            id,
            EmailCandidateReviewRequest(action, category, duplicateOfId = duplicateOfId)
        ).data.toDomain()
    }

    override suspend fun disconnect(provider: EmailProvider) {
        authenticatedCall { api.deleteEmailConnection(provider.apiValue) }
    }

    private suspend fun <T> authenticatedCall(request: suspend () -> T): T = try {
        request()
    } catch (exception: HttpException) {
        if (exception.code() == 401) {
            session.clear()
            throw EmailSessionExpiredException()
        }
        throw exception
    }
}

private fun EmailConnectionDto.toDomain() = EmailConnection(
    provider = EmailProvider.fromApi(provider),
    displayName = displayName,
    status = when (status.lowercase()) {
        "connected" -> EmailConnectionStatus.CONNECTED
        "disconnected", "not_connected", "pending" -> EmailConnectionStatus.DISCONNECTED
        else -> EmailConnectionStatus.ERROR
    },
    email = email,
    configurationReady = configurationReady,
    connectedAt = connectedAt,
    expiresAt = expiresAt
)

private fun EmailSyncDto.toDomain() = EmailSyncResult(
    messagesDiscovered = messagesDiscovered,
    messagesCreated = messagesCreated,
    candidatesCreated = candidatesCreated,
    conversionsBackfilled = conversionsBackfilled
)

private fun EmailCandidateDto.toDomain() = EmailCandidate(
    id = id,
    provider = EmailProvider.fromApi(provider),
    merchant = merchant,
    cardLastFour = cardLastFour,
    amount = amount,
    currency = currency,
    direction = direction,
    categorySuggestion = categorySuggestion,
    occurredAt = occurredAt,
    confidence = confidence,
    status = status,
    subject = subject,
    duplicateOfId = duplicateOfId,
    convertedAmount = convertedAmount,
    convertedCurrency = convertedCurrency,
    exchangeRateMicros = exchangeRateMicros,
    exchangeRateAt = exchangeRateAt,
    exchangeRateSource = exchangeRateSource,
    conversionKind = conversionKind,
    conversionStatus = conversionStatus
)
