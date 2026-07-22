package com.bsolutions.wallet.core.email

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bsolutions.wallet.data.repository.EmailSessionExpiredException
import com.bsolutions.wallet.data.repository.WalletSessionStore
import com.bsolutions.wallet.domain.email.SyncConnectedEmailAccounts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val session: WalletSessionStore,
    private val syncConnectedEmailAccounts: SyncConnectedEmailAccounts
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (session.token.isNullOrBlank()) return Result.success()

        return try {
            val sweep = syncConnectedEmailAccounts()
            if (sweep.failed == 0) Result.success() else Result.retry()
        } catch (_: EmailSessionExpiredException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "connected_email_sync"
        const val REPEAT_INTERVAL_MINUTES = 15L
    }
}
