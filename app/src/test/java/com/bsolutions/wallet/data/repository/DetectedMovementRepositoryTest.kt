package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.deduplication.FinancialEvidenceSource
import com.bsolutions.wallet.core.notifications.ParsedBankNotice
import com.bsolutions.wallet.data.local.dao.DetectedMovementDao
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDetectedMovementDao : DetectedMovementDao {
    private val movements = mutableListOf<DetectedMovementEntity>()
    private val flow = MutableStateFlow<List<DetectedMovementEntity>>(emptyList())
    private val allFlow = MutableStateFlow<List<DetectedMovementEntity>>(emptyList())

    private fun updateFlow() {
        allFlow.value = movements.sortedBy { it.occurredAt }
        flow.value = movements.filter { it.status == "PENDING" && it.duplicateOfId == null }
            .sortedByDescending { it.occurredAt }
    }

    override fun observeAll(ownerId: String): Flow<List<DetectedMovementEntity>> =
        allFlow.map { rows -> rows.filter { it.ownerId == ownerId } }

    override fun getPendingMovements(ownerId: String): Flow<List<DetectedMovementEntity>> = flow

    override suspend fun getMovementById(ownerId: String, id: String) =
        movements.find { it.ownerId == ownerId && it.id == id }

    override suspend fun getByOrigin(ownerId: String, source: String, sourceReference: String) =
        movements.find {
            it.ownerId == ownerId && it.source == source && it.sourceReference == sourceReference
        }

    override suspend fun findCanonicalCandidates(
        ownerId: String,
        fromInclusive: Long,
        toInclusive: Long
    ) = movements.filter {
        it.ownerId == ownerId && it.status == "PENDING" && it.duplicateOfId == null &&
            it.occurredAt in fromInclusive..toInclusive
    }

    override suspend fun getEvidence(ownerId: String, canonicalId: String) = movements.filter {
        it.ownerId == ownerId && (it.id == canonicalId || it.canonicalId == canonicalId)
    }

    override suspend fun insertMovement(movement: DetectedMovementEntity): Long {
        val conflicts = movements.any {
            it.ownerId == movement.ownerId &&
                (it.id == movement.id ||
                    (movement.sourceReference != null && it.source == movement.source &&
                        it.sourceReference == movement.sourceReference))
        }
        if (conflicts) return -1L
        movements += movement
        updateFlow()
        return movements.size.toLong()
    }

    override suspend fun insertAll(movements: List<DetectedMovementEntity>): List<Long> =
        movements.map { insertMovement(it) }

    override suspend fun reassignCanonicalGroup(
        ownerId: String,
        oldCanonicalId: String,
        newCanonicalId: String,
        reason: String
    ) {
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId &&
                (movement.id == oldCanonicalId || movement.canonicalId == oldCanonicalId)
            ) {
                movement.copy(
                    canonicalId = newCanonicalId,
                    duplicateOfId = newCanonicalId,
                    possibleDuplicateOfId = null,
                    dedupeState = "DUPLICATE",
                    dedupeReason = reason
                )
            } else movement
        }
        updateFlow()
    }

    override suspend fun resolveCanonicalAsSeparate(ownerId: String, canonicalId: String): Int {
        var updated = 0
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId && movement.id == canonicalId && movement.duplicateOfId == null) {
                updated++
                movement.copy(
                    possibleDuplicateOfId = null,
                    dedupeState = "CANONICAL",
                    dedupeReason = null
                )
            } else movement
        }
        updateFlow()
        return updated
    }

    override suspend fun resolveCanonicalAsTransactionDuplicate(
        ownerId: String,
        canonicalId: String,
        transactionReference: String,
        reason: String
    ): Int {
        var updated = 0
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId &&
                (movement.id == canonicalId || movement.canonicalId == canonicalId)
            ) {
                updated++
                movement.copy(
                    status = "DISMISSED",
                    needsSync = false,
                    duplicateOfId = transactionReference,
                    possibleDuplicateOfId = null,
                    dedupeState = "DUPLICATE",
                    dedupeReason = reason
                )
            } else movement
        }
        updateFlow()
        return updated
    }

    override suspend fun updateEvidenceReviewState(
        ownerId: String,
        evidenceId: String,
        status: String,
        needsSync: Boolean
    ): Int {
        var updated = 0
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId && movement.id == evidenceId) {
                updated++
                movement.copy(status = status, needsSync = needsSync)
            } else movement
        }
        updateFlow()
        return updated
    }

    override suspend fun updateCanonicalGroupStatus(
        ownerId: String,
        canonicalId: String,
        status: String,
        needsSync: Boolean
    ) {
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId &&
                (movement.id == canonicalId || movement.canonicalId == canonicalId)
            ) movement.copy(status = status, needsSync = needsSync) else movement
        }
        updateFlow()
    }

    override suspend fun updateStatus(
        ownerId: String,
        id: String,
        status: String,
        needsSync: Boolean
    ) {
        movements.replaceAll { movement ->
            if (movement.ownerId == ownerId && movement.id == id) {
                movement.copy(status = status, needsSync = needsSync)
            } else movement
        }
        updateFlow()
    }

    override suspend fun deleteMovement(ownerId: String, id: String) {
        movements.removeAll { it.ownerId == ownerId && it.id == id }
        updateFlow()
    }

    override suspend fun purgeProcessedMovements(ownerId: String) {
        movements.removeAll { it.ownerId == ownerId && it.status != "PENDING" }
        updateFlow()
    }

    override suspend fun getAll(ownerId: String) = movements.filter { it.ownerId == ownerId }
}

