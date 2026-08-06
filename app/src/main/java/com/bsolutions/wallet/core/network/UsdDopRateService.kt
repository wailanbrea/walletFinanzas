package com.bsolutions.wallet.core.network

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Obtiene la tasa USD-DOP del día actual para precargar una estimación editable. */
@Singleton
class UsdDopRateService @Inject constructor(
    private val api: WalletApi?
) {
    suspend fun currentRateMicros(): Long? = runCatching {
        api?.currentUsdDopRate(LATEST_URL)?.usd?.get("dop")
            ?.movePointRight(RATE_SCALE)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.longValueExact()
            ?.takeIf { it > 0L }
    }.getOrNull()

    companion object {
        const val LATEST_URL =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
        private const val RATE_SCALE = 6

        /** Convierte minor units sin usar Double/Float. */
        fun convertUsdMinorToDop(amountMinor: Long, rateMicros: Long): Long? {
            if (amountMinor == 0L || rateMicros <= 0L) return null
            val absolute = if (amountMinor == Long.MIN_VALUE) return null else abs(amountMinor)
            val converted = BigDecimal.valueOf(absolute, 2)
                .multiply(BigDecimal.valueOf(rateMicros, RATE_SCALE))
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
            return if (amountMinor < 0L) -converted else converted
        }
    }
}

data class CurrentUsdDopRateDto(
    val date: String? = null,
    @SerializedName("usd") val usd: Map<String, BigDecimal>? = null
)