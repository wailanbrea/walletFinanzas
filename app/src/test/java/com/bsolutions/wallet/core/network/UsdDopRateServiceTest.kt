package com.bsolutions.wallet.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsdDopRateServiceTest {
    @Test
    fun `converts USD minor units to DOP minor units without floating point`() {
        assertEquals(25_000L, UsdDopRateService.convertUsdMinorToDop(400L, 62_500_000L))
        assertEquals(-25_000L, UsdDopRateService.convertUsdMinorToDop(-400L, 62_500_000L))
    }

    @Test
    fun `rejects zero and invalid rates`() {
        assertNull(UsdDopRateService.convertUsdMinorToDop(0L, 62_500_000L))
        assertNull(UsdDopRateService.convertUsdMinorToDop(400L, 0L))
    }
}