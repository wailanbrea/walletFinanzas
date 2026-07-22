package com.bsolutions.wallet.core.common

import com.bsolutions.wallet.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseCategorizerTest {
    private val categories = DefaultCategories.asCategories()

    @Test
    fun `regla integrada usa id estable aunque la categoria se renombre`() {
        val renamed = categories.map {
            if (it.id == "cat_transporte") it.copy(name = "Movilidad") else it
        }

        val result = ExpenseCategorizer.categoryIdFor("Pago de gasolina", renamed)

        assertEquals("cat_transporte", result)
    }

    @Test
    fun `regla personalizada valida tiene prioridad sobre regla integrada`() {
        val result = ExpenseCategorizer.categoryIdFor(
            text = "Cena de trabajo en restaurante",
            categories = categories,
            customRules = listOf(CustomCategoryRule("trabajo", "cat_educacion"))
        )

        assertEquals("cat_educacion", result)
    }

    @Test
    fun `regla personalizada huerfana se ignora y aplica la integrada`() {
        val result = ExpenseCategorizer.categoryIdFor(
            text = "Uber a casa",
            categories = categories,
            customRules = listOf(CustomCategoryRule("uber", "categoria_eliminada"))
        )

        assertEquals("cat_transporte", result)
    }

    @Test
    fun `normaliza mayusculas y acentos`() {
        assertEquals("cat_educacion", ExpenseCategorizer.categoryIdFor("MATRÍCULA UNIVERSITARIA", categories))
    }

    @Test
    fun `no asigna una categoria sin coincidencia`() {
        assertNull(ExpenseCategorizer.categoryIdFor("Movimiento desconocido 123", categories))
    }

    @Test
    fun `no devuelve una categoria integrada que fue eliminada`() {
        val withoutTransport = categories.filterNot { it.id == "cat_transporte" }

        assertNull(ExpenseCategorizer.categoryIdFor("Taxi", withoutTransport))
    }
}
