package com.bsolutions.wallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bsolutions.wallet.data.local.entity.NotificationSourceEntity
import com.bsolutions.wallet.data.local.entity.RawBankNoticeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawBankNoticeDao {
    @Query(
        "SELECT * FROM notification_sources WHERE ownerId = :ownerId " +
            "ORDER BY isEnabled DESC, lastSeenAt DESC, displayName COLLATE NOCASE ASC"
    )
    fun observeSources(ownerId: String): Flow<List<NotificationSourceEntity>>

    @Query(
        "SELECT * FROM raw_bank_notices WHERE ownerId = :ownerId " +
            "ORDER BY postTime DESC LIMIT :limit"
    )
    fun observeNotices(ownerId: String, limit: Int = 100): Flow<List<RawBankNoticeEntity>>

    @Query("SELECT * FROM raw_bank_notices WHERE ownerId = :ownerId ORDER BY postTime DESC")
    suspend fun getAllNotices(ownerId: String): List<RawBankNoticeEntity>

    @Query(
        "SELECT isEnabled FROM notification_sources " +
            "WHERE ownerId = :ownerId AND packageName = :packageName"
    )
    suspend fun isSourceEnabled(ownerId: String, packageName: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: NotificationSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSources(sources: List<NotificationSourceEntity>): List<Long>

    @Query(
        "UPDATE notification_sources SET displayName = :displayName, " +
            "lastSeenAt = :seenAt, observedCount = observedCount + 1 " +
            "WHERE ownerId = :ownerId AND packageName = :packageName"
    )
    suspend fun updateSourceObservation(
        ownerId: String,
        packageName: String,
        displayName: String,
        seenAt: Long
    )

    @Transaction
    suspend fun recordSource(source: NotificationSourceEntity) {
        val inserted = insertSource(source)
        if (inserted == -1L) {
            updateSourceObservation(
                ownerId = source.ownerId,
                packageName = source.packageName,
                displayName = source.displayName,
                seenAt = source.lastSeenAt
            )
        }
    }

    @Query(
        "UPDATE notification_sources SET isEnabled = :enabled " +
            "WHERE ownerId = :ownerId AND packageName = :packageName"
    )
    suspend fun setSourceEnabled(ownerId: String, packageName: String, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotice(notice: RawBankNoticeEntity): Long

    @Query("DELETE FROM raw_bank_notices WHERE ownerId = :ownerId AND expiresAt <= :now")
    suspend fun purgeExpired(ownerId: String, now: Long): Int

    @Query("DELETE FROM raw_bank_notices WHERE ownerId = :ownerId")
    suspend fun clearNotices(ownerId: String)
}
