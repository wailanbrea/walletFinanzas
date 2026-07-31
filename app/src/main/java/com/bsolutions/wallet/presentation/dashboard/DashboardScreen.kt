package com.bsolutions.wallet.presentation.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.core.common.MoneyParser
import com.bsolutions.wallet.core.designsystem.CurrencyDisplayTextStyle
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.presentation.common.DonutChart
import com.bsolutions.wallet.presentation.common.DonutSegment
import androidx.compose.ui.platform.LocalContext
import com.bsolutions.wallet.presentation.common.authenticateBiometric
import com.bsolutions.wallet.presentation.common.parseHexColor
import com.bsolutions.wallet.presentation.common.WaterSurface
import com.bsolutions.wallet.presentation.common.privacyBlur
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.LinearProgressIndicator
import com.bsolutions.wallet.presentation.budgets.BudgetsViewModel
import com.bsolutions.wallet.presentation.budgets.BudgetCategoryItem
import com.bsolutions.wallet.presentation.budgets.AddBudgetDialog
import com.bsolutions.wallet.presentation.budgets.EditBudgetDialog
import com.bsolutions.wallet.presentation.goals.GoalsViewModel
import com.bsolutions.wallet.presentation.goals.GoalCard
import com.bsolutions.wallet.presentation.goals.CreateGoalSheet
import com.bsolutions.wallet.presentation.goals.ContributeSheet
import com.bsolutions.wallet.domain.model.Goal
import com.bsolutions.wallet.presentation.common.animatedProgress
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    budgetsViewModel: BudgetsViewModel = hiltViewModel(),
    goalsViewModel: GoalsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val budgetsUiState by budgetsViewModel.uiState.collectAsState()
    val goalsUiState by goalsViewModel.uiState.collectAsState()

    val context = LocalContext.current
    val privacyAuthTitle = stringResource(R.string.privacy_auth_title)
    val privacyAuthSubtitle = stringResource(R.string.privacy_auth_subtitle)

    var selectedTab by remember { mutableIntStateOf(0) }
    var showQuickActionSheet by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showCardSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            viewModel.refreshTime()
        }
    }

    // Budgets states
    var showAddBudgetDialog by remember { mutableStateOf(false) }
    var selectedBudget by remember { mutableStateOf<BudgetCategoryItem?>(null) }

    // Goals states
    var showCreateGoalSheet by remember { mutableStateOf(false) }
    var contributeGoal by remember { mutableStateOf<Goal?>(null) }

    // Dialog overlays
    if (showFilterDialog) {
        DashboardFilterDialog(
            selectedPeriod = uiState.selectedPeriod,
            selectedCategoryId = uiState.selectedCategoryId,
            categories = uiState.categories.values.sortedBy { it.name },
            onDismiss = { showFilterDialog = false },
            onApply = { period, categoryId ->
                viewModel.setPeriodFilter(period)
                viewModel.setCategoryFilter(categoryId)
                showFilterDialog = false
            },
            onClear = {
                viewModel.setPeriodFilter(DashboardPeriodFilter.THIS_MONTH)
                viewModel.setCategoryFilter(null)
                showFilterDialog = false
            }
        )
    }

    if (showCardSelector) {
        DashboardCardSelectorDialog(
            uiState = uiState,
            onDismiss = { showCardSelector = false },
            onSetCardEnabled = viewModel::setDashboardCardEnabled,
            onNavigateToTransactions = {
                showCardSelector = false
                onNavigateToTransactions()
            }
        )
    }

    if (showQuickActionSheet) {
        QuickActionBottomSheet(
            accounts = uiState.accounts,
            categories = uiState.categories,
            onDismiss = { showQuickActionSheet = false },
            onSaveExpense = { accId, amount, catId, note ->
                viewModel.addTransaction(accId, amount, "EXPENSE", catId, note)
                showQuickActionSheet = false
            },
            onSaveIncome = { accId, amount, catId, note ->
                viewModel.addTransaction(accId, amount, "INCOME", catId, note)
                showQuickActionSheet = false
            },
            onSaveTransfer = { fromId, toId, amount, note ->
                viewModel.transfer(fromId, toId, amount, note)
                showQuickActionSheet = false
            }
        )
    }

    if (showAddBudgetDialog) {
        AddBudgetDialog(
            categories = budgetsUiState.availableCategories,
            onDismiss = { showAddBudgetDialog = false },
            onConfirm = { catId, limit ->
                budgetsViewModel.addBudget(catId, limit)
                showAddBudgetDialog = false
            }
        )
    }

    selectedBudget?.let { item ->
        EditBudgetDialog(
            item = item,
            onDismiss = { selectedBudget = null },
            onSave = { newLimit ->
                budgetsViewModel.updateBudgetLimit(item.budgetId, item.category.id, newLimit)
                selectedBudget = null
            },
            onDelete = {
                budgetsViewModel.deleteBudget(item.budgetId)
                selectedBudget = null
            }
        )
    }

    if (showCreateGoalSheet) {
        CreateGoalSheet(
            onDismiss = { showCreateGoalSheet = false },
            onSave = { name, target ->
                goalsViewModel.addGoal(name, target, null)
                showCreateGoalSheet = false
            }
        )
    }

    contributeGoal?.let { goal ->
        ContributeSheet(
            goal = goal,
            onDismiss = { contributeGoal = null },
            onSave = { amount ->
                goalsViewModel.contribute(goal, amount)
                contributeGoal = null
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.nav_home),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.common_open_menu)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            context.authenticateBiometric(privacyAuthTitle, privacyAuthSubtitle) {
                                viewModel.toggleBalancesHidden()
                            }
                        }) {
                            Icon(
                                imageVector = if (uiState.balancesHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (uiState.balancesHidden) R.string.privacy_show_amounts else R.string.privacy_hide_amounts
                                )
                            )
                        }
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.common_notifications)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.dashboard_tab_accounts), fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.dashboard_tab_budgets_goals), fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showQuickActionSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dashboard_add_transaction))
                }
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (DashboardCardType.TOTAL_BALANCE in uiState.selectedCards) item {
                    Spacer(modifier = Modifier.height(8.dp))
                    TotalBalanceCard(
                        totalBalance = uiState.totalBalance,
                        foreignSubtitle = uiState.foreignBalancesSubtitle,
                        expenseTrendPercent = uiState.expenseTrendPercent,
                        hidden = uiState.balancesHidden,
                        onFilterClick = { showFilterDialog = true }
                    )
                }

                if (DashboardCardType.CASH_FLOW in uiState.selectedCards) item {
                    CashFlowCard(uiState)
                }

                if (DashboardCardType.EXPENSE_STRUCTURE in uiState.selectedCards) item {
                    ExpenseStructureCard(uiState)
                }

                if (DashboardCardType.RECENT_TRANSACTIONS in uiState.selectedCards) item {
                    RecentTransactionsCard(uiState, onNavigateToTransactions)
                }

                if (DashboardCardType.ACCOUNT_BALANCES in uiState.selectedCards) item {
                    AccountBalancesCard(uiState)
                }

                item {
                    OutlinedButton(
                        onClick = { showCardSelector = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dashboard_card_add_more))
                    }
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- SECTION 1: PRESUPUESTOS ---
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_active_budgets),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showAddBudgetDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.budgets_new),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (budgetsUiState.budgetItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_no_active_budgets),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showAddBudgetDialog = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.dashboard_create_budget))
                                }
                            }
                        }
                    }
                } else {
                    items(budgetsUiState.budgetItems) { item ->
                        val ratio = if (item.limitAmount > 0) item.spentAmount.toFloat() / item.limitAmount.toFloat() else 0f
                        val percentage = (ratio * 100).toInt()

                        val progressColor = when {
                            percentage <= 70 -> MaterialTheme.colorScheme.secondary
                            percentage <= 95 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBudget = item },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val iconFallback = Icons.Default.Wallet
                                        val icon = getIconForName(item.category.icon)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    parseHexColor(item.category.colorHex, MaterialTheme.colorScheme.primary)
                                                        .copy(alpha = 0.1f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon ?: iconFallback,
                                                contentDescription = null,
                                                tint = parseHexColor(item.category.colorHex, MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                        Text(
                                            text = item.category.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "${MoneyFormat.format(item.spentAmount)} / ${MoneyFormat.format(item.limitAmount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                val animatedProg = animatedProgress(ratio)
                                LinearProgressIndicator(
                                    progress = { animatedProg },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_budget_consumed, percentage),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (percentage > 95) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // --- SECTION 2: METAS Y OBJETIVOS ---
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_goals_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showCreateGoalSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.goals_new),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (goalsUiState.goals.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_no_active_goals),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showCreateGoalSheet = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.dashboard_create_goal))
                                }
                            }
                        }
                    }
                } else {
                    items(goalsUiState.goals, key = { it.id }) { goal ->
                        GoalCard(
                            goal = goal,
                            onContribute = { contributeGoal = goal },
                            onDelete = { goalsViewModel.deleteGoal(goal.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardFilterDialog(
    selectedPeriod: DashboardPeriodFilter,
    selectedCategoryId: String?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onApply: (DashboardPeriodFilter, String?) -> Unit,
    onClear: () -> Unit
) {
    var period by remember(selectedPeriod) { mutableStateOf(selectedPeriod) }
    var categoryId by remember(selectedCategoryId) { mutableStateOf(selectedCategoryId) }
    var periodExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.dashboard_filter_period), style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(
                            onClick = { periodExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(dashboardPeriodLabel(period), modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = periodExpanded,
                            onDismissRequest = { periodExpanded = false }
                        ) {
                            DashboardPeriodFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(dashboardPeriodLabel(option)) },
                                    onClick = {
                                        period = option
                                        periodExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.dashboard_filter_category), style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(
                            onClick = { categoryExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                categories.firstOrNull { it.id == categoryId }?.name
                                    ?: stringResource(R.string.dashboard_filter_all_categories),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dashboard_filter_all_categories)) },
                                onClick = {
                                    categoryId = null
                                    categoryExpanded = false
                                }
                            )
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        categoryId = category.id
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(period, categoryId) }) {
                Text(stringResource(R.string.dashboard_filter_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.dashboard_filter_clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dashboard_filter_cancel))
                }
            }
        }
    )
}

