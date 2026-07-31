package com.bsolutions.wallet.presentation.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bsolutions.wallet.R
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.core.common.MoneyParser
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Transaction
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
    var sheetIsCharge by remember { mutableStateOf(false) }
    var detailDebt by remember { mutableStateOf<Debt?>(null) }
    var editDebt by remember { mutableStateOf<Debt?>(null) }

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
            isCharge = sheetIsCharge,
            onDismiss = { paymentDebt = null },
            onSave = { amount, accountId, note ->
                if (sheetIsCharge) {
                    viewModel.addCharge(debt, amount, accountId, note)
                } else {
                    viewModel.recordPayment(debt, amount, accountId)
                }
                paymentDebt = null
            }
        )
    }

    detailDebt?.let { debt ->
        // Se relee del estado y no se guarda la copia del momento: si desde el detalle se
        // corrige el monto o se registra un abono, lo que se ve tiene que cambiar solo.
        val fresh = (uiState.owedToMeDebts + uiState.iOweDebts).find { it.id == debt.id }
        if (fresh == null) {
            detailDebt = null
        } else {
            DebtDetailSheet(
                debt = fresh,
                movements = uiState.transactionsByDebt[fresh.id].orEmpty(),
                onDismiss = { detailDebt = null },
                onEdit = { editDebt = fresh },
                onAddCharge = { paymentDebt = fresh; sheetIsCharge = true; detailDebt = null },
                onRecordPayment = { paymentDebt = fresh; sheetIsCharge = false; detailDebt = null }
            )
        }
    }

    editDebt?.let { debt ->
        EditDebtSheet(
            debt = debt,
            onDismiss = { editDebt = null },
            onSave = { name, description, amount ->
                viewModel.updateDebt(debt, name, description, amount)
                editDebt = null
            }
        )
    }

    // Arranca en el lado que tiene algo: abrir en una lista vacia se ve roto.
    var shownDirection by remember(uiState.owedToMeDebts.isEmpty()) {
        mutableStateOf(if (uiState.owedToMeDebts.isEmpty() && uiState.iOweDebts.isNotEmpty()) "I_OWE" else "OWED_TO_ME")
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
                // Las tarjetas de resumen son el selector: antes el rotulo aparecia dos
                // veces, en el resumen y otra vez como texto suelto sobre cada lista.
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryCard(
                            title = stringResource(R.string.debts_owed_to_me),
                            amount = uiState.totalOwedToMe,
                            count = uiState.owedToMeDebts.count { !it.isClosed },
                            icon = Icons.Default.CallReceived,
                            isNegative = false,
                            selected = shownDirection == "OWED_TO_ME",
                            onClick = { shownDirection = "OWED_TO_ME" },
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCard(
                            title = stringResource(R.string.debts_i_owe),
                            amount = uiState.totalIOwe,
                            count = uiState.iOweDebts.count { !it.isClosed },
                            icon = Icons.Default.CallMade,
                            isNegative = true,
                            selected = shownDirection == "I_OWE",
                            onClick = { shownDirection = "I_OWE" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val shown = if (shownDirection == "I_OWE") uiState.iOweDebts else uiState.owedToMeDebts
                if (shown.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (shownDirection == "I_OWE") R.string.debts_none_i_owe
                                else R.string.debts_none_owed_to_me
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                }
                // Las cerradas al final: lo que sigue vivo es lo que importa.
                items(shown.sortedBy { it.isClosed }, key = { it.id }) { debt ->
                    DebtCard(
                        debt = debt,
                        onOpen = { detailDebt = debt },
                        onRecordPayment = { paymentDebt = debt; sheetIsCharge = false },
                        onAddCharge = { paymentDebt = debt; sheetIsCharge = true },
                        onDelete = { viewModel.deleteDebt(debt.id) }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * Resumen de una direccion de deuda, que ademas selecciona que lista se ve.
 *
 * Al no estar seleccionada se apaga: asi se lee de un golpe cual de las dos listas
 * esta abajo, sin repetir el rotulo como encabezado.
 */
@Composable
private fun SummaryCard(
    title: String,
    amount: Long,
    count: Int,
    icon: ImageVector,
    isNegative: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val content = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                accent.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(if (selected) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                text = formatMoney(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(R.plurals.debts_open_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DebtCard(
    debt: Debt,
    onOpen: () -> Unit,
    onRecordPayment: () -> Unit,
    onAddCharge: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        // Toda la tarjeta abre el detalle: era lo unico de la pantalla que enseñaba un
        // total sin dejar ver de donde salia.
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
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
                    val onAvatar = if (debt.isClosed) MaterialTheme.colorScheme.onSecondaryContainer
                    else if (debt.direction == "I_OWE") MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                    // La inicial de la persona en vez de una moneda genérica: en una lista
                    // de deudas lo que distingue una de otra es de quién es.
                    if (debt.isClosed) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = onAvatar
                        )
                    } else {
                        Text(
                            text = debt.name.trim().take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onAvatar
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = debt.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Se guardaba pero no se veia: sin esto no hay forma de acordarse
                    // de que era el prestamo cuando pasen meses.
                    if (debt.description.isNotBlank()) {
                        Text(
                            text = debt.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        // Una deuda saldada decia solo "Saldada", sin cifra: al cuadrar lo
                        // cobrado del mes no habia forma de saber de donde salia ese dinero,
                        // porque la deuda ya no aparece en el saldo a tu favor.
                        text = if (debt.isClosed) stringResource(R.string.debts_settled, formatMoney(debt.paidAmount))
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
                    // Un gasto nuevo por lo mismo (el currier de lo que compraste) engorda
                    // esta deuda; abrir otra por cada cargo la partiria en pedazos.
                    Text(
                        text = stringResource(R.string.debts_add_charge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onAddCharge)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
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
    /** true: se anade un cargo nuevo. false: se registra un abono. */
    isCharge: Boolean,
    onDismiss: () -> Unit,
    onSave: (amount: Long, accountId: String, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
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
                text = stringResource(
                    if (isCharge) R.string.debts_charge_to else R.string.debts_payment_to,
                    debt.name
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    if (isCharge) R.string.debts_charge_help else R.string.debts_remaining,
                    formatMoney(debt.remainingAmount)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = {
                    Text(
                        stringResource(
                            if (isCharge) R.string.debts_charge_amount else R.string.debts_payment_amount,
                            MoneyFormat.symbol()
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (isCharge) {
                // Para acordarse de por que subio: "currier de la mica" y no un monto suelto.
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.debts_charge_concept)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // El dinero tiene que salir o entrar en algún lado: sin cuenta, esto sería
            // solo un contador y el saldo se quedaría descuadrado.
            Text(
                text = stringResource(
                    if (isCharge) R.string.debts_charge_account else R.string.debts_payment_account
                ),
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
                        onSave(amount, selectedAccountId, note)
                    }
                },
                enabled = (MoneyParser.parseMinorUnits(amountStr) ?: 0L) > 0L &&
                    selectedAccountId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isCharge) R.string.debts_add_charge else R.string.debts_payment_action
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Detalle de una deuda: sus cifras y los movimientos que la formaron.
 *
 * La tarjeta solo enseñaba un total. Cuando ese total no cuadra con lo que uno recuerda
 * —y pasa, porque un movimiento mal atado lo mueve— no había ninguna forma de averiguar
 * de dónde salía, ni de corregirlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtDetailSheet(
    debt: Debt,
    movements: List<Transaction>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddCharge: () -> Unit,
    onRecordPayment: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", LocalConfiguration.current.locales[0])
    val owedToMe = debt.direction == "OWED_TO_ME"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = debt.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (debt.description.isNotBlank()) {
                Text(
                    text = debt.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DebtDetailRow(stringResource(R.string.debts_detail_total), formatMoney(debt.totalAmount))
            DebtDetailRow(
                label = stringResource(
                    if (owedToMe) R.string.debts_detail_paid else R.string.debts_detail_paid_owed
                ),
                value = formatMoney(debt.paidAmount)
            )
            DebtDetailRow(stringResource(R.string.debts_detail_remaining), formatMoney(debt.remainingAmount))

            Text(
                text = stringResource(R.string.debts_detail_movements),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (movements.isEmpty()) {
                Text(
                    text = stringResource(R.string.debts_detail_no_movements),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                movements.sortedByDescending { it.date }.forEach { movement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = movement.note.ifBlank { dateFormat.format(Date(movement.date)) },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dateFormat.format(Date(movement.date)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        // El color dice de qué lado va: lo que sale de tu bolsillo es lo
                        // que prestas, lo que entra es lo que te devuelven.
                        Text(
                            text = MoneyFormat.formatSigned(
                                movement.amount,
                                isIncome = movement.type == "INCOME",
                                currency = movement.currency
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (movement.type == "INCOME") MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.debts_edit), maxLines = 1)
                }
                if (!debt.isClosed) {
                    Button(onClick = onAddCharge, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.debts_add_charge), maxLines = 1)
                    }
                    Button(onClick = onRecordPayment, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.debts_record_payment), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Corrige los datos de una deuda sin tocar ninguna cuenta.
 *
 * Es lo que faltaba para arreglar un total mal puesto: «Agregar cargo» y «Registrar
 * abono» crean un movimiento y mueven el saldo, así que corregir un dato con ellos
 * ensuciaba las cuentas con dinero que nunca se movió.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDebtSheet(
    debt: Debt,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, totalAmount: Long) -> Unit
) {
    var name by remember(debt.id) { mutableStateOf(debt.name) }
    var description by remember(debt.id) { mutableStateOf(debt.description) }
    var amountStr by remember(debt.id) {
        mutableStateOf(String.format(Locale.US, "%.2f", debt.totalAmount / 100.0))
    }
    val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.debts_edit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.debts_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.debts_edit_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.debts_edit_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text(stringResource(R.string.debts_edit_amount, MoneyFormat.symbol())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(name, description, amount) },
                enabled = name.isNotBlank() && amount > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
