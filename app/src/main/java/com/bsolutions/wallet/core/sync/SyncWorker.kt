package com.bsolutions.wallet.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bsolutions.wallet.data.repository.SyncOutcome
import com.bsolutions.wallet.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sube operaciones pendientes y baja cambios del backend en segundo plano.
 * Sin sesión, no hace nada (la app sigue local-only). Reintenta ante errores de red.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (syncRepository.sync()) {
        is SyncOutcome.Success -> Result.success()
        is SyncOutcome.NoSession -> Result.success()
        is SyncOutcome.Error -> Result.retry()
    }

    companion object {
        const val WORK_NAME = "wallet_backend_sync"
        const val REPEAT_INTERVAL_MINUTES = 30L
    }
}