class DetectedMovementRepositoryTest {
    private val fakeDao = FakeDetectedMovementDao()
    private val repository = DetectedMovementRepository(fakeDao)

    @Test
    fun `saveMovement no reemplaza una evidencia existente`() = runTest {
        val original = movement(id = "cand_1", merchant = "UBER")

        assertTrue(repository.saveMovement(original))
        assertTrue(!repository.saveMovement(original.copy(merchant = "ALTERADO")))

        assertEquals("UBER", repository.getMovementById("cand_1")?.merchant)
    }

    @Test
    fun `updateStatus changes movement status`() = runTest {
        repository.saveMovement(movement(id = "cand_2", merchant = "SUPERMERCADO BRAVO"))

        repository.updateStatus(id = "cand_2", status = "APPROVED")

        assertEquals(0, repository.getPendingMovements().first().size)
        val updated = repository.getMovementById("cand_2")
        assertNotNull(updated)
        assertEquals("APPROVED", updated?.status)
    }

    @Test
    fun `push fuerte reemplaza correo como canonico y reingesta idempotente`() = runTest {
        val email = emailCandidate(id = "email-1", provider = EmailProvider.GMAIL)
        val first = repository.ingestEmailCandidates(listOf(email), "guest").getValue(email.id)

        val push = repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-1",
            appLabel = "Banco Popular",
            title = "Compra aprobada",
            occurredAt = 1_700_000_100_000,
            parsed = parsedNotice()
        )
        val repeated = repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-1",
            appLabel = "Banco Popular",
            title = "Compra aprobada",
            occurredAt = 1_700_000_100_000,
            parsed = parsedNotice()
        )

        assertEquals(DedupeDisposition.CANONICAL, first.disposition)
        assertEquals(DedupeDisposition.CANONICAL, push.disposition)
        assertEquals(DedupeDisposition.ALREADY_INGESTED, repeated.disposition)
        val rows = repository.allForTesting()
        assertEquals(2, rows.size)
        assertEquals("notification:notice-1", rows.single { it.sourceReference == "email-1" }.duplicateOfId)
        assertEquals(1, repository.getPendingMovements().first().size)
    }

    @Test
    fun `monto sin comercio ni tarjeta queda como posible duplicado visible`() = runTest {
        repository.ingestEmailCandidates(
            listOf(emailCandidate(id = "email-1", merchant = null, last4 = null)),
            "guest"
        )

        val result = repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-2",
            appLabel = "Banco",
            title = "Aviso",
            occurredAt = 1_700_000_100_000,
            parsed = parsedNotice(merchant = null, last4 = null)
        )

        assertEquals(DedupeDisposition.POSSIBLE_DUPLICATE, result.disposition)
        assertEquals(2, repository.getPendingMovements().first().size)
    }

    @Test
    fun `usuario puede conservar por separado un posible duplicado`() = runTest {
        repository.ingestEmailCandidates(
            listOf(emailCandidate(id = "email-keep", merchant = null, last4 = null)),
            "guest"
        )
        repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-keep",
            appLabel = "Banco",
            title = "Aviso",
            occurredAt = 1_700_000_100_000,
            parsed = parsedNotice(merchant = null, last4 = null)
        )
        val possible = repository.getPendingMovements().first().single {
            it.possibleDuplicateOfId != null
        }

        val resolution = repository.resolvePossibleDuplicate(possible.id, keepSeparate = true)

        assertEquals(PossibleDuplicateResolution.KEPT_SEPARATE, resolution)
        assertEquals(null, repository.getMovementById(possible.id)?.possibleDuplicateOfId)
        assertEquals("CANONICAL", repository.getMovementById(possible.id)?.dedupeState)
    }

    @Test
    fun `usuario puede unir dos detecciones ambiguas sin borrar evidencia`() = runTest {
        repository.ingestEmailCandidates(
            listOf(emailCandidate(id = "email-merge", merchant = null, last4 = null)),
            "guest"
        )
        repository.ingestNotification(
            ownerId = "guest",
            noticeId = "notice-merge",
            appLabel = "Banco",
            title = "Aviso",
            occurredAt = 1_700_000_100_000,
            parsed = parsedNotice(merchant = null, last4 = null)
        )
        val possible = repository.getPendingMovements().first().single {
            it.possibleDuplicateOfId != null
        }

        val resolution = repository.resolvePossibleDuplicate(possible.id, keepSeparate = false)

        assertEquals(PossibleDuplicateResolution.MERGED_WITH_DETECTED_MOVEMENT, resolution)
        assertEquals(1, repository.getPendingMovements().first().size)
        assertEquals(2, repository.allForTesting().size)
    }

    @Test
    fun `mismo evento de propietarios distintos nunca se cruza`() = runTest {
        repository.ingestEmailCandidates(listOf(emailCandidate(id = "same")), "user:1")

        val other = repository.ingestNotification(
            ownerId = "user:2",
            noticeId = "notice",
            appLabel = "Banco",
            title = "Compra",
            occurredAt = 1_700_000_100_000,
            parsed = parsedNotice()
        )

        assertEquals(DedupeDisposition.CANONICAL, other.disposition)
        assertEquals(1, repository.allForTesting("user:1").size)
        assertEquals(1, repository.allForTesting("user:2").size)
    }

    private fun movement(id: String, merchant: String) = DetectedMovementEntity(
        id = id,
        source = FinancialEvidenceSource.EMAIL_GMAIL.name,
        merchant = merchant,
        amountMinor = 35_000L,
        currency = "DOP",
        status = "PENDING",
        sourceReference = id,
        canonicalId = id
    )

    private fun emailCandidate(
        id: String,
        provider: EmailProvider = EmailProvider.GMAIL,
        merchant: String? = "Amazon",
        last4: String? = "1234"
    ) = EmailCandidate(
        id = id,
        provider = provider,
        merchant = merchant,
        cardLastFour = last4,
        amount = -10_000,
        currency = "DOP",
        direction = "expense",
        eventType = "CARD_PURCHASE_APPROVED",
        categorySuggestion = "Compras",
        occurredAt = "2023-11-14T22:13:20Z",
        confidence = 90,
        status = "pending",
        subject = "Compra aprobada"
    )

    private fun parsedNotice(merchant: String? = "Amazon", last4: String? = "1234") =
        ParsedBankNotice(
            merchant = merchant,
            amountMinor = 10_000,
            currency = "DOP",
            last4Digits = last4,
            suggestedCategoryId = "cat_compras"
        )
}
