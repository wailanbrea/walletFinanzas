package com.bsolutions.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bsolutions.wallet.core.email.EmailSyncWorker
import com.bsolutions.wallet.core.notifications.PlannedPaymentWorker
import com.bsolutions.wallet.core.sync.SyncWorker
import com.bsolutions.wallet.core.database.LocalDataIsolation
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class WalletApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var localDataIsolation: LocalDataIsolation

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { localDataIsolation.reconcileCurrentSession() }
        schedulePlannedPaymentChecks()
        scheduleConnectedEmailSync()
        scheduleBackendSync()
    }

    /**
     * Los logos oficiales de la Superintendencia de Bancos son SVG en su mayoría, así que
     * el decodificador se registra globalmente. La caché en disco evita volver a pedirlos:
     * después de la primera descarga, la lista puede pintarse completa sin red.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("institution_logos"))
                .maxSizeBytes(INSTITUTION_LOGO_CACHE_BYTES)
                .build()
        }
        .build()

    /** Sincronización con el backend (push/pull) cada 30 min, solo con conexión. */
    private fun scheduleBackendSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SyncWorker.REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Revisión de pagos planificados cada 12h (límite de 00_REGLAS_COSTO). */
    private fun schedulePlannedPaymentChecks() {
        val request = PeriodicWorkRequestBuilder<PlannedPaymentWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PlannedPaymentWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Sondeo mínimo permitido por WorkManager para detectar correos nuevos. */
    private fun scheduleConnectedEmailSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<EmailSyncWorker>(
            EmailSyncWorker.REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            EmailSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        /** 28 entidades; sobra de largo para el catálogo completo con sus variantes. */
        const val INSTITUTION_LOGO_CACHE_BYTES = 16L * 1024 * 1024
    }
}
