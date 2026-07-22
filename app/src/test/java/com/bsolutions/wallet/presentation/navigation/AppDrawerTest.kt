package com.bsolutions.wallet.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppDrawerTest {
    @Test
    fun `drawer exposes email sync instead of bank sync`() {
        assertEquals(1, drawerItems.count { it.route == "email_connections" })
        assertFalse(drawerItems.any { it.route == "sync_settings" })
    }
}
