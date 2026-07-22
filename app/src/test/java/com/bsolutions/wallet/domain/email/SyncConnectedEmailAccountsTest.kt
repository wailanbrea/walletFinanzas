package com.bsolutions.wallet.domain.email

import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionStatus
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.EmailSyncResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncConnectedEmailAccountsTest {
    @Test
    fun `syncs only connected providers`() = runTest {
        val repository = FakeEmailConnectionsRepository(
            connections = listOf(
                connection(EmailProvider.GMAIL, EmailConnectionStatus.CONNECTED),
                connection(EmailProvider.MICROSOFT, EmailConnectionStatus.DISCONNECTED)
            )
        )

        val result = SyncConnectedEmailAccounts(repository)()

        assertEquals(listOf(EmailProvider.GMAIL), repository.syncedProviders)
        assertEquals(1, result.attempted)
        assertEquals(0, result.failed)
    }

    @Test
    fun `continues syncing remaining providers after one fails`() = runTest {
        val repository = FakeEmailConnectionsRepository(
            connections = listOf(
                connection(EmailProvider.GMAIL, EmailConnectionStatus.CONNECTED),
                connection(EmailProvider.MICROSOFT, EmailConnectionStatus.CONNECTED)
            ),
            failingProvider = EmailProvider.GMAIL
        )

        val result = SyncConnectedEmailAccounts(repository)()

        assertEquals(listOf(EmailProvider.GMAIL, EmailProvider.MICROSOFT), repository.syncedProviders)
        assertEquals(2, result.attempted)
        assertEquals(1, result.failed)
    }

    private fun connection(provider: EmailProvider, status: EmailConnectionStatus) = EmailConnection(
        provider = provider,
        displayName = provider.name,
        status = status,
        email = null,
        configurationReady = true,
        connectedAt = null,
        expiresAt = null
    )
}

private class FakeEmailConnectionsRepository(
    private val connections: List<EmailConnection>,
    private val failingProvider: EmailProvider? = null
) : EmailConnectionsRepository {
    val syncedProviders = mutableListOf<EmailProvider>()

    override suspend fun getConnections() = connections
    override suspend fun getCandidates(): List<EmailCandidate> = emptyList()
    override suspend fun getAuthorizationUrl(provider: EmailProvider) = ""
    override suspend fun sync(provider: EmailProvider): EmailSyncResult {
        syncedProviders += provider
        if (provider == failingProvider) error("provider failure")
        return EmailSyncResult(0, 0, 0)
    }
    override suspend fun reviewCandidate(id: String, action: String, category: String?): EmailCandidate =
        error("Not used")
    override suspend fun disconnect(provider: EmailProvider) = Unit
}
