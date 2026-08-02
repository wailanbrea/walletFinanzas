package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.data.local.dao.DetectedMovementDao
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FakeDetectedMovementDao : DetectedMovementDao {
    private val movements = mutableListOf<DetectedMovementEntity>()
    private val flow = MutableStateFlow<List<DetectedMovementEntity>>(emptyList())

    private fun updateFlow() {
        flow.value = movements.filter { it.status == "PENDING" }
    }

    override fun getPendingMovements(ownerId: String): Flow<List<DetectedMovementEntity>> = flow

    override suspend fun getMovementById(ownerId: String, id: String): DetectedMovementEntity? {
        return movements.find { it.ownerId == ownerId && it.id == id }
    }

    override suspend fun insertMovement(movement: DetectedMovementEntity) {
        movements.removeAll { it.id == movement.id && it.ownerId == movement.ownerId }
        movements.add(movement)
        updateFlow()
    }

    override suspend fun insertAll(movements: List<DetectedMovementEntity>) {
        movements.forEach { insertMovement(it) }
    }

    override suspend fun updateStatus(ownerId: String, id: String, status: String, needsSync: Boolean) {
        val existing = movements.find { it.ownerId == ownerId && it.id == id }
        if (existing != null) {
            movements.removeAll { it.id == id && it.ownerId == ownerId }
            movements.add(existing.copy(status = status, needsSync = needsSync))
            updateFlow()
        }
    }

    override suspend fun deleteMovement(ownerId: String, id: String) {
        movements.removeAll { it.ownerId == ownerId && it.id == id }
        updateFlow()
    }

    override suspend fun purgeProcessedMovements(ownerId: String) {
        movements.removeAll { it.ownerId == ownerId && it.status != "PENDING" }
        updateFlow()
    }
}

class DetectedMovementRepositoryTest {

    private val fakeDao = FakeDetectedMovementDao()
    private val repository = DetectedMovementRepository(fakeDao)

    @Test
    fun `saveMovement and getPendingMovements returns pending movement`() = runTest {
        val movement = DetectedMovementEntity(
            id = "cand_1",
            source = "EMAIL",
            merchant = "UBER",
            amountMinor = 35000L,
            currency = "DOP",
            status = "PENDING"
        )

        repository.saveMovement(movement)

        val pending = repository.getPendingMovements().first()

        assertEquals(1, pending.size)
        assertEquals("UBER", pending.first().merchant)
    }

    @Test
    fun `updateStatus changes movement status`() = runTest {
        val movement = DetectedMovementEntity(
            id = "cand_2",
            source = "NOTIFICATION",
            merchant = "SUPERMERCADO BRAVO",
            amountMinor = 150000L,
            currency = "DOP",
            status = "PENDING"
        )

        repository.saveMovement(movement)
        repository.updateStatus(id = "cand_2", status = "APPROVED")

        val pending = repository.getPendingMovements().first()
        val updated = repository.getMovementById("cand_2")

        assertEquals(0, pending.size)
        assertNotNull(updated)
        assertEquals("APPROVED", updated?.status)
    }
}