@Composable
private fun dashboardPeriodLabel(period: DashboardPeriodFilter): String = stringResource(
    when (period) {
        DashboardPeriodFilter.TODAY -> R.string.dashboard_filter_today
        DashboardPeriodFilter.THIS_WEEK -> R.string.dashboard_filter_this_week
        DashboardPeriodFilter.THIS_MONTH -> R.string.dashboard_filter_this_month
        DashboardPeriodFilter.THIS_YEAR -> R.string.dashboard_filter_this_year
        DashboardPeriodFilter.LAST_7_DAYS -> R.string.dashboard_filter_last_7_days
        DashboardPeriodFilter.LAST_30_DAYS -> R.string.dashboard_filter_last_30_days
        DashboardPeriodFilter.LAST_12_WEEKS -> R.string.dashboard_filter_last_12_weeks
        DashboardPeriodFilter.LAST_6_MONTHS -> R.string.dashboard_filter_last_6_months
        DashboardPeriodFilter.LAST_1_YEAR -> R.string.dashboard_filter_last_1_year
        DashboardPeriodFilter.LAST_5_YEARS -> R.string.dashboard_filter_last_5_years
    }
)

/**
 * Tarjeta de Balance Total con degradado de marca, círculos decorativos
 * y monto animado (cuenta suavemente hacia el valor al entrar y al cambiar).
 */
