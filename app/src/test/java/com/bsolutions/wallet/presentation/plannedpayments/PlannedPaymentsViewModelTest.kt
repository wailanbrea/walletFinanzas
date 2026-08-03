package com.bsolutions.wallet.presentation.plannedpayments

import com.bsolutions.wallet.domain.model.PlannedPayment
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannedPaymentsViewModelTest {

    @Test
    fun `planned expenses and incomes are exposed in separate groups`() {
        val state = buildPlannedPaymentsUiState(
            payments = listOf(
                payment(id = "rent", type = "EXPENSE", amount = 50_000L),
                payment(id = "salary", type = "INCOME", amount = 180_000L),
                payment(id = "inactive-income", type = "INCOME", amount = 10_000L, isActive = false),
                // Compatibilidad con un registro anterior que no tenga un tipo válido.
                payment(id = "legacy", type = "", amount = 2_000L)
            ),
            accounts = emptyList(),
            categories = emptyList()
        )

        assertEquals(listOf("rent", "legacy"), state.expensePayments.map { it.id })
        assertEquals(listOf("salary", "inactive-income"), state.incomePayments.map { it.id })
        assertEquals(52_000L, state.activeExpenseTotal)
        assertEquals(180_000L, state.activeIncomeTotal)
    }

    private fun payment(
        id: String,
        type: String,
        amount: Long,
        isActive: Boolean = true
    ) = PlannedPayment(
        id = id,
        name = id,
        accountId = "account-1",
        categoryId = "category-1",
        amount = amount,
        type = type,
        frequency = "MONTHLY",
        nextDueDate = 1_786_000_000_000L,
        isActive = isActive
    )
}
