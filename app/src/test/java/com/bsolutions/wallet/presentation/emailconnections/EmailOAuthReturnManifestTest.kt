package com.bsolutions.wallet.presentation.emailconnections

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailOAuthReturnManifestTest {
    @Test
    fun `main activity accepts the email oauth return URI`() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first(File::isFile).readText()

        assertTrue(
            "MainActivity must handle walletfinanzas://email-oauth after provider consent",
            manifest.contains("android:scheme=\"walletfinanzas\"") &&
                manifest.contains("android:host=\"email-oauth\"")
        )
    }
}
