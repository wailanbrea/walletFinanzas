package com.bsolutions.wallet.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.CategoryRuleRepository
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoriesUiState(
    val categories: List<Category> = emptyList()
)

/**
 * Tipos validos de categoria. "BOTH" existe para los casos que tienen dos patas
 * —prestar dinero y que te lo devuelvan— y necesitan la misma etiqueta en ambas
 * para que se neteen en vez de inflar gastos e ingresos por separado.
 */
val CATEGORY_TYPES = listOf("EXPENSE", "INCOME", "BOTH")

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val categoryRules: CategoryRuleRepository
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = categoryRepository.getCategories()
        .map { CategoriesUiState(categories = it.sortedBy { cat -> cat.name }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState()
        )

    fun addCategory(name: String, icon: String, colorHex: String, type: String = "EXPENSE") {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.addCategory(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    icon = icon,
                    colorHex = colorHex,
                    type = type.takeIf { it in CATEGORY_TYPES } ?: "EXPENSE"
                )
            )
        }
    }

    fun updateCategory(
        category: Category,
        newName: String,
        newIcon: String,
        newColorHex: String,
        newType: String = category.type
    ) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            // addCategory usa REPLACE en el DAO, así que sirve como update
            categoryRepository.addCategory(
                category.copy(
                    name = newName.trim(),
                    icon = newIcon,
                    colorHex = newColorHex,
                    type = newType.takeIf { it in CATEGORY_TYPES } ?: category.type
                )
            )
        }
    }

    fun deleteCategory(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            categoryRepository.deleteCategory(id)
            categoryRules.removeByCategory(id)
        }
    }
}
