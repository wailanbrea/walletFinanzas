package com.bsolutions.wallet.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Dispara una sincronización con el backend bajo demanda (p. ej. justo tras el login). */
interface SyncScheduler {
    fun requestSyncNow()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncScheduler {
    override fun requestSyncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private companion object {
        const val SYNC_NOW_WORK = "wallet_backend_sync_now"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
