package com.bsolutions.wallet.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.data.local.entity.CategoryEntity
import com.bsolutions.wallet.data.local.entity.WALLET_GUEST_OWNER_ID
import com.bsolutions.wallet.data.repository.AuthUser
import com.bsolutions.wallet.data.repository.WalletSessionStore
import com.bsolutions.wallet.data.preferences.UserPreferencesRepository
import com.bsolutions.wallet.data.preferences.CategoryRuleStore
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
}

private class IsolationSessionStore : WalletSessionStore {
    override val token: String? = null
    override val user: AuthUser? = null
    override fun save(token: String, user: AuthUser) = Unit
    override fun clear() = Unit
}
