package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.deduplication.FinancialEventEvidence
import com.bsolutions.wallet.core.deduplication.FinancialEventMatcher
import com.bsolutions.wallet.core.deduplication.FinancialEvidenceSource
import com.bsolutions.wallet.core.deduplication.FinancialMatchResult
import com.bsolutions.wallet.core.notifications.ParsedBankNotice
import com.bsolutions.wallet.data.local.dao.DetectedMovementDao
import com.bsolutions.wallet.data.local.dao.TransactionDao
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.local.entity.TransactionEntity
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class DedupeDisposition {
    CANONICAL,
    DUPLICATE,
    POSSIBLE_DUPLICATE,
    ALREADY_INGESTED
}

enum class PossibleDuplicateResolution {
    KEPT_SEPARATE,
    MERGED_WITH_DETECTED_MOVEMENT,
    MATCHED_EXISTING_TRANSACTION
}

data class MovementIngestionResult(
    val movementId: String,
    val canonicalId: String,
    val disposition: DedupeDisposition,
    val reason: String? = null
)

@Singleton
class DetectedMovementRepository @Inject constructor(
    private val dao: DetectedMovementDao,
    private val transactionDao: TransactionDao
) {
    internal constructor(dao: DetectedMovementDao) : this(dao, NoOpTransactionDao)

    private val ingestionMutex = Mutex()

    fun observeAll(ownerId: String = WALLET_GUEST_OWNER_ID): Flow<List<DetectedMovementEntity>> =
        dao.observeAll(ownerId)

    suspend fun getAllMovements(ownerId: String = WALLET_GUEST_OWNER_ID): List<DetectedMovementEntity> =
        dao.getAll(ownerId)

    fun getPendingMovements(ownerId: String = WALLET_GUEST_OWNER_ID): Flow<List<DetectedMovementEntity>> =
        dao.getPendingMovements(ownerId)

    suspend fun deduplicateExistingPendingMovements(ownerId: String = WALLET_GUEST_OWNER_ID) = ingestionMutex.withLock {
        val pending = dao.getAll(ownerId).filter { it.status == "PENDING" && it.duplicateOfId == null }
        for (i in pending.indices) {
            val current = pending[i]
            val currentCanonicalId = current.canonicalId ?: current.id

            val bookedTxId = transactionIdForCanonical(currentCanonicalId)
            if (transactionDao.getTransactionById(ownerId, bookedTxId) != null) {
                dao.updateCanonicalGroupStatus(ownerId, currentCanonicalId, "APPROVED", needsSync = false)
                continue
            }

            val manualMatch = findManualPossibleDuplicate(current)
            if (manualMatch != null) {
                dao.resolveCanonicalAsTransactionDuplicate(
                    ownerId = ownerId,
                    canonicalId = currentCanonicalId,
                    transactionReference = "transaction:${manualMatch.id}",
                    reason = "Movimiento ya registrado previamente."
                )
                continue
            }

            for (j in i + 1 until pending.size) {
                val candidate = pending[j]
                val candidateCanonicalId = candidate.canonicalId ?: candidate.id
                if (currentCanonicalId == candidateCanonicalId) continue

                val match = FinancialEventMatcher.match(current.toEvidence(), listOf(candidate.toEvidence()))
                if (match is FinancialMatchResult.StrongMatch) {
                    dao.reassignCanonicalGroup(
                        ownerId = ownerId,
                        oldCanonicalId = candidateCanonicalId,
                        newCanonicalId = currentCanonicalId,
                        reason = "Depuración de duplicados detectada automáticamente."
                    )
                }
            }
        }
    }

    suspend fun getMovementById(
        id: String,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ): DetectedMovementEntity? = dao.getMovementById(ownerId, id)

    suspend fun getEvidence(
        canonicalId: String,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ): List<DetectedMovementEntity> = dao.getEvidence(ownerId, canonicalId)

    suspend fun saveMovement(movement: DetectedMovementEntity): Boolean =
        dao.insertMovement(movement) != -1L

    suspend fun saveAll(movements: List<DetectedMovementEntity>): Int =
        dao.insertAll(movements).count { it != -1L }

    suspend fun updateStatus(
        id: String,
        status: String,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ) {
        dao.updateStatus(ownerId = ownerId, id = id, status = status, needsSync = true)
    }

    suspend fun deleteMovement(id: String, ownerId: String = WALLET_GUEST_OWNER_ID) {
        dao.deleteMovement(ownerId = ownerId, id = id)
    }

    suspend fun purgeProcessedMovements(ownerId: String = WALLET_GUEST_OWNER_ID) {
        dao.purgeProcessedMovements(ownerId = ownerId)
    }

    suspend fun resolvePossibleDuplicate(
        movementId: String,
        keepSeparate: Boolean,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ): PossibleDuplicateResolution = ingestionMutex.withLock {
        val movement = requireNotNull(dao.getMovementById(ownerId, movementId)) {
            "El movimiento detectado ya no existe."
        }
        require(movement.duplicateOfId == null && movement.status == "PENDING") {
            "Solo se puede resolver una raíz pendiente."
        }
        val possibleTarget = requireNotNull(movement.possibleDuplicateOfId) {
            "El movimiento no está marcado como posible duplicado."
        }
        val canonicalId = movement.canonicalId ?: movement.id

        if (keepSeparate) {
            check(dao.resolveCanonicalAsSeparate(ownerId, canonicalId) == 1) {
                "No se pudo conservar el movimiento como independiente."
            }
            return@withLock PossibleDuplicateResolution.KEPT_SEPARATE
        }

        if (possibleTarget.startsWith(TRANSACTION_REFERENCE_PREFIX)) {
            val transactionId = possibleTarget.removePrefix(TRANSACTION_REFERENCE_PREFIX)
            require(transactionDao.getTransactionById(ownerId, transactionId) != null) {
                "El movimiento manual ya no existe. Actualiza la bandeja."
            }
            check(
                dao.resolveCanonicalAsTransactionDuplicate(
                    ownerId = ownerId,
                    canonicalId = canonicalId,
                    transactionReference = possibleTarget,
                    reason = "El usuario confirmó que ya estaba registrado manualmente."
                ) > 0
            ) { "No se pudo vincular el movimiento con la transacción existente." }
            return@withLock PossibleDuplicateResolution.MATCHED_EXISTING_TRANSACTION
        }

        val target = requireNotNull(dao.getMovementById(ownerId, possibleTarget)) {
            "La otra evidencia ya no está disponible."
        }
        require(target.status == "PENDING" && target.duplicateOfId == null) {
            "La otra detección ya fue procesada. Actualiza la bandeja."
        }
        val targetCanonicalId = target.canonicalId ?: target.id
        require(targetCanonicalId != canonicalId) { "Un movimiento no puede duplicarse a sí mismo." }
        dao.reassignCanonicalGroup(
            ownerId = ownerId,
            oldCanonicalId = canonicalId,
            newCanonicalId = targetCanonicalId,
            reason = "El usuario confirmó que ambas detecciones representan el mismo movimiento."
        )
        PossibleDuplicateResolution.MERGED_WITH_DETECTED_MOVEMENT
    }

    suspend fun dismissCanonicalGroup(
        canonicalId: String,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ) {
        dao.updateCanonicalGroupStatus(ownerId, canonicalId, "DISMISSED", needsSync = false)
    }

    suspend fun completeBookingReview(
        canonicalId: String,
        failedEmailEvidenceIds: Set<String>,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ) = ingestionMutex.withLock {
        dao.updateCanonicalGroupStatus(ownerId, canonicalId, "APPROVED", needsSync = false)
        failedEmailEvidenceIds.forEach { evidenceId ->
            check(
                dao.updateEvidenceReviewState(
                    ownerId = ownerId,
                    evidenceId = evidenceId,
                    status = "APPROVED",
                    needsSync = true
                ) == 1
            ) { "No se pudo conservar la confirmación pendiente del correo." }
        }
    }

    suspend fun transactionIdForEmailCandidate(
        provider: EmailProvider,
        candidateId: String,
        ownerId: String = WALLET_GUEST_OWNER_ID
    ): String? {
        val source = when (provider) {
            EmailProvider.GMAIL -> FinancialEvidenceSource.EMAIL_GMAIL
            EmailProvider.MICROSOFT -> FinancialEvidenceSource.EMAIL_MICROSOFT
        }
        val evidence = dao.getByOrigin(ownerId, source.name, candidateId) ?: return null
        return transactionIdForCanonical(evidence.canonicalId ?: evidence.id)
    }

    suspend fun ingestEmailCandidates(
        candidates: List<EmailCandidate>,
        ownerId: String
    ): Map<String, MovementIngestionResult> = buildMap {
        candidates.forEach { candidate ->
            val occurredAt = runCatching { Instant.parse(candidate.occurredAt).toEpochMilli() }
                .getOrNull() ?: return@forEach
            val source = when (candidate.provider) {
                EmailProvider.GMAIL -> FinancialEvidenceSource.EMAIL_GMAIL
                EmailProvider.MICROSOFT -> FinancialEvidenceSource.EMAIL_MICROSOFT
            }
            val movement = DetectedMovementEntity(
                id = "email:${candidate.provider.apiValue}:${candidate.id}",
                source = source.name,
                senderOrApp = candidate.senderDomain ?: candidate.provider.apiValue,
                title = candidate.subject.orEmpty(),
                rawBody = "",
                merchant = candidate.merchant,
                amountMinor = candidate.amount.safeAbsoluteValue(),
                currency = candidate.currency.uppercase(Locale.ROOT),
                last4Digits = candidate.cardLastFour,
                detectedAt = System.currentTimeMillis(),
                occurredAt = occurredAt,
                direction = candidate.direction.lowercase(Locale.ROOT),
                eventType = candidate.eventType,
                baseAmountMinor = candidate.baseAmountMinor(),
                baseCurrency = candidate.baseCurrency(),
                status = "PENDING",
                suggestedCategoryId = null,
                confidence = candidate.confidence,
                needsSync = false,
                ownerId = ownerId,
                sourceReference = candidate.id,
                canonicalId = "email:${candidate.provider.apiValue}:${candidate.id}"
            )
            put(candidate.id, ingest(movement))
        }
    }

    suspend fun ingestNotification(
        ownerId: String,
        noticeId: String,
        appLabel: String,
        title: String,
        occurredAt: Long,
        parsed: ParsedBankNotice
    ): MovementIngestionResult {
        val movementId = "notification:$noticeId"
        return ingest(
            DetectedMovementEntity(
                id = movementId,
                source = FinancialEvidenceSource.BANK_NOTIFICATION.name,
                senderOrApp = appLabel,
                title = title,
                rawBody = "",
                merchant = parsed.merchant,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency,
                last4Digits = parsed.last4Digits,
                detectedAt = System.currentTimeMillis(),
                occurredAt = occurredAt,
                direction = parsed.direction,
                eventType = parsed.eventType,
                baseAmountMinor = parsed.amountMinor.takeIf {
                    parsed.currency.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true)
                },
                baseCurrency = parsed.currency.takeIf {
                    it.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true)
                },
                status = "PENDING",
                suggestedCategoryId = parsed.suggestedCategoryId,
                confidence = if (parsed.last4Digits != null) 90 else 75,
                needsSync = false,
                ownerId = ownerId,
                sourceReference = noticeId,
                canonicalId = movementId
            )
        )
    }

    suspend fun markEmailCandidateApproved(
        candidate: EmailCandidate,
        ownerId: String
    ) {
        val source = when (candidate.provider) {
            EmailProvider.GMAIL -> FinancialEvidenceSource.EMAIL_GMAIL
            EmailProvider.MICROSOFT -> FinancialEvidenceSource.EMAIL_MICROSOFT
        }
        val evidence = dao.getByOrigin(ownerId, source.name, candidate.id) ?: return
        val canonicalId = evidence.canonicalId ?: evidence.id
        dao.updateCanonicalGroupStatus(
            ownerId = ownerId,
            canonicalId = canonicalId,
            status = "APPROVED",
            needsSync = false
        )
    }

    internal suspend fun allForTesting(ownerId: String = WALLET_GUEST_OWNER_ID) = dao.getAll(ownerId)

    private suspend fun ingest(incoming: DetectedMovementEntity): MovementIngestionResult =
        ingestionMutex.withLock {
            val sourceReference = requireNotNull(incoming.sourceReference)
            dao.getByOrigin(incoming.ownerId, incoming.source, sourceReference)?.let { existing ->
                return@withLock existing.toResult(DedupeDisposition.ALREADY_INGESTED)
            }

            val incomingEvidence = incoming.toEvidence()
            val candidates = dao.findCanonicalCandidates(
                ownerId = incoming.ownerId,
                fromInclusive = incoming.occurredAt - FinancialEventMatcher.EMAIL_WINDOW_MILLIS,
                toInclusive = incoming.occurredAt + FinancialEventMatcher.EMAIL_WINDOW_MILLIS
            )
            val match = FinancialEventMatcher.match(incomingEvidence, candidates.map { it.toEvidence() })

            when (match) {
                FinancialMatchResult.NoMatch -> {
                    val manual = findManualPossibleDuplicate(incoming)
                    if (manual != null) {
                        val duplicate = incoming.copy(
                            canonicalId = incoming.id,
                            duplicateOfId = "transaction:${manual.id}",
                            possibleDuplicateOfId = null,
                            status = "DISMISSED",
                            dedupeState = "DUPLICATE",
                            dedupeReason = manualMatchReason()
                        )
                        insertResolved(duplicate, DedupeDisposition.DUPLICATE)
                    } else {
                        insertCanonical(incoming)
                    }
                }

                is FinancialMatchResult.PossibleDuplicate ->
                    insertPossible(incoming, match.candidate.id, match.reason)

                is FinancialMatchResult.StrongMatch -> {
                    val existing = candidates.first { it.id == match.canonical.id }
                    val preferred = FinancialEventMatcher.preferredCanonical(
                        incomingEvidence,
                        existing.toEvidence()
                    )
                    if (preferred.id == incoming.id) {
                        val inserted = incoming.copy(
                            canonicalId = incoming.id,
                            dedupeState = "CANONICAL",
                            dedupeReason = match.reason
                        )
                        if (dao.insertMovement(inserted) == -1L) {
                            checkNotNull(dao.getByOrigin(incoming.ownerId, incoming.source, sourceReference))
                                .toResult(DedupeDisposition.ALREADY_INGESTED)
                        } else {
                            dao.reassignCanonicalGroup(
                                ownerId = incoming.ownerId,
                                oldCanonicalId = existing.canonicalId ?: existing.id,
                                newCanonicalId = incoming.id,
                                reason = match.reason
                            )
                            inserted.toResult(DedupeDisposition.CANONICAL)
                        }
                    } else {
                        val canonicalId = existing.canonicalId ?: existing.id
                        val duplicate = incoming.copy(
                            canonicalId = canonicalId,
                            duplicateOfId = canonicalId,
                            possibleDuplicateOfId = null,
                            dedupeState = "DUPLICATE",
                            dedupeReason = match.reason
                        )
                        insertResolved(duplicate, DedupeDisposition.DUPLICATE)
                    }
                }
            }
        }

    private suspend fun insertCanonical(incoming: DetectedMovementEntity): MovementIngestionResult {
        val canonical = incoming.copy(
            canonicalId = incoming.id,
            duplicateOfId = null,
            possibleDuplicateOfId = null,
            dedupeState = "CANONICAL",
            dedupeReason = null
        )
        return insertResolved(canonical, DedupeDisposition.CANONICAL)
    }

    private suspend fun insertPossible(
        incoming: DetectedMovementEntity,
        possibleDuplicateOfId: String,
        reason: String
    ): MovementIngestionResult {
        val possible = incoming.copy(
            canonicalId = incoming.id,
            duplicateOfId = null,
            possibleDuplicateOfId = possibleDuplicateOfId,
            dedupeState = "POSSIBLE_DUPLICATE",
            dedupeReason = reason
        )
        return insertResolved(possible, DedupeDisposition.POSSIBLE_DUPLICATE)
    }

    private suspend fun insertResolved(
        movement: DetectedMovementEntity,
        disposition: DedupeDisposition
    ): MovementIngestionResult {
        val inserted = dao.insertMovement(movement)
        if (inserted != -1L) return movement.toResult(disposition)
        val existing = checkNotNull(
            dao.getByOrigin(movement.ownerId, movement.source, requireNotNull(movement.sourceReference))
        )
        return existing.toResult(DedupeDisposition.ALREADY_INGESTED)
    }

    private suspend fun findManualPossibleDuplicate(
        incoming: DetectedMovementEntity
    ): TransactionEntity? {
        val comparableAmount = incoming.baseAmountMinor ?: incoming.amountMinor ?: return null
        val rawCurrency = incoming.baseCurrency ?: incoming.currency ?: "DOP"
        val comparableCurrency = FinancialEventMatcher.normalizeCurrency(rawCurrency)
        val transactionType = when (incoming.direction.lowercase(Locale.ROOT)) {
            "expense", "debit", "compra", "egreso", "" -> "EXPENSE"
            "income", "credit", "deposito", "ingreso" -> "INCOME"
            "transfer" -> "TRANSFER"
            else -> "EXPENSE"
        }
        val manual = transactionDao.findRecentPotentialDuplicates(
            ownerId = incoming.ownerId,
            type = transactionType,
            currency = comparableCurrency,
            fromInclusive = incoming.occurredAt - FinancialEventMatcher.EMAIL_WINDOW_MILLIS,
            toInclusive = incoming.occurredAt + FinancialEventMatcher.EMAIL_WINDOW_MILLIS
        )
        return manual.filter { transaction ->
            FinancialEventMatcher.amountsMatch(
                incoming.toEvidence(),
                transaction.toEvidence()
            )
        }.minByOrNull { transaction -> abs(transaction.date - incoming.occurredAt) }
    }

    private fun DetectedMovementEntity.toEvidence() = FinancialEventEvidence(
        id = id,
        source = source.toEvidenceSource(),
        occurredAt = occurredAt,
        direction = direction,
        amountMinor = amountMinor,
        currency = currency,
        baseAmountMinor = baseAmountMinor,
        baseCurrency = baseCurrency,
        merchant = merchant,
        last4Digits = last4Digits,
        eventType = eventType
    )

    private fun TransactionEntity.toEvidence() = FinancialEventEvidence(
        id = "transaction:$id",
        source = FinancialEvidenceSource.MANUAL_TRANSACTION,
        occurredAt = date,
        direction = type.lowercase(Locale.ROOT),
        amountMinor = amount,
        currency = currency,
        baseAmountMinor = amount.takeIf {
            currency.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true)
        },
        baseCurrency = currency.takeIf {
            it.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true)
        },
        merchant = note.takeIf(String::isNotBlank)
    )

    private fun String.toEvidenceSource(): FinancialEvidenceSource = when (this) {
        "EMAIL" -> FinancialEvidenceSource.EMAIL_GMAIL
        "NOTIFICATION" -> FinancialEvidenceSource.BANK_NOTIFICATION
        else -> FinancialEvidenceSource.valueOf(this)
    }

    private fun DetectedMovementEntity.toResult(disposition: DedupeDisposition) =
        MovementIngestionResult(
            movementId = id,
            canonicalId = canonicalId ?: id,
            disposition = disposition,
            reason = dedupeReason
        )

    private fun EmailCandidate.baseAmountMinor(): Long? = when {
        currency.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true) ->
            amount.safeAbsoluteValue()
        convertedCurrency.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true) ->
            convertedAmount?.safeAbsoluteValue()
        else -> null
    }

    private fun EmailCandidate.baseCurrency(): String? = when {
        currency.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true) ->
            FinancialEventMatcher.BASE_CURRENCY
        convertedCurrency.equals(FinancialEventMatcher.BASE_CURRENCY, ignoreCase = true) &&
            convertedAmount != null -> FinancialEventMatcher.BASE_CURRENCY
        else -> null
    }

    private fun Long.safeAbsoluteValue(): Long = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)

    private fun manualMatchReason() =
        "Coincide con un movimiento manual de las últimas 24 horas; requiere revisión."

    private object NoOpTransactionDao : TransactionDao by unsupportedTransactionDao()

    companion object {
        private const val TRANSACTION_REFERENCE_PREFIX = "transaction:"

        fun transactionIdForCanonical(canonicalId: String): String = UUID.nameUUIDFromBytes(
            "detected-movement:$canonicalId".toByteArray(Charsets.UTF_8)
        ).toString()
    }
}

/** Solo para pruebas unitarias legacy que construyen el repositorio con un DAO. */
private fun unsupportedTransactionDao(): TransactionDao = java.lang.reflect.Proxy.newProxyInstance(
    TransactionDao::class.java.classLoader,
    arrayOf(TransactionDao::class.java)
) { _, method, _ ->
    when (method.name) {
        "findRecentPotentialDuplicates" -> emptyList<TransactionEntity>()
        else -> error("TransactionDao.${method.name} no está disponible en esta prueba.")
    }
} as TransactionDao
