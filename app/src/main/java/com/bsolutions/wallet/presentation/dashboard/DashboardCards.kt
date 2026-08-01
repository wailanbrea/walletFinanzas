package com.bsolutions.wallet.presentation.dashboard

import androidx.compose.foundation.BorderStroke

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.presentation.accounts.balanceBarFraction
import com.bsolutions.wallet.presentation.accounts.creditCardDebt
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
            hidden = uiState.balancesHidden,
            waterLevel = uiState.waterLevel
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
        // El neto no se desenfoca: el modo privacidad solo cubre Balance Total e Ingresos.
        Text(
            text = MoneyFormat.format(uiState.monthlyIncome - uiState.monthlyExpenses),
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
            hidden = false
        )
        if (uiState.spendingPacePercent != null) {
            Spacer(Modifier.height(10.dp))
            val pace = uiState.spendingPacePercent
            val isUnderPace = pace <= 0
            Surface(
                color = if (isUnderPace) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isUnderPace) "Vas ${kotlin.math.abs(pace)}% por debajo del ritmo estimado de gasto 👏" else "Ritmo de gasto: +$pace% sobre lo proyectado ⚠️",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUnderPace) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        // Un prestamo no es gasto, pero el dinero si salio de la cuenta. Sin esta linea
        // el flujo no cuadra con el balance y parece que falta plata sin explicacion.
        if (uiState.monthlyLent > 0L || uiState.monthlyCollected > 0L) {
            Spacer(Modifier.height(10.dp))
            AmountProgressRow(
                label = stringResource(R.string.dashboard_lent),
                amount = uiState.monthlyLent,
                progress = uiState.monthlyLent / maximum,
                color = MaterialTheme.colorScheme.tertiary,
                hidden = false
            )
            if (uiState.monthlyCollected > 0L) {
                Spacer(Modifier.height(10.dp))
                AmountProgressRow(
                    label = stringResource(R.string.dashboard_collected),
                    amount = uiState.monthlyCollected,
                    progress = uiState.monthlyCollected / maximum,
                    color = MaterialTheme.colorScheme.tertiary,
                    hidden = false
                )
            }
        }
        if (uiState.outstandingReceivable > 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                // Prestado y Cobrado son del periodo elegido; esta linea es el saldo de
                // todas las deudas abiertas. Al estar una debajo de otra se leia como si
                // fuera la resta de las dos, y no cuadraba nunca: una deuda apuntada a
                // mano no pasa por Prestado, y una ya saldada sigue en Cobrado pero ya no
                // suma aqui. Decir cuantas deudas son y que es todo el historial es lo
                // que separa las dos cuentas.
                text = pluralStringResource(
                    R.plurals.dashboard_outstanding_receivable,
                    uiState.openReceivableCount,
                    MoneyFormat.format(uiState.outstandingReceivable),
                    uiState.openReceivableCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    Spacer(Modifier.height(6.dp))
    // Crece al aparecer y al cambiar el periodo: el movimiento deja ver cuánto se movió
    // la barra, que en un número seco se pierde.
    val target = progress.coerceIn(0f, 1f)
    val grown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "barra"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            // El canal de fondo en el mismo color, muy tenue: se ve cuánto falta sin
            // meter un gris que pelee con el tema.
            .background(color.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(grown)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.55f), color)
                    )
                )
        )
    }
}

@Composable
internal fun ExpenseStructureCard(uiState: DashboardUiState) {
    var showBreakdown by remember { mutableStateOf(false) }

    if (showBreakdown) {
        ExpenseBreakdownSheet(uiState = uiState, onDismiss = { showBreakdown = false })
    }

    DashboardInfoCard(
        title = stringResource(R.string.dashboard_card_expense_structure),
        subtitle = stringResource(R.string.dashboard_card_expense_question),
        // La leyenda solo cabe para tres categorias, pero el grafico las pinta todas: sin
        // esto, las porciones de las demas no tenian nombre en ningun lado.
        onClick = { showBreakdown = true }.takeIf { uiState.categorySpending.isNotEmpty() }
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    MoneyFormat.format(uiState.monthlyExpenses),
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
                    uiState.categorySpending.take(LEGEND_CATEGORIES).forEach { spend ->
                        CategoryLegendItem(
                            text = "${spend.category.name} (${spend.percentage}%)",
                            color = parseHexColor(spend.category.colorHex, fallback)
                        )
                    }
                    val hidden = uiState.categorySpending.size - LEGEND_CATEGORIES
                    if (hidden > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_expense_more_categories,
                                hidden,
                                hidden
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
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
            // Dos escalas y no una. Antes las barras se median todas contra el saldo mas
            // grande, y como las tarjetas guardan la deuda en negativo salian siempre
            // vacias: la tarjeta parecia rota justo cuando estaba al dia.
            val largestBalance = uiState.accounts
                .filter { it.type != "CREDIT_CARD" }
                .maxOfOrNull { it.balance.coerceAtLeast(0L) } ?: 0L
            val largestCardDebt = uiState.accounts
                .filter { it.type == "CREDIT_CARD" && (it.creditLimit ?: 0L) <= 0L }
                .maxOfOrNull { creditCardDebt(it.balance) } ?: 0L
            uiState.accounts.take(5).forEachIndexed { index, account ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(account.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        MoneyFormat.format(account.balance, account.currency),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Mismo tratamiento que el flujo de caja: degradado, esquinas redondeadas
                // y crecimiento animado, para que las dos tarjetas se lean como una familia.
                val visible = balanceBarFraction(
                    type = account.type,
                    balance = account.balance,
                    creditLimit = account.creditLimit,
                    largestBalance = largestBalance,
                    largestCardDebt = largestCardDebt
                )
                val grown by animateFloatAsState(
                    targetValue = visible,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "barraCuenta"
                )
                val accent = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        // Canal mas marcado que en flujo de caja: aqui las barras son
                        // cortas y sin el el hueco no se lee.
                        .background(accent.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(grown)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.55f), accent)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardInfoCard(
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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

/** Cuantas categorias caben en la leyenda de la tarjeta sin aplastar el grafico. */
private const val LEGEND_CATEGORIES = 3

/**
 * Desglose completo del gasto por categoria.
 *
 * En la tarjeta solo caben tres nombres, pero el grafico dibuja todas las porciones: el
 * resto quedaba pintado y sin nombre, y no habia forma de saber a que correspondia cada
 * trozo ni cuanto valia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseBreakdownSheet(
    uiState: DashboardUiState,
    onDismiss: () -> Unit
) {
    val fallback = MaterialTheme.colorScheme.primary
    // Cada barra se mide contra la categoria mas gastadora y no contra el total: si una
    // sola se lleva el 70%, medir contra el total deja a las demas en una raya invisible.
    val largest = uiState.categorySpending.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_card_expense_structure),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.dashboard_expense_breakdown_total,
                            MoneyFormat.format(uiState.monthlyExpenses)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    DonutChart(
                        segments = uiState.categorySpending.map { spend ->
                            DonutSegment(spend.amount, parseHexColor(spend.category.colorHex, fallback))
                        },
                        size = 160.dp,
                        strokeWidth = 22.dp
                    )
                }
            }
            items(uiState.categorySpending, key = { it.category.id }) { spend ->
                val color = parseHexColor(spend.category.colorHex, fallback)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = spend.category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyFormat.format(spend.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            // El porcentaje redondea a entero, asi que una categoria
                            // pequeña sale como 0%. El importe de arriba es el que manda.
                            Text(
                                text = "${spend.percentage}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((spend.amount.toFloat() / largest).coerceIn(0.04f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}
