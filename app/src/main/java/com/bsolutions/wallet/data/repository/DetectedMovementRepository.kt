package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.data.local.dao.DetectedMovementDao
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectedMovementRepository @Inject constructor(
    private val dao: DetectedMovementDao
) {
    fun getPendingMovements(ownerId: String = WALLET_GUEST_OWNER_ID): Flow<List<DetectedMovementEntity>> {
        return dao.getPendingMovements(ownerId)
    }

    suspend fun getMovementById(id: String, ownerId: String = WALLET_GUEST_OWNER_ID): DetectedMovementEntity? {
        return dao.getMovementById(ownerId, id)
    }

    suspend fun saveMovement(movement: DetectedMovementEntity) {
        dao.insertMovement(movement)
    }

    suspend fun saveAll(movements: List<DetectedMovementEntity>) {
        dao.insertAll(movements)
    }

    suspend fun updateStatus(id: String, status: String, ownerId: String = WALLET_GUEST_OWNER_ID) {
        dao.updateStatus(ownerId = ownerId, id = id, status = status, needsSync = true)
    }

    suspend fun deleteMovement(id: String, ownerId: String = WALLET_GUEST_OWNER_ID) {
        dao.deleteMovement(ownerId = ownerId, id = id)
    }

    suspend fun purgeProcessedMovements(ownerId: String = WALLET_GUEST_OWNER_ID) {
        dao.purgeProcessedMovements(ownerId = ownerId)
    }
}