@Composable
fun TotalBalanceCard(
    totalBalance: Long,
    foreignSubtitle: String? = null,
    expenseTrendPercent: Int?,
    hidden: Boolean = false,
    /** Cuánto llena el agua: el avance de la meta elegida, o la mitad si no hay ninguna. */
    waterLevel: Float = 0.5f,
    onFilterClick: (() -> Unit)? = null
) {
    // Estado para forzar la animación en cada entrada a la composición
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    val appearScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.96f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "balanceScale"
    )
    val appearAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "balanceAlpha"
    )

    // Contador animado del monto (se fuerza a contar desde 0 cada vez)
    val animatedBalance by animateFloatAsState(
        targetValue = if (startAnimation) totalBalance.toFloat() else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "balanceValue"
    )

    // Degradado verde de marca (mismo del header del drawer)
    val gradientStart = Color(0xFF8BD08D)
    val gradientEnd = Color(0xFF3FA45B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = appearScale
                scaleY = appearScale
                alpha = appearAlpha
            }
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = gradientEnd)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors = listOf(gradientStart, gradientEnd)))
    ) {
        // El agua va detrás de todo y recortada por la tarjeta, para que se vea contenida
        // dentro del vaso y no flotando encima del contenido.
        WaterSurface(
            level = waterLevel,
            color = Color.White,
            modifier = Modifier.matchParentSize(),
            // El fondo se pasa por fórmula y no se captura: es el mismo degradado que
            // pinta la tarjeta, así que el shader puede recalcularlo y desviarlo.
            backgroundTop = gradientStart,
            backgroundBottom = gradientEnd
        )
        // Los circulos decorativos se retiraron: dentro del vaso ahora hay piezas de
        // ajedrez, y un fondo con burbujas fijas competia con ellas.

        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.dashboard_total_balance),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = MoneyFormat.format(animatedBalance.toLong()),
                modifier = Modifier.privacyBlur(hidden, radius = 16.dp),
                style = CurrencyDisplayTextStyle,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            // Subtotales en otras divisas (cuentas bancarias importadas en €, US$, etc.)
            foreignSubtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.dashboard_other_currencies, subtitle),
                    modifier = Modifier.privacyBlur(hidden, radius = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            // Tendencia real de gasto vs mes anterior (solo si hay base de comparación)
            expenseTrendPercent?.let { trend ->
                Spacer(modifier = Modifier.height(10.dp))
                val trendUp = trend > 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (trendUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            // Gastar más que el mes pasado es negativo; menos, positivo
                            tint = if (trendUp) Color(0xFFFFCDD2) else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.dashboard_trend_vs_last_month, if (trendUp) "+" else "", trend),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (trendUp) Color(0xFFFFCDD2) else Color.White
                        )
                    }
                }
            }
        }
        onFilterClick?.let { onClick ->
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.dashboard_filter_action),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CategoryLegendItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TransactionItem(
    title: String,
    subtitle: String,
    amount: Long,
    type: String,
    icon: ImageVector,
    currency: String = MoneyFormat.DEFAULT_CURRENCY,
    categoryName: String? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Categoría del movimiento, pequeña bajo la fecha
                if (!categoryName.isNullOrBlank()) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        val textColor = if (type == "INCOME") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        Text(
            text = MoneyFormat.formatSigned(amount, isIncome = type == "INCOME", currency = currency),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Iconos disponibles para categorías (nombre persistido -> vector). */
val categoryIcons: Map<String, ImageVector> = mapOf(
    "home" to Icons.Default.Home,
    "restaurant" to Icons.Default.Restaurant,
    "fastfood" to Icons.Default.Fastfood,
    "restaurant_menu" to Icons.Default.RestaurantMenu,
    "local_cafe" to Icons.Default.LocalCafe,
    "local_gas_station" to Icons.Default.LocalGasStation,
    "directions_car" to Icons.Default.DirectionsCar,
    "shopping_cart" to Icons.Default.ShoppingCart,
    "payments" to Icons.Default.Payments,
    "local_hospital" to Icons.Default.LocalHospital,
    "school" to Icons.Default.School,
    "movie" to Icons.Default.Movie,
    "flight" to Icons.Default.Flight,
    "fitness_center" to Icons.Default.FitnessCenter,
    "pets" to Icons.Default.Pets,
    "card_giftcard" to Icons.Default.CardGiftcard
)

fun getIconForName(name: String): ImageVector =
    categoryIcons[name] ?: Icons.Default.ShoppingCart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionBottomSheet(
    accounts: List<Account>,
    categories: Map<String, Category>,
    onDismiss: () -> Unit,
    onSaveExpense: (accountId: String, amount: Long, categoryId: String, note: String) -> Unit,
    onSaveIncome: (accountId: String, amount: Long, categoryId: String, note: String) -> Unit,
    onSaveTransfer: (fromAccountId: String, toAccountId: String, amount: Long, note: String) -> Unit
) {
    var selectedAction by remember { mutableStateOf<String?>(null) }

    if (selectedAction == null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            dragHandle = null
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.quick_new_transaction),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                QuickActionItem(
                    icon = Icons.Default.ArrowUpward,
                    label = stringResource(R.string.quick_expense),
                    description = stringResource(R.string.quick_expense_desc),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { selectedAction = "EXPENSE" }
                )
                Spacer(modifier = Modifier.height(12.dp))

                QuickActionItem(
                    icon = Icons.Default.ArrowDownward,
                    label = stringResource(R.string.quick_income),
                    description = stringResource(R.string.quick_income_desc),
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { selectedAction = "INCOME" }
                )
                Spacer(modifier = Modifier.height(12.dp))

                QuickActionItem(
                    icon = Icons.Default.SwapHoriz,
                    label = stringResource(R.string.quick_transfer),
                    description = stringResource(R.string.quick_transfer_desc),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { selectedAction = "TRANSFER" }
                )
            }
        }
    } else if (selectedAction == "TRANSFER") {
        TransferFormSheet(
            accounts = accounts,
            onDismiss = { selectedAction = null },
            onSave = onSaveTransfer
        )
    } else {
        QuickActionFormSheet(
            actionType = selectedAction,
            accounts = accounts,
            categories = categories.values.toList(),
            onDismiss = { selectedAction = null },
            onSave = { accId, amount, catId, note ->
                if (selectedAction == "EXPENSE") {
                    onSaveExpense(accId, amount, catId, note)
                } else {
                    onSaveIncome(accId, amount, catId, note)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionItem(
    icon: ImageVector,
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionFormSheet(
    actionType: String?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (accountId: String, amount: Long, categoryId: String, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    // Vacío = "Sin categoría (automática)": si el usuario no elige, se infiere de la nota.
    var selectedCategoryId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val typeLabel = stringResource(if (actionType == "EXPENSE") R.string.quick_expense else R.string.quick_income)
    val typeColor = if (actionType == "EXPENSE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    ModalBottomSheet(
        onDismissRequest = {
            amountStr = ""
            note = ""
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_new_type, typeLabel),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = typeColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = MoneyFormat.symbol(),
                    style = MaterialTheme.typography.headlineMedium.copy(color = typeColor),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("0.00") },
                    textStyle = CurrencyDisplayTextStyle.copy(
                        color = typeColor,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = typeColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(stringResource(R.string.common_account), style = MaterialTheme.typography.labelLarge)
            var expandedAcc by remember { mutableStateOf(false) }
            val selectedAccount = accounts.find { it.id == selectedAccountId }
            Box {
                OutlinedButton(
                    onClick = { expandedAcc = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedAccount?.name ?: stringResource(R.string.common_select_account))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = expandedAcc,
                    onDismissRequest = { expandedAcc = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.name) },
                            onClick = {
                                selectedAccountId = acc.id
                                expandedAcc = false
                            }
                        )
                    }
                }
            }

            // Selector de categoría SIEMPRE visible (antes se ocultaba si la lista estaba
            // vacía). "Sin categoría" deja que se infiera automáticamente de la nota.
            Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelLarge)
            var expandedCat by remember { mutableStateOf(false) }
            val selectedCat = categories.find { it.id == selectedCategoryId }
            Box {
                OutlinedButton(
                    onClick = { expandedCat = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            selectedCat?.let {
                                Icon(getIconForName(it.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(selectedCat?.name ?: stringResource(R.string.category_none_auto))
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = expandedCat,
                    onDismissRequest = { expandedCat = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.category_none_auto)) },
                        onClick = { selectedCategoryId = ""; expandedCat = false }
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            leadingIcon = { Icon(getIconForName(cat.icon), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { selectedCategoryId = cat.id; expandedCat = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.common_note_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
                    if (amount > 0L && selectedAccountId.isNotEmpty()) {
                        // Con categoría vacía, el ViewModel infiere por palabras clave
                        // (reglas del usuario primero, luego las integradas).
                        onSave(selectedAccountId, amount, selectedCategoryId, note)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = typeColor),
                enabled = amountStr.isNotEmpty() && selectedAccountId.isNotEmpty()
            ) {
                Text(stringResource(R.string.quick_save_type, typeLabel), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFormSheet(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (fromAccountId: String, toAccountId: String, amount: Long, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var fromAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var toAccountId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var note by remember { mutableStateOf("") }

    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
    val fromAccount = accounts.find { it.id == fromAccountId }
    val valid = amount > 0L && fromAccountId.isNotEmpty() && toAccountId.isNotEmpty() &&
        fromAccountId != toAccountId &&
        (fromAccount?.balance ?: 0L) >= amount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.transfer_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = MoneyFormat.symbol(),
                    style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("0.00") },
                    textStyle = CurrencyDisplayTextStyle.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AccountDropdown(
                label = stringResource(R.string.transfer_from),
                accounts = accounts,
                selectedId = fromAccountId,
                onSelect = { fromAccountId = it }
            )
            AccountDropdown(
                label = stringResource(R.string.transfer_to),
                accounts = accounts.filter { it.id != fromAccountId },
                selectedId = toAccountId,
                onSelect = { toAccountId = it }
            )

            if (fromAccount != null && amount > 0L && fromAccount.balance < amount) {
                Text(
                    text = stringResource(R.string.transfer_insufficient, fromAccount.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.common_note_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    if (valid) {
                        onSave(fromAccountId, toAccountId, amount, note)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                enabled = valid
            ) {
                Text(stringResource(R.string.transfer_action), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId }

    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selected?.let { acc ->
                        "${acc.name} · ${MoneyFormat.format(acc.balance, acc.currency)}"
                    } ?: stringResource(R.string.common_select_account)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text(acc.name) },
                    onClick = {
                        onSelect(acc.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
