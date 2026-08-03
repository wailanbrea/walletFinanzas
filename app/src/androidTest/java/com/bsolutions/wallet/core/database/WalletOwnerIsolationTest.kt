package com.bsolutions.wallet.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.core.notifications.NotificationCaptureData
import com.bsolutions.wallet.core.notifications.InstalledBankingAppsDetector
import com.bsolutions.wallet.core.notifications.ParsedBankNotice
import com.bsolutions.wallet.data.repository.BankNotificationRepository
import com.bsolutions.wallet.data.repository.DetectedMovementRepository
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.data.repository.NotificationCaptureOutcome
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import com.bsolutions.wallet.data.repository.AuthUser
import com.bsolutions.wallet.data.repository.WalletSessionStore
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.preferences.CategoryRuleStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletOwnerIsolationTest {
    private lateinit var database: WalletDatabase
    private lateinit var ownerScope: WalletOwnerScope
    private lateinit var isolation: RoomLocalDataIsolation
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var categoryRules: CategoryRuleStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WalletDatabase::class.java
        ).allowMainThreadQueries().build()
        ownerScope = WalletOwnerScope(IsolationSessionStore())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        preferences = UserPreferencesRepository(context, ownerScope)
        categoryRules = CategoryRuleStore(context, ownerScope)
        isolation = RoomLocalDataIsolation(
            database,
            ownerScope,
            preferences,
            categoryRules
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun guestDataIsClaimedAndOtherUsersRemainInvisible() = runBlocking {
        val sharedId = "00000000-0000-4000-8000-000000000001"
        database.accountDao().insertAccount(
            AccountEntity(id = sharedId, name = "Invitado", type = "CASH", balance = 100)
        )
        database.accountDao().insertAccount(
            AccountEntity(
                id = sharedId,
                name = "Segundo usuario",
                type = "CASH",
                balance = 900,
                ownerId = "user:2"
            )
        )
        database.categoryDao().insertCategory(
            CategoryEntity(
                id = "cat_comida",
                name = "Comida",
                icon = "restaurant",
                colorHex = "#E57373"
            )
        )

        isolation.activateUser("1")

        assertEquals("user:1", ownerScope.currentOwnerId())
        assertEquals("Invitado", database.accountDao().getAllAccounts("user:1").first().single().name)
        assertEquals(1, database.categoryDao().getAllCategories("user:1").first().size)
        assertTrue(database.accountDao().getAllAccounts(WALLET_GUEST_OWNER_ID).first().isEmpty())
        assertEquals("Segundo usuario", database.accountDao().getAllAccounts("user:2").first().single().name)

        isolation.activateGuest()
        assertEquals(WALLET_GUEST_OWNER_ID, ownerScope.currentOwnerId())
        assertTrue(database.accountDao().getAllAccounts(ownerScope.currentOwnerId()).first().isEmpty())

        isolation.activateUser("2")
        assertEquals("Segundo usuario", database.accountDao().getAllAccounts("user:2").first().single().name)
    }

    @Test
    fun profileAndCategoryRulesFollowTheActiveOwner() = runBlocking {
        val userId = UUID.randomUUID().toString()
        preferences.saveProfile("Perfil invitado", "guest@example.com", "Wallet invitado")
        categoryRules.add("cafeteria", "cat_comida")

        isolation.activateUser(userId)

        assertEquals("Perfil invitado", preferences.profile.first().userName)
        assertEquals("guest@example.com", preferences.profile.first().email)
        assertTrue(categoryRules.rules.first().any { it.keyword == "cafeteria" })

        isolation.activateGuest()

        assertEquals("Mi Perfil", preferences.profile.first().userName)
        assertEquals("", preferences.profile.first().email)
        assertTrue(categoryRules.rules.first().isEmpty())
    }

    @Test
    fun rawNoticesAreDeduplicatedFilteredAndClaimedByTheSignedInUser() = runBlocking {
        val repository = notificationRepository()
        val otp = capture(
            key = "otp",
            title = "Código de seguridad",
            text = "Tu código es 482901. No lo compartas"
        )
        assertEquals(NotificationCaptureOutcome.DISCARDED_SENSITIVE, repository.processNotification(otp, 1_000))
        assertTrue(repository.sources.first().isEmpty())

        val purchase = capture(
            key = "purchase",
            title = "Compra aprobada",
            text = "RD$ 1,250.00 en comercio"
        )
        assertEquals(NotificationCaptureOutcome.SOURCE_DISCOVERED, repository.processNotification(purchase, 2_000))
        repository.setSourceEnabled(purchase.packageName, true)
        assertEquals(NotificationCaptureOutcome.CAPTURED, repository.processNotification(purchase, 3_000))
        assertEquals(NotificationCaptureOutcome.DUPLICATE, repository.processNotification(purchase, 4_000))
        assertEquals(1, repository.notices().first().size)

        isolation.activateUser("owner")
        assertTrue(repository.notices().first().single().ownerId == "user:owner")
        assertTrue(database.rawBankNoticeDao().getAllNotices(WALLET_GUEST_OWNER_ID).isEmpty())
        assertTrue(database.detectedMovementDao().getAll("user:owner").single().ownerId == "user:owner")
        assertTrue(database.detectedMovementDao().getAll(WALLET_GUEST_OWNER_ID).isEmpty())
    }

    @Test
    fun installedKnownAppsAreSuggestedDisabledAndIdempotent() = runBlocking {
        val installedPackages = setOf("com.qik.android.app", "com.popular.pinkapp")
        val repository = notificationRepository(installedPackages)

        assertEquals(2, repository.discoverInstalledKnownApps())
        assertEquals(0, repository.discoverInstalledKnownApps())

        val suggestions = repository.sources.first()
        assertEquals(installedPackages, suggestions.mapTo(mutableSetOf()) { it.packageName })
        assertTrue(suggestions.all { !it.isEnabled })
        assertTrue(suggestions.all { it.observedCount == 0 && it.lastSeenAt == 0L })

        repository.setSourceEnabled("com.qik.android.app", true)
        assertEquals(0, repository.discoverInstalledKnownApps())
        assertTrue(repository.sources.first().single { it.packageName == "com.qik.android.app" }.isEnabled)
    }

    @Test
    fun emailAndBankNotificationShareOneCanonicalMovementInRoom() = runBlocking {
        val movementRepository = DetectedMovementRepository(
            database.detectedMovementDao(),
            database.transactionDao()
        )
        val email = EmailCandidate(
            id = "gmail-message-1",
            provider = EmailProvider.GMAIL,
            merchant = "Supermercado Bravo",
            cardLastFour = "5678",
            amount = 245_000,
            currency = "DOP",
            direction = "expense",
            eventType = "CARD_PURCHASE_APPROVED",
            senderDomain = "banreservas.com",
            categorySuggestion = "cat_alimentacion",
            occurredAt = "2023-11-14T22:13:20Z",
            confidence = 90,
            status = "pending",
            subject = "Aviso de consumo"
        )

        movementRepository.ingestEmailCandidates(listOf(email), WALLET_GUEST_OWNER_ID)
        val push = movementRepository.ingestNotification(
            ownerId = WALLET_GUEST_OWNER_ID,
            noticeId = "notice-1",
            appLabel = "Banreservas",
            title = "Compra aprobada",
            occurredAt = 1_700_000_000_000,
            parsed = ParsedBankNotice(
                merchant = "SUPERMERCADO BRAVO",
                amountMinor = 245_000,
                currency = "DOP",
                last4Digits = "5678",
                suggestedCategoryId = "cat_alimentacion"
            )
        )

        assertEquals("notification:notice-1", push.canonicalId)
        assertEquals(
            listOf("notification:notice-1"),
            movementRepository.getPendingMovements(WALLET_GUEST_OWNER_ID).first().map { it.id }
        )
        val evidence = movementRepository.getEvidence(push.canonicalId, WALLET_GUEST_OWNER_ID)
        assertEquals(2, evidence.size)
        assertEquals(1, evidence.count { it.duplicateOfId == push.canonicalId })
        assertEquals(1, evidence.count { it.id == push.canonicalId && it.duplicateOfId == null })
    }

    @Test
    fun ambiguousEmailAndPushCanBeMergedInRoomWithoutLosingEvidence() = runBlocking {
        val movementRepository = DetectedMovementRepository(
            database.detectedMovementDao(),
            database.transactionDao()
        )
        val email = EmailCandidate(
            id = "gmail-ambiguous",
            provider = EmailProvider.GMAIL,
            merchant = null,
            amount = 12_500,
            currency = "DOP",
            direction = "expense",
            categorySuggestion = null,
            occurredAt = "2023-11-14T22:13:20Z",
            confidence = 65,
            status = "pending",
            subject = "Aviso de consumo"
        )
        movementRepository.ingestEmailCandidates(listOf(email), WALLET_GUEST_OWNER_ID)
        movementRepository.ingestNotification(
            ownerId = WALLET_GUEST_OWNER_ID,
            noticeId = "notice-ambiguous",
            appLabel = "Banco",
            title = "Compra",
            occurredAt = 1_700_000_100_000,
            parsed = ParsedBankNotice(
                merchant = null,
                amountMinor = 12_500,
                currency = "DOP",
                last4Digits = null,
                suggestedCategoryId = null
            )
        )
        val possible = movementRepository.getPendingMovements(WALLET_GUEST_OWNER_ID).first()
            .single { it.possibleDuplicateOfId != null }

        movementRepository.resolvePossibleDuplicate(
            movementId = possible.id,
            keepSeparate = false,
            ownerId = WALLET_GUEST_OWNER_ID
        )

        val root = movementRepository.getPendingMovements(WALLET_GUEST_OWNER_ID).first().single()
        assertEquals(2, movementRepository.getEvidence(root.id, WALLET_GUEST_OWNER_ID).size)
        assertEquals(2, database.detectedMovementDao().getAll(WALLET_GUEST_OWNER_ID).size)
    }

    private fun notificationRepository(installedPackages: Set<String> = emptySet()) =
        BankNotificationRepository(
            dao = database.rawBankNoticeDao(),
            ownerScope = ownerScope,
            gson = Gson(),
            installedAppsDetector = InstalledBankingAppsDetector(installedPackages::contains),
            detectedMovementRepository = DetectedMovementRepository(
                database.detectedMovementDao(),
                database.transactionDao()
            )
        )

    private fun capture(key: String, title: String, text: String) = NotificationCaptureData(
        packageName = "com.bank.app",
        appLabel = "Banco",
        notificationKey = key,
        title = title,
        text = text,
        bigText = "",
        postTime = 1_700_000_000_000
    )
}

private class IsolationSessionStore : WalletSessionStore {
    override val token: String? = null
    override val user: AuthUser? = null
    override fun save(token: String, user: AuthUser) = Unit
    override fun clear() = Unit
}
