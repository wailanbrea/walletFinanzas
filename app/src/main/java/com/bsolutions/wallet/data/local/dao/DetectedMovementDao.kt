package com.bsolutions.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedMovementDao {
    @Query("SELECT * FROM detected_movements WHERE ownerId = :ownerId ORDER BY occurredAt ASC")
    fun observeAll(ownerId: String): Flow<List<DetectedMovementEntity>>

    @Query(
        "SELECT * FROM detected_movements WHERE ownerId = :ownerId " +
            "AND status = 'PENDING' AND duplicateOfId IS NULL ORDER BY occurredAt DESC"
    )
    fun getPendingMovements(ownerId: String): Flow<List<DetectedMovementEntity>>

    @Query("SELECT * FROM detected_movements WHERE ownerId = :ownerId AND id = :id")
    suspend fun getMovementById(ownerId: String, id: String): DetectedMovementEntity?

    @Query(
        "SELECT * FROM detected_movements WHERE ownerId = :ownerId AND source = :source " +
            "AND sourceReference = :sourceReference LIMIT 1"
    )
    suspend fun getByOrigin(
        ownerId: String,
        source: String,
        sourceReference: String
    ): DetectedMovementEntity?

    @Query(
        "SELECT * FROM detected_movements WHERE ownerId = :ownerId " +
            "AND status = 'PENDING' AND duplicateOfId IS NULL " +
            "AND occurredAt BETWEEN :fromInclusive AND :toInclusive ORDER BY occurredAt ASC"
    )
    suspend fun findCanonicalCandidates(
        ownerId: String,
        fromInclusive: Long,
        toInclusive: Long
    ): List<DetectedMovementEntity>

    @Query(
        "SELECT * FROM detected_movements WHERE ownerId = :ownerId " +
            "AND (id = :canonicalId OR canonicalId = :canonicalId) ORDER BY occurredAt ASC"
    )
    suspend fun getEvidence(ownerId: String, canonicalId: String): List<DetectedMovementEntity>
    @Query(
        "SELECT * FROM detected_movements WHERE ownerId = :ownerId AND status = 'PENDING' " +
            "AND duplicateOfId IS NULL AND possibleDuplicateOfId = :canonicalId ORDER BY occurredAt ASC"
    )
    suspend fun findPendingPossibleDuplicatesPointingTo(
        ownerId: String,
        canonicalId: String
    ): List<DetectedMovementEntity>

    /** Una evidencia recibida previamente nunca sobrescribe una decisiÃ³n del usuario. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovement(movement: DetectedMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(movements: List<DetectedMovementEntity>): List<Long>

    @Query(
        "UPDATE detected_movements SET canonicalId = :newCanonicalId, " +
            "duplicateOfId = :newCanonicalId, possibleDuplicateOfId = NULL, " +
            "dedupeState = 'DUPLICATE', dedupeReason = :reason " +
            "WHERE ownerId = :ownerId AND (id = :oldCanonicalId OR canonicalId = :oldCanonicalId)"
    )
    suspend fun reassignCanonicalGroup(
        ownerId: String,
        oldCanonicalId: String,
        newCanonicalId: String,
        reason: String
    )

    @Query(
        "UPDATE detected_movements SET possibleDuplicateOfId = NULL, " +
            "dedupeState = 'CANONICAL', dedupeReason = NULL " +
            "WHERE ownerId = :ownerId AND id = :canonicalId AND duplicateOfId IS NULL"
    )
    suspend fun resolveCanonicalAsSeparate(ownerId: String, canonicalId: String): Int

    @Query(
        "UPDATE detected_movements SET status = 'DISMISSED', needsSync = 0, " +
            "duplicateOfId = :transactionReference, possibleDuplicateOfId = NULL, " +
            "dedupeState = 'DUPLICATE', dedupeReason = :reason " +
            "WHERE ownerId = :ownerId AND (id = :canonicalId OR canonicalId = :canonicalId)"
    )
    suspend fun resolveCanonicalAsTransactionDuplicate(
        ownerId: String,
        canonicalId: String,
        transactionReference: String,
        reason: String
    ): Int

    @Query(
        "UPDATE detected_movements SET status = :status, needsSync = :needsSync " +
            "WHERE ownerId = :ownerId AND id = :evidenceId"
    )
    suspend fun updateEvidenceReviewState(
        ownerId: String,
        evidenceId: String,
        status: String,
        needsSync: Boolean
    ): Int

    @Query(
        "UPDATE detected_movements SET status = :status, needsSync = :needsSync " +
            "WHERE ownerId = :ownerId AND (id = :canonicalId OR canonicalId = :canonicalId)"
    )
    suspend fun updateCanonicalGroupStatus(
        ownerId: String,
        canonicalId: String,
        status: String,
        needsSync: Boolean = true
    )

    @Query(
        "UPDATE detected_movements SET status = :status, needsSync = :needsSync " +
            "WHERE ownerId = :ownerId AND id = :id"
    )
    suspend fun updateStatus(
        ownerId: String,
        id: String,
        status: String,
        needsSync: Boolean = true
    )

    @Query("DELETE FROM detected_movements WHERE ownerId = :ownerId AND id = :id")
    suspend fun deleteMovement(ownerId: String, id: String)

    @Query("DELETE FROM detected_movements WHERE ownerId = :ownerId AND status != 'PENDING'")
    suspend fun purgeProcessedMovements(ownerId: String)

    @Query("SELECT * FROM detected_movements WHERE ownerId = :ownerId ORDER BY occurredAt ASC")
    suspend fun getAll(ownerId: String): List<DetectedMovementEntity>
}
