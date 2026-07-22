package com.bsolutions.wallet.presentation.emailconnections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOAuthReturnContractTest {
    @Test
    fun `accepts only the wallet email oauth return URI`() {
        assertTrue(
            EmailOAuthReturnContract.matches(
                "walletfinanzas://email-oauth?provider=gmail&status=connected"
            )
        )
        assertTrue(
            EmailOAuthReturnContract.matches(
                "walletfinanzas://email-oauth?provider=microsoft&status=connected"
            )
        )
        assertFalse(EmailOAuthReturnContract.matches("walletfinanzas://saltedge?status=success"))
        assertFalse(EmailOAuthReturnContract.matches("walletfinanzas://email-oauth?provider=other&status=connected"))
        assertFalse(EmailOAuthReturnContract.matches("walletfinanzas://email-oauth?provider=gmail&status=failed"))
        assertFalse(EmailOAuthReturnContract.matches("https://example.com/email-oauth"))
        assertFalse(EmailOAuthReturnContract.matches(null))
    }
}
