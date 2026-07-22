package com.bsolutions.wallet.presentation.emailconnections

import org.junit.Assert.assertEquals

import org.junit.Test
import java.time.ZoneId

class EmailCandidateDateFormatterTest {

    @Test
    fun `formats candidate ISO date in requested time zone`() {
        assertEquals(
            "20/07/2026 · 14:30",
            formatEmailCandidateDate("2026-07-20T14:30:00Z", ZoneId.of("UTC"))
        )
    }

    @Test
    fun `invalid candidate date remains visible with explicit fallback`() {
        assertEquals("—", formatEmailCandidateDate("not-a-date", ZoneId.of("UTC")))
    }
}
