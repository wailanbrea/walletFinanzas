package com.bsolutions.wallet.core.sync

import android.content.Context
import com.bsolutions.wallet.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Provider
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

/**
 * Dispara una sincronización con el backend bajo demanda: tras el login y tras cada
 * cambio local, para que editar o borrar no espere al ciclo periódico de 30 minutos.
 */
interface SyncScheduler {
    fun requestSyncNow()
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    // Provider y no inyeccion directa: SyncRepository arrastra la base de datos entera
    // y no hace falta construirla solo por programar trabajo.
    private val syncRepository: Provider<SyncRepository>
) : SyncScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = Mutex()

    override fun requestSyncNow() {
        // Se intenta de inmediato y ademas se encola.
        //
        // WorkManager por si solo no sirve para "sube esto ahora": Android difiere el
        // trabajo de fondo (TIMING_DELAY, FLEXIBILITY) y en algunos fabricantes puede
        // tardar mucho, asi que un cambio recien hecho parecia no subir nunca. La
        // subida directa cubre el caso normal -la app esta abierta y hay red- y el
        // trabajo encolado queda como red de seguridad si el proceso muere antes.
        scope.launch {
            // Una sola subida a la vez: varias ediciones seguidas no lanzan copias.
            if (!running.tryLock()) return@launch
            try {
                runCatching { syncRepository.get().sync() }
            } finally {
                running.unlock()
            }
        }
        enqueueBackgroundSync()
    }

    private fun enqueueBackgroundSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        // APPEND_OR_REPLACE y no REPLACE: si ya hay una subida en curso no se cancela
        // a medias, se encola otra detras. Con REPLACE, editar varias cosas seguidas
        // podia ir abortando la sincronizacion una y otra vez sin que terminara nunca.
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
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
