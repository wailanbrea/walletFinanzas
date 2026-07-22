package com.bsolutions.wallet.presentation.emailconnections

import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionStatus
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.EmailSyncResult
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailConnectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load exposes content returned by backend`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()))
        val viewModel = createViewModel(repository)

        advanceUntilIdle()

        assertEquals(EmailConnectionsPhase.CONTENT, viewModel.uiState.value.phase)
        assertEquals("ana@example.com", viewModel.uiState.value.connections.single().email)
    }

    @Test
    fun `load distinguishes empty and error states`() = runTest {
        val emptyViewModel = createViewModel(FakeRepository())
        advanceUntilIdle()
        assertEquals(EmailConnectionsPhase.EMPTY, emptyViewModel.uiState.value.phase)

        val errorViewModel = createViewModel(FakeRepository(loadError = true))
        advanceUntilIdle()
        assertEquals(EmailConnectionsPhase.ERROR, errorViewModel.uiState.value.phase)
        assertEquals("No se pudieron cargar las conexiones de correo.", errorViewModel.uiState.value.message)
    }

    @Test
    fun `connect emits one authorization URL and consume clears it`() = runTest {
        val viewModel = createViewModel(FakeRepository(authorizationUrl = "https://login.example/oauth"))
        advanceUntilIdle()

        viewModel.connect(EmailProvider.GMAIL)
        advanceUntilIdle()

        assertEquals("https://login.example/oauth", viewModel.uiState.value.authorizationUrl)
        viewModel.consumeAuthorizationUrl()
        assertNull(viewModel.uiState.value.authorizationUrl)
    }

    @Test
    fun `disconnect refreshes connections after backend confirms`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.disconnect(EmailProvider.GMAIL)
        advanceUntilIdle()

        assertEquals(listOf(EmailProvider.GMAIL), repository.disconnected)
        assertEquals(EmailConnectionsPhase.EMPTY, viewModel.uiState.value.phase)
    }

    @Test
    fun `returning from authorization refreshes backend state`() = runTest {
        val repository = FakeRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        repository.connections = listOf(gmailConnected())

        viewModel.onAuthorizationReturn()
        advanceUntilIdle()

        assertEquals(2, repository.loadCount)
        assertEquals(EmailConnectionsPhase.CONTENT, viewModel.uiState.value.phase)
    }

    @Test
    fun `manual sync refreshes classified candidates and exposes counts`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected()))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        repository.candidates = listOf(financialCandidate())

        viewModel.sync(EmailProvider.GMAIL)
        advanceUntilIdle()

        assertEquals(listOf(EmailProvider.GMAIL), repository.synced)
        assertEquals(1, viewModel.uiState.value.candidates.size)
        assertEquals(1, viewModel.uiState.value.syncResult?.candidatesCreated)
        assertNull(viewModel.uiState.value.actionProvider)
    }

    @Test
    fun `candidates are separated by Gmail and Microsoft`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected())).apply {
            candidates = listOf(
                financialCandidate("gmail-1", EmailProvider.GMAIL),
                financialCandidate("microsoft-1", EmailProvider.MICROSOFT)
            )
        }
        val viewModel = createViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf("gmail-1"), viewModel.uiState.value.candidatesByProvider[EmailProvider.GMAIL]?.map { it.id })
        assertEquals(listOf("microsoft-1"), viewModel.uiState.value.candidatesByProvider[EmailProvider.MICROSOFT]?.map { it.id })
    }

    @Test
    fun `classifying candidate calls backend and removes reviewed item`() = runTest {
        val repository = FakeRepository(connections = listOf(gmailConnected())).apply {
            candidates = listOf(financialCandidate())
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.classify("candidate-1", "Compras en línea")
        advanceUntilIdle()

        assertEquals(Triple("candidate-1", "categorize", "Compras en línea"), repository.reviews.single())
        assertEquals(emptyList<EmailCandidate>(), viewModel.uiState.value.candidates)
    }

    private fun gmailConnected() = EmailConnection(
        provider = EmailProvider.GMAIL,
        displayName = "Gmail",
        status = EmailConnectionStatus.CONNECTED,
        email = "ana@example.com",
        configurationReady = true,
        connectedAt = "2026-07-20T10:15:00Z",
        expiresAt = null
    )

    private fun createViewModel(repository: EmailConnectionsRepository) =
        EmailConnectionsViewModel(repository, FakeCategoryRepository())

    private fun financialCandidate(
        id: String = "candidate-1",
        provider: EmailProvider = EmailProvider.GMAIL
    ) = EmailCandidate(
        id = id,
        provider = provider,
        merchant = "Supermercado Nacional",
        amount = -325000,
        currency = "DOP",
        direction = "expense",
        categorySuggestion = "Alimentación",
        occurredAt = "2026-07-20T14:30:00Z",
        confidence = 90,
        status = "pending",
        subject = "Compra aprobada"
    )

    private class FakeRepository(
        var connections: List<EmailConnection> = emptyList(),
        private val authorizationUrl: String = "https://example.test/oauth",
        private val loadError: Boolean = false
    ) : EmailConnectionsRepository {
        var loadCount = 0
        val disconnected = mutableListOf<EmailProvider>()
        val synced = mutableListOf<EmailProvider>()
        val reviews = mutableListOf<Triple<String, String, String?>>()
        var candidates: List<EmailCandidate> = emptyList()

        override suspend fun getConnections(): List<EmailConnection> {
            loadCount++
            if (loadError) error("network")
            return connections
        }

        override suspend fun getAuthorizationUrl(provider: EmailProvider): String = authorizationUrl

        override suspend fun getCandidates(): List<EmailCandidate> = candidates

        override suspend fun sync(provider: EmailProvider): EmailSyncResult {
            synced += provider
            return EmailSyncResult(messagesDiscovered = 1, messagesCreated = 1, candidatesCreated = 1)
        }

        override suspend fun reviewCandidate(id: String, action: String, category: String?): EmailCandidate {
            reviews += Triple(id, action, category)
            val current = candidates.first { it.id == id }
            val reviewed = current.copy(
                status = if (action == "dismiss") "dismissed" else "classified",
                categorySuggestion = category ?: current.categorySuggestion
            )
            candidates = candidates.filterNot { it.id == id }
            return reviewed
        }

        override suspend fun disconnect(provider: EmailProvider) {
            disconnected += provider
            connections = connections.filterNot { it.provider == provider }
        }
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val categories = MutableStateFlow(
            listOf(Category("cat_alimentacion", "Alimentación", "restaurant", "#E57373"))
        )

        override fun getCategories(): Flow<List<Category>> = categories
        override suspend fun getCategory(id: String): Category? = categories.value.firstOrNull { it.id == id }
        override suspend fun getAllCategoryIdsIncludingDeleted(): Set<String> = categories.value.mapTo(mutableSetOf()) { it.id }
        override suspend fun addCategory(category: Category) {
            categories.value = categories.value.filterNot { it.id == category.id } + category
        }
        override suspend fun deleteCategory(id: String) {
            categories.value = categories.value.filterNot { it.id == id }
        }
    }
}
