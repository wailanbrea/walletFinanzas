package com.bsolutions.wallet.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.domain.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GoalsUiState(
    val goals: List<Goal> = emptyList(),
    val totalSaved: Long = 0L,
    val totalTarget: Long = 0L
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository
) : ViewModel() {

    val uiState: StateFlow<GoalsUiState> = goalRepository.getGoals()
        .map { goals ->
            GoalsUiState(
                goals = goals,
                totalSaved = goals.sumOf { it.savedAmount },
                totalTarget = goals.sumOf { it.targetAmount }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GoalsUiState()
        )

    fun addGoal(name: String, targetAmount: Long, targetDate: Long?) {
        if (name.isBlank() || targetAmount <= 0L) return
        viewModelScope.launch {
            goalRepository.addGoal(
                Goal(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    icon = "track_changes",
                    targetAmount = targetAmount,
                    savedAmount = 0L,
                    targetDate = targetDate,
                    isCompleted = false
                )
            )
        }
    }

    fun contribute(goal: Goal, amount: Long) {
        if (amount <= 0L) return
        viewModelScope.launch {
            val newSaved = goal.savedAmount + amount
            goalRepository.updateGoal(
                goal.copy(
                    savedAmount = newSaved,
                    isCompleted = newSaved >= goal.targetAmount
                )
            )
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { goalRepository.deleteGoal(id) }
    }
}
