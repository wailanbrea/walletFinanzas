package com.bsolutions.wallet.presentation.plannedpayments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bsolutions.wallet.R
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.core.common.MoneyParser
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.PlannedPayment
import java.text.SimpleDateFormat
import java.util.Date

private fun formatMoney(minorUnits: Long): String = MoneyFormat.format(minorUnits)

private val frequencyLabelRes = mapOf(
    "WEEKLY" to R.string.freq_weekly,
    "BIWEEKLY" to R.string.freq_biweekly,
    "SEMIMONTHLY" to R.string.email_candidate_freq_semimonthly,
    "EVERY_15_DAYS" to R.string.email_candidate_freq_every15,
    "EVERY_30_DAYS" to R.string.email_candidate_freq_every30,
    "MONTHLY" to R.string.freq_monthly,
    "YEARLY" to R.string.freq_yearly,
    "ONCE" to R.string.freq_once
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedPaymentsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: PlannedPaymentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var payingPayment by remember { mutableStateOf<PlannedPayment?>(null) }

    if (showCreateSheet) {
        CreatePlannedPaymentSheet(
            accounts = uiState.accounts,
            categories = uiState.categories,
            onDismiss = { showCreateSheet = false },
            onSave = { name, accountId, categoryId, amount, frequency, type ->
                viewModel.addPayment(name, accountId, categoryId, amount, frequency, System.currentTimeMillis(), type)
                showCreateSheet = false
            }
        )
    }

    // Al registrar se confirma el importe: una quincena puede venir distinta de lo
    // previsto y lo que debe quedar guardado es lo que realmente entro.
    payingPayment?.let { payment ->
        ConfirmAmountDialog(
            payment = payment,
            onDismiss = { payingPayment = null },
            onConfirm = { amount ->
                viewModel.payNow(payment, amount)
                payingPayment = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.planned_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_open_menu))
                    }
                },
                colors = walletTopBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.planned_new))
            }
        }
    ) { innerPadding ->
        if (uiState.payments.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Update,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.planned_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.planned_empty_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (uiState.expensePayments.isNotEmpty()) {
                    item {
                        PlannedPaymentsSectionHeader(
                            title = stringResource(R.string.planned_expenses_title),
                            activeCount = uiState.expensePayments.count { it.isActive },
                            total = uiState.activeExpenseTotal,
                            isIncome = false
                        )
                    }
                    items(uiState.expensePayments, key = { "expense_${it.id}" }) { payment ->
                        PlannedPaymentCard(
                            payment = payment,
                            onPayNow = { payingPayment = payment },
                            onDelete = { viewModel.deletePayment(payment.id) }
                        )
                    }
                }
                if (uiState.incomePayments.isNotEmpty()) {
                    item {
                        PlannedPaymentsSectionHeader(
                            title = stringResource(R.string.planned_incomes_title),
                            activeCount = uiState.incomePayments.count { it.isActive },
                            total = uiState.activeIncomeTotal,
                            isIncome = true
                        )
                    }
                    items(uiState.incomePayments, key = { "income_${it.id}" }) { payment ->
                        PlannedPaymentCard(
                            payment = payment,
                            onPayNow = { payingPayment = payment },
                            onDelete = { viewModel.deletePayment(payment.id) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun PlannedPaymentsSectionHeader(
    title: String,
    activeCount: Int,
    total: Long,
    isIncome: Boolean
) {
    val containerColor = if (isIncome) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isIncome) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.planned_active_count, activeCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = formatMoney(total),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PlannedPaymentCard(
    payment: PlannedPayment,
    onPayNow: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", LocalConfiguration.current.locales[0]).format(Date(payment.nextDueDate))
    val overdue = payment.isActive && payment.nextDueDate < System.currentTimeMillis()
    val isIncome = payment.type == "INCOME"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (overdue) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Update,
                        contentDescription = null,
                        tint = if (overdue) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val freqLabel = frequencyLabelRes[payment.frequency]?.let { stringResource(it) } ?: payment.frequency
                    val statusLabel = if (payment.isActive) {
                        stringResource(if (overdue) R.string.planned_overdue else R.string.planned_next, dateStr)
                    } else stringResource(R.string.planned_inactive)
                    Text(
                        text = "$freqLabel · $statusLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            if (isIncome) R.string.planned_type_income else R.string.planned_type_expense
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isIncome) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = formatMoney(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            if (payment.isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.planned_delete),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = stringResource(
                            if (isIncome) R.string.planned_receive_now else R.string.planned_pay_now
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onPayNow)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlannedPaymentSheet(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, accountId: String, categoryId: String, amount: Long, frequency: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    // Un sueldo tambien es recurrente: sin esto solo se podian planificar gastos.
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var expandedAcc by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.planned_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.planned_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.planned_amount, MoneyFormat.symbol())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.planned_type), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == "EXPENSE",
                    onClick = { type = "EXPENSE" },
                    label = { Text(stringResource(R.string.planned_type_expense)) }
                )
                FilterChip(
                    selected = type == "INCOME",
                    onClick = { type = "INCOME" },
                    label = { Text(stringResource(R.string.planned_type_income)) }
                )
            }

            Text(stringResource(R.string.planned_frequency), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(frequencyLabelRes.entries.toList()) { (key, labelRes) ->
                    FilterChip(
                        selected = frequency == key,
                        onClick = { frequency = key },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            // Solo se ofrecen las categorias del tipo elegido: un sueldo no puede
            // etiquetarse "Transporte" ni un gasto "Salario".
            val categoriesForType = categories.filter { it.type == type || it.type == "BOTH" }
            // Si se cambia de tipo, una categoria del tipo anterior deja de ser valida.
            if (selectedCategoryId.isNotBlank() && categoriesForType.none { it.id == selectedCategoryId }) {
                selectedCategoryId = ""
            }

            Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId.isBlank(),
                        onClick = { selectedCategoryId = "" },
                        label = { Text(stringResource(R.string.category_none_auto)) }
                    )
                }
                items(categoriesForType, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) }
                    )
                }
            }

            Text(stringResource(R.string.common_account), style = MaterialTheme.typography.labelLarge)
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

            Button(
                onClick = {
                    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
                    if (name.isNotBlank() && amount > 0L && selectedAccountId.isNotEmpty()) {
                        onSave(name, selectedAccountId, selectedCategoryId, amount, frequency, type)
                    }
                },
                enabled = name.isNotBlank() &&
                    (MoneyParser.parseMinorUnits(amountStr) ?: 0L) > 0L &&
                    selectedAccountId.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Confirma el importe antes de registrar la ocurrencia. El plan guarda lo previsto,
 * pero lo que se anota en la cuenta es lo que realmente entró o salió: una quincena
 * puede traer horas extra o un descuento sin que el sueldo planificado cambie.
 */
@Composable
private fun ConfirmAmountDialog(
    payment: PlannedPayment,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var amountStr by remember(payment.id) {
        // Se precarga lo previsto: lo normal es confirmarlo tal cual.
        mutableStateOf((payment.amount / 100.0).toString())
    }
    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
    val isIncome = payment.type == "INCOME"

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isIncome) R.string.planned_register_income else R.string.planned_register_expense
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(payment.name, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.planned_amount_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.planned_amount, MoneyFormat.symbol())) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount) }, enabled = amount > 0L) {
                Text(
                    stringResource(
                        if (isIncome) R.string.planned_receive_now else R.string.planned_pay_now
                    )
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
