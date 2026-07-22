package com.bsolutions.wallet.presentation.dashboard

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.presentation.common.DonutChart
import com.bsolutions.wallet.presentation.common.DonutSegment
import com.bsolutions.wallet.presentation.common.parseHexColor
import com.bsolutions.wallet.presentation.common.privacyBlur
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardCardSelectorDialog(
    uiState: DashboardUiState,
    onDismiss: () -> Unit,
    onSetCardEnabled: (DashboardCardType, Boolean) -> Unit,
    onNavigateToTransactions: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val cards = when (selectedTab) {
        1 -> listOf(DashboardCardType.TOTAL_BALANCE, DashboardCardType.ACCOUNT_BALANCES)
        2 -> listOf(DashboardCardType.CASH_FLOW)
        3 -> listOf(DashboardCardType.EXPENSE_STRUCTURE, DashboardCardType.RECENT_TRANSACTIONS)
        else -> DashboardCardType.entries
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.dashboard_card_selector_title)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    TabRow(selectedTabIndex = selectedTab) {
                        listOf(
                            R.string.dashboard_card_tab_all,
                            R.string.dashboard_card_tab_balance,
                            R.string.dashboard_card_tab_cash_flow,
                            R.string.dashboard_card_tab_expenses
                        ).forEachIndexed { index, label ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(stringResource(label), maxLines = 1) }
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(Modifier.height(1.dp)) }
                        items(cards, key = { it.storageId }) { card ->
                            DashboardCardSelectorItem(
                                card = card,
                                uiState = uiState,
                                isSelected = card in uiState.selectedCards,
                                onSetEnabled = { enabled -> onSetCardEnabled(card, enabled) },
                                onNavigateToTransactions = onNavigateToTransactions
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCardSelectorItem(
    card: DashboardCardType,
    uiState: DashboardUiState,
    isSelected: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onNavigateToTransactions: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardCardContent(card, uiState, onNavigateToTransactions)
        Button(
            onClick = { onSetEnabled(!isSelected) },
            modifier = Modifier.fillMaxWidth(),
            enabled = card != DashboardCardType.TOTAL_BALANCE
        ) {
            Text(
                stringResource(
                    if (card == DashboardCardType.TOTAL_BALANCE) R.string.dashboard_card_always_on_panel
                    else if (isSelected) R.string.dashboard_card_remove_from_panel
                    else R.string.dashboard_card_add_to_panel
                )
            )
        }
    }
}

@Composable
internal fun DashboardCardContent(
    card: DashboardCardType,
    uiState: DashboardUiState,
    onNavigateToTransactions: () -> Unit
) {
    when (card) {
        DashboardCardType.TOTAL_BALANCE -> TotalBalanceCard(
            totalBalance = uiState.totalBalance,
            foreignSubtitle = uiState.foreignBalancesSubtitle,
            expenseTrendPercent = uiState.expenseTrendPercent,
            hidden = uiState.balancesHidden
        )
        DashboardCardType.CASH_FLOW -> CashFlowCard(uiState)
        DashboardCardType.EXPENSE_STRUCTURE -> ExpenseStructureCard(uiState)
        DashboardCardType.RECENT_TRANSACTIONS -> RecentTransactionsCard(uiState, onNavigateToTransactions)
        DashboardCardType.ACCOUNT_BALANCES -> AccountBalancesCard(uiState)
    }
}

@Composable
internal fun CashFlowCard(uiState: DashboardUiState) {
    val maximum = maxOf(uiState.monthlyIncome, uiState.monthlyExpenses, 1L).toFloat()
    DashboardInfoCard(
        title = stringResource(R.string.dashboard_card_cash_flow),
        subtitle = stringResource(R.string.dashboard_card_cash_flow_question)
    ) {
        Text(
            text = MoneyFormat.format(uiState.monthlyIncome - uiState.monthlyExpenses),
            modifier = Modifier.privacyBlur(uiState.balancesHidden, radius = 10.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        AmountProgressRow(
            label = stringResource(R.string.dashboard_income),
            amount = uiState.monthlyIncome,
            progress = uiState.monthlyIncome / maximum,
            color = MaterialTheme.colorScheme.secondary,
            hidden = uiState.balancesHidden
        )
        Spacer(Modifier.height(10.dp))
        AmountProgressRow(
            label = stringResource(R.string.dashboard_expenses),
            amount = uiState.monthlyExpenses,
            progress = uiState.monthlyExpenses / maximum,
            color = MaterialTheme.colorScheme.error,
            hidden = uiState.balancesHidden
        )
    }
}

@Composable
private fun AmountProgressRow(
    label: String,
    amount: Long,
    progress: Float,
    color: Color,
    hidden: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            MoneyFormat.format(amount),
            modifier = Modifier.privacyBlur(hidden, radius = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        color = color
    )
}

@Composable
internal fun ExpenseStructureCard(uiState: DashboardUiState) {
    DashboardInfoCard(
        title = stringResource(R.string.dashboard_card_expense_structure),
        subtitle = stringResource(R.string.dashboard_card_expense_question)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    MoneyFormat.format(uiState.monthlyExpenses),
                    modifier = Modifier.privacyBlur(uiState.balancesHidden, radius = 10.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                if (uiState.categorySpending.isEmpty()) {
                    Text(
                        stringResource(R.string.dashboard_no_expenses_filter),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val fallback = MaterialTheme.colorScheme.primary
                    uiState.categorySpending.take(3).forEach { spend ->
                        CategoryLegendItem(
                            text = "${spend.category.name} (${spend.percentage}%)",
                            color = parseHexColor(spend.category.colorHex, fallback)
                        )
                    }
                }
            }
            val fallback = MaterialTheme.colorScheme.primary
            DonutChart(
                segments = uiState.categorySpending.map { spend ->
                    DonutSegment(spend.amount, parseHexColor(spend.category.colorHex, fallback))
                },
                size = 104.dp,
                strokeWidth = 15.dp
            )
        }
    }
}

@Composable
internal fun RecentTransactionsCard(
    uiState: DashboardUiState,
    onNavigateToTransactions: () -> Unit
) {
    DashboardInfoCard(
        title = stringResource(R.string.dashboard_recent_transactions),
        subtitle = stringResource(R.string.dashboard_card_recent_question),
        action = {
            TextButton(onClick = onNavigateToTransactions) {
                Text(stringResource(R.string.dashboard_see_all))
            }
        }
    ) {
        if (uiState.recentTransactions.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_card_no_recent),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            uiState.recentTransactions.take(5).forEachIndexed { index, transaction ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                TransactionPreview(transaction, uiState)
            }
        }
    }
}

@Composable
private fun TransactionPreview(transaction: Transaction, uiState: DashboardUiState) {
    val category = uiState.categories[transaction.categoryId]
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                transaction.note.ifEmpty { category?.name ?: stringResource(R.string.dashboard_other_category) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                SimpleDateFormat("dd MMM, hh:mm a", LocalConfiguration.current.locales[0]).format(Date(transaction.date)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Categoría del movimiento, pequeña bajo la fecha
            category?.let {
                Text(
                    it.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            MoneyFormat.formatSigned(transaction.amount, transaction.type == "INCOME", transaction.currency),
            modifier = Modifier.privacyBlur(uiState.balancesHidden, radius = 8.dp),
            color = if (transaction.type == "INCOME") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun AccountBalancesCard(uiState: DashboardUiState) {
    DashboardInfoCard(
        title = stringResource(R.string.dashboard_card_account_balances),
        subtitle = stringResource(R.string.dashboard_card_accounts_question)
    ) {
        if (uiState.accounts.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_card_no_accounts),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maximum = uiState.accounts.maxOf { it.balance.coerceAtLeast(0L) }.coerceAtLeast(1L).toFloat()
            uiState.accounts.take(5).forEachIndexed { index, account ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(account.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        MoneyFormat.format(account.balance, account.currency),
                        modifier = Modifier.privacyBlur(uiState.balancesHidden, radius = 8.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (account.balance.coerceAtLeast(0L) / maximum).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardInfoCard(
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                action?.invoke()
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}
