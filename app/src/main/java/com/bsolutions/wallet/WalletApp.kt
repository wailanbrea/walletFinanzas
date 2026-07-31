package com.bsolutions.wallet

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bsolutions.wallet.core.email.EmailSyncWorker
import com.bsolutions.wallet.core.notifications.PlannedPaymentWorker
import com.bsolutions.wallet.core.sync.SyncScheduler
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

    @Inject
    lateinit var syncScheduler: SyncScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Actividades visibles. De 0 a 1 es el momento en que la app pasa a primer plano. */
    private var startedActivities = 0

    /** Cuando se sincronizo por lo ultimo al abrir; null si todavia no ha pasado. */
    private var lastForegroundSync: Long? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { localDataIsolation.reconcileCurrentSession() }
        syncWhenAppComesToForeground()
        schedulePlannedPaymentChecks()
        scheduleConnectedEmailSync()
        scheduleBackendSync()
    }

    /**
     * Sincroniza cada vez que la app pasa a primer plano.
     *
     * Sin esto, abrir la app no traia nada: onCreate solo programaba el trabajo periodico
     * de 30 minutos, y con ExistingPeriodicWorkPolicy.KEEP un trabajo ya programado ni
     * siquiera se adelantaba. En un segundo telefono eso significaba abrir la app y ver
     * datos de hace media hora, o de la ultima vez que se sincronizo a mano.
     *
     * Se cuentan las actividades visibles en vez de usar ProcessLifecycleOwner para no
     * anadir la dependencia de lifecycle-process por una sola senal. El paso de 0 a 1 es
     * exactamente "la app se acaba de abrir o se volvio a ella"; girar la pantalla no
     * cuenta, porque ahi la actividad se recrea sin que el contador llegue a cero.
     *
     * Engancharse aqui y no en la Activity tiene otra ventaja: un arranque en frio del
     * proceso para un worker de fondo no dispara nada, porque no hay actividad ninguna.
     */
    private fun syncWhenAppComesToForeground() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val cameFromBackground = startedActivities == 0
                startedActivities++
                if (!cameFromBackground) return

                // Entrar y salir de la app varias veces seguidas no debe disparar una
                // sincronizacion por cada vuelta: no habria dado tiempo a que el servidor
                // cambiara, y son peticiones y bateria gastadas para nada.
                val now = SystemClock.elapsedRealtime()
                val previous = lastForegroundSync
                if (previous != null && now - previous < FOREGROUND_SYNC_MIN_INTERVAL_MS) return
                lastForegroundSync = now

                // Sube lo pendiente y baja lo del servidor. Sin sesion sale enseguida.
                syncScheduler.requestSyncNow()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
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

        /**
         * Tiempo minimo entre dos sincronizaciones al abrir. Corto a proposito: el caso
         * que importa es dejar el telefono, hacer algo en el otro y volver, y eso pasa en
         * menos de un minuto. Solo esta para que rebotar entre apps no dispare una
         * peticion por cada vuelta.
         */
        const val FOREGROUND_SYNC_MIN_INTERVAL_MS = 20_000L
    }
}
