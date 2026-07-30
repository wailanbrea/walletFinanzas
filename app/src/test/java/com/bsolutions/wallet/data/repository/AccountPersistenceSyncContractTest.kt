package com.bsolutions.wallet.data.repository

import com.bsolutions.wallet.core.network.AccountDto
import com.bsolutions.wallet.data.local.entity.AccountEntity
import com.bsolutions.wallet.domain.model.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountPersistenceSyncContractTest {
    @Test
    fun `domain and entity mappers preserve nullable credit limit`() {
        val account = Account(
            id = "credit-1",
            name = "Tarjeta",
            type = "CREDIT_CARD",
            balance = -2_500,
            currency = "DOP",
            creditLimit = 150_000
        )

        val entity = account.toEntity("owner-1")

        assertEquals(150_000L, entity.creditLimit)
        assertEquals(account, entity.toDomain())
    }

    @Test
    fun `sync mappers preserve backend account type and credit limit in both directions`() {
        val pulled = AccountDto(
            id = "credit-1",
            name = "Tarjeta",
            balance = -2_500,
            currency = "DOP",
            institutionName = "Banco",
            countryCode = "DO",
            cardLastFour = "1234",
            isActive = true,
            type = "CREDIT_CARD",
            creditLimit = 150_000
        ).toAccountEntity("owner-1")

        assertEquals("CREDIT_CARD", pulled.type)
        assertEquals(150_000L, pulled.creditLimit)
        assertEquals("owner-1", pulled.ownerId)

        val pushed = AccountEntity(
            id = "credit-2",
            name = "Otra tarjeta",
            type = "CREDIT_CARD",
            balance = 0,
            creditLimit = null
        ).toCreateAccountRequest()

        assertEquals("CREDIT_CARD", pushed.type)
        assertEquals(null, pushed.creditLimit)
    }
}
