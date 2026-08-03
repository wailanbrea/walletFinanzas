package com.bsolutions.wallet.core.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class KnownBankingApp(
    val packageName: String,
    val displayName: String
)

/**
 * Lista cerrada de apps financieras dominicanas que pueden producir movimientos.
 * Una coincidencia solo crea una sugerencia desactivada; nunca autoriza la captura.
 */
object KnownBankingApps {
    val supported: List<KnownBankingApp> = listOf(
        KnownBankingApp("com.banreservas.tubancoappmobile", "Banreservas"),
        KnownBankingApp("com.qik.android.app", "Qik Banco Digital"),
        KnownBankingApp("com.popular.app.android", "Banco Popular"),
        KnownBankingApp("com.artech.infocorp_bhd.bhd", "Móvil Banking BHD"),
        KnownBankingApp("com.sii.bdi", "BDI App"),
        KnownBankingApp("com.popular.joven", "gnial — Banco Popular"),
        KnownBankingApp("com.popular.pinkapp", "Toke — Banco Popular")
    )

    init {
        check(supported.map(KnownBankingApp::packageName).distinct().size == supported.size) {
            "El catálogo de apps bancarias contiene paquetes duplicados."
        }
    }
}

@Singleton
class InstalledBankingAppsDetector internal constructor(
    private val isPackageInstalled: (String) -> Boolean
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        isPackageInstalled = { packageName -> context.isPackageInstalled(packageName) }
    )

    fun detect(): List<KnownBankingApp> =
        KnownBankingApps.supported.filter { isPackageInstalled(it.packageName) }
}

private fun Context.isPackageInstalled(packageName: String): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(packageName, 0)
    }
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
