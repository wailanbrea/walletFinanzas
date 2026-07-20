package com.bsolutions.wallet.presentation.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = categoryRepository.getCategories()
        .map { CategoriesUiState(categories = it.sortedBy { cat -> cat.name }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState()
        )

    fun addCategory(name: String, icon: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.addCategory(
                Category(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    icon = icon,
                    colorHex = colorHex
                )
            )
        }
    }

    fun updateCategory(category: Category, newName: String, newIcon: String, newColorHex: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            // addCategory usa REPLACE en el DAO, así que sirve como update
            categoryRepository.addCategory(
                category.copy(name = newName.trim(), icon = newIcon, colorHex = newColorHex)
            )
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch { categoryRepository.deleteCategory(id) }
    }
}
