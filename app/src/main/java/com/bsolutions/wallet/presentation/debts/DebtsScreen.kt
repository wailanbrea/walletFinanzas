package com.bsolutions.wallet.presentation.debts

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bsolutions.wallet.R
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.core.common.MoneyParser
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.presentation.common.animatedProgress

private fun formatMoney(minorUnits: Long): String = MoneyFormat.format(minorUnits)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: DebtsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var paymentDebt by remember { mutableStateOf<Debt?>(null) }

    if (showCreateSheet) {
        CreateDebtSheet(
            onDismiss = { showCreateSheet = false },
            onSave = { name, description, direction, amount ->
                viewModel.addDebt(name, description, direction, amount)
                showCreateSheet = false
            }
        )
    }

    paymentDebt?.let { debt ->
        RecordPaymentSheet(
            debt = debt,
            accounts = uiState.accounts,
            onDismiss = { paymentDebt = null },
            onSave = { amount, accountId ->
                viewModel.recordPayment(debt, amount, accountId)
                paymentDebt = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debts_title), fontWeight = FontWeight.Bold) },
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.debts_new))
            }
        }
    ) { innerPadding ->
        val isEmpty = uiState.iOweDebts.isEmpty() && uiState.owedToMeDebts.isEmpty()
        if (isEmpty) {
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
                        imageVector = Icons.Outlined.MonetizationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.debts_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.debts_empty_desc),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryCard(
                            title = stringResource(R.string.debts_i_owe),
                            amount = uiState.totalIOwe,
                            isNegative = true,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            title = stringResource(R.string.debts_owed_to_me),
                            amount = uiState.totalOwedToMe,
                            isNegative = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (uiState.iOweDebts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.debts_i_owe),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(uiState.iOweDebts, key = { it.id }) { debt ->
                        DebtCard(
                            debt = debt,
                            onRecordPayment = { paymentDebt = debt },
                            onDelete = { viewModel.deleteDebt(debt.id) }
                        )
                    }
                }

                if (uiState.owedToMeDebts.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.debts_owed_to_me),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(uiState.owedToMeDebts, key = { it.id }) { debt ->
                        DebtCard(
                            debt = debt,
                            onRecordPayment = { paymentDebt = debt },
                            onDelete = { viewModel.deleteDebt(debt.id) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Long,
    isNegative: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNegative) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isNegative) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = formatMoney(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isNegative) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DebtCard(
    debt: Debt,
    onRecordPayment: () -> Unit,
    onDelete: () -> Unit
) {
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
                            if (debt.isClosed) MaterialTheme.colorScheme.secondaryContainer
                            else if (debt.direction == "I_OWE") MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (debt.isClosed) Icons.Default.CheckCircle else Icons.Outlined.MonetizationOn,
                        contentDescription = null,
                        tint = if (debt.isClosed) MaterialTheme.colorScheme.onSecondaryContainer
                        else if (debt.direction == "I_OWE") MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = debt.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (debt.isClosed) stringResource(R.string.debts_settled)
                        else stringResource(R.string.debts_remaining_of, formatMoney(debt.remainingAmount), formatMoney(debt.totalAmount)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.debts_delete),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val progress = animatedProgress(debt.progress)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (debt.isClosed) MaterialTheme.colorScheme.secondary
                else if (debt.direction == "I_OWE") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondary
            )
            if (!debt.isClosed) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.debts_record_payment),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRecordPayment)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDebtSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, direction: String, amount: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("I_OWE") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.debts_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction == "I_OWE",
                    onClick = { direction = "I_OWE" },
                    label = { Text(stringResource(R.string.debts_i_owe)) }
                )
                FilterChip(
                    selected = direction == "OWED_TO_ME",
                    onClick = { direction = "OWED_TO_ME" },
                    label = { Text(stringResource(R.string.debts_owed_to_me)) }
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(if (direction == "I_OWE") R.string.debts_who_you_owe else R.string.debts_who_owes_you)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.debts_description_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.debts_total_amount, MoneyFormat.symbol())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
                    if (name.isNotBlank() && amount > 0L) {
                        onSave(name, description, direction, amount)
                    }
                },
                enabled = name.isNotBlank() && (MoneyParser.parseMinorUnits(amountStr) ?: 0L) > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.debts_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentSheet(
    debt: Debt,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (amount: Long, accountId: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.debts_payment_to, debt.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.debts_remaining, formatMoney(debt.remainingAmount)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.debts_payment_amount, MoneyFormat.symbol())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            // El dinero cobrado tiene que entrar en algún lado: sin cuenta, el abono
            // sería solo un contador y el saldo se quedaría corto.
            Text(
                text = stringResource(R.string.debts_payment_account),
                style = MaterialTheme.typography.labelLarge
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts, key = { it.id }) { account ->
                    FilterChip(
                        selected = selectedAccountId == account.id,
                        onClick = { selectedAccountId = account.id },
                        label = { Text("${account.name} (${account.currency})") }
                    )
                }
            }
            if (accounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.debts_payment_no_accounts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
                    if (amount > 0L && selectedAccountId.isNotBlank()) {
                        onSave(amount, selectedAccountId)
                    }
                },
                enabled = (MoneyParser.parseMinorUnits(amountStr) ?: 0L) > 0L &&
                    selectedAccountId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.debts_payment_action), fontWeight = FontWeight.Bold)
            }
        }
    }
}
