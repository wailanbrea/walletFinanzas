package com.bsolutions.wallet.presentation.transactions

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.bsolutions.wallet.R
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.core.common.MoneyParser
import com.bsolutions.wallet.core.designsystem.CurrencyDisplayTextStyle
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Category
import com.bsolutions.wallet.domain.model.Debt
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.presentation.dashboard.TransactionItem
import com.bsolutions.wallet.presentation.dashboard.getIconForName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isAddingTransaction by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            transaction = tx,
            account = uiState.accounts.find { it.id == tx.accountId },
            categories = uiState.categories,
            onDismiss = { selectedTransaction = null },
            onUpdate = { newAmount, newCatId, newNote ->
                viewModel.updateTransaction(tx, newAmount, newCatId, newNote)
                selectedTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx)
                selectedTransaction = null
            },
            linkedDebt = tx.debtId?.let { id -> uiState.receivables.find { it.id == id } },
            openReceivables = uiState.openReceivables,
            onMarkAsLoan = { personName, note ->
                viewModel.markAsLoan(tx, personName, note)
                selectedTransaction = null
            },
            onApplyToDebt = { debtId ->
                viewModel.applyToDebt(tx, debtId)
                selectedTransaction = null
            },
            onUnlinkDebt = {
                viewModel.unlinkFromDebt(tx)
                selectedTransaction = null
            }
        )
    }

    if (isAddingTransaction) {
        AddTransactionView(
            accounts = uiState.accounts,
            categories = uiState.categories,
            onDismiss = { isAddingTransaction = false },
            onSave = { accId, amount, type, catId, note ->
                viewModel.addTransaction(accId, amount, type, catId, note)
                isAddingTransaction = false
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_transactions), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_open_menu))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { isAddingTransaction = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dashboard_add_transaction))
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.tx_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Horizontal Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(stringResource(R.string.tx_filter_month)) },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(stringResource(R.string.tx_filter_all_accounts)) },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(stringResource(R.string.categories_title)) },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                // Transactions List
                if (uiState.transactions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tx_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // In a real production app, we would group items by date.
                        // For simplicity in the MVP, we display a reactive scroll list.
                        items(uiState.transactions) { tx ->
                            val category = uiState.categories.find { it.id == tx.categoryId }
                            val dateStr = SimpleDateFormat("dd MMM, hh:mm a", LocalConfiguration.current.locales[0]).format(Date(tx.date))

                            TransactionItem(
                                title = tx.note.ifEmpty { category?.name ?: "Otros" },
                                subtitle = dateStr,
                                amount = tx.amount,
                                type = tx.type,
                                icon = getIconForName(category?.icon ?: "shopping_cart"),
                                currency = tx.currency,
                                categoryName = category?.name,
                                onClick = { selectedTransaction = tx }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: Transaction,
    account: Account?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onUpdate: (newAmount: Long, newCategoryId: String, newNote: String) -> Unit,
    onDelete: () -> Unit,
    /** Deuda a la que pertenece este movimiento, si ya está atado a una. */
    linkedDebt: Debt? = null,
    /** Deudas por cobrar abiertas: a ellas se puede aplicar un ingreso como abono. */
    openReceivables: List<Debt> = emptyList(),
    onMarkAsLoan: (personName: String, note: String) -> Unit = { _, _ -> },
    onApplyToDebt: (debtId: String) -> Unit = {},
    onUnlinkDebt: () -> Unit = {}
) {
    var isEditing by remember { mutableStateOf(false) }
    var askLoanName by remember { mutableStateOf(false) }
    var pickDebt by remember { mutableStateOf(false) }
    var loanPersonName by remember { mutableStateOf("") }
    var loanNote by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", transaction.amount / 100.0)) }
    var note by remember { mutableStateOf(transaction.note) }
    var selectedCategoryId by remember(transaction.id, categories) {
        mutableStateOf(transaction.categoryId.takeIf { id -> categories.any { it.id == id } }.orEmpty())
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val category = categories.find { it.id == transaction.categoryId }
    val dateStr = SimpleDateFormat("dd MMMM yyyy, hh:mm a", LocalConfiguration.current.locales[0]).format(Date(transaction.date))
    val typeLabel = stringResource(
        when (transaction.type) {
            "INCOME" -> R.string.quick_income
            "TRANSFER" -> R.string.quick_transfer
            else -> R.string.quick_expense
        }
    )
    val typeColor = when (transaction.type) {
        "INCOME" -> MaterialTheme.colorScheme.secondary
        "TRANSFER" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) stringResource(R.string.tx_edit_title, typeLabel) else typeLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
                if (!isEditing && transaction.type != "TRANSFER") {
                    Row {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (!isEditing) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.tx_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId.isBlank(),
                            onClick = { selectedCategoryId = "" },
                            label = { Text(stringResource(R.string.category_none_auto)) }
                        )
                    }
                    if (categories.isNotEmpty()) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.tx_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
                            if (amount > 0) onUpdate(amount, selectedCategoryId, note)
                        },
                        enabled = (MoneyParser.parseMinorUnits(amountStr) ?: 0L) > 0L,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold) }
                }
            } else {
                Text(
                    text = MoneyFormat.format(transaction.amount),
                    style = CurrencyDisplayTextStyle,
                    color = typeColor,
                    fontWeight = FontWeight.Bold
                )
                DetailRow(label = stringResource(R.string.common_account), value = account?.name ?: "—")
                DetailRow(label = stringResource(R.string.common_category), value = category?.name ?: stringResource(R.string.tx_no_category))
                DetailRow(label = stringResource(R.string.tx_date), value = dateStr)
                if (transaction.note.isNotBlank()) {
                    DetailRow(label = stringResource(R.string.tx_note), value = transaction.note)
                }
                when {
                    linkedDebt != null -> {
                        DetailRow(
                            label = stringResource(R.string.tx_debt_linked),
                            value = linkedDebt.name
                        )
                        DetailRow(
                            label = stringResource(R.string.tx_debt_remaining),
                            value = MoneyFormat.format(linkedDebt.remainingAmount)
                        )
                        TextButton(onClick = onUnlinkDebt) {
                            Text(stringResource(R.string.tx_debt_unlink))
                        }
                    }
                    transaction.type == "EXPENSE" -> {
                        TextButton(onClick = { askLoanName = true }) {
                            Text(stringResource(R.string.tx_mark_as_loan))
                        }
                        // Un gasto por algo que ya prestaste (el currier de la mica) no
                        // abre otra deuda: engorda la que ya existe.
                        if (openReceivables.isNotEmpty()) {
                            TextButton(onClick = { pickDebt = true }) {
                                Text(stringResource(R.string.tx_add_to_debt))
                            }
                        }
                    }
                    transaction.type == "INCOME" && openReceivables.isNotEmpty() -> {
                        TextButton(onClick = { pickDebt = true }) {
                            Text(stringResource(R.string.tx_apply_to_debt))
                        }
                    }
                }
            }
        }
    }

    if (askLoanName) {
        AlertDialog(
            onDismissRequest = { askLoanName = false },
            title = { Text(stringResource(R.string.tx_mark_as_loan)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.tx_mark_as_loan_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = loanPersonName,
                        onValueChange = { loanPersonName = it },
                        label = { Text(stringResource(R.string.tx_loan_person)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Sirve para acordarse de que era el prestamo cuando pasen meses.
                    OutlinedTextField(
                        value = loanNote,
                        onValueChange = { loanNote = it },
                        label = { Text(stringResource(R.string.tx_loan_note)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askLoanName = false
                        onMarkAsLoan(loanPersonName, loanNote)
                    },
                    enabled = loanPersonName.isNotBlank()
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { askLoanName = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (pickDebt) {
        AlertDialog(
            onDismissRequest = { pickDebt = false },
            title = {
                Text(
                    stringResource(
                        if (transaction.type == "EXPENSE") R.string.tx_add_to_debt
                        else R.string.tx_apply_to_debt
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (transaction.type == "EXPENSE") R.string.tx_add_to_debt_help
                            else R.string.tx_apply_to_debt_help
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    openReceivables.forEach { debt ->
                        OutlinedButton(
                            onClick = {
                                pickDebt = false
                                onApplyToDebt(debt.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${debt.name} · ${MoneyFormat.format(debt.remainingAmount)}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickDebt = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.tx_delete_title)) },
            text = { Text(stringResource(R.string.tx_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionView(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (accountId: String, amount: Long, type: String, categoryId: String, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tx_add_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                colors = walletTopBarColors()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            // Large Amount Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        placeholder = { Text("0.00") },
                        textStyle = CurrencyDisplayTextStyle.copy(
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.width(200.dp)
                    )
                }
            }

            // Form Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Transaction Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { type = "EXPENSE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "EXPENSE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "EXPENSE") MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.quick_expense)) }

                    Button(
                        onClick = { type = "INCOME" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "INCOME") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "INCOME") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.quick_income)) }
                }

                // Account Selector
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

                // Category Chips
                Text(stringResource(R.string.common_category), style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId.isBlank(),
                            onClick = { selectedCategoryId = "" },
                            label = { Text(stringResource(R.string.category_none_auto)) }
                        )
                    }
                    items(categories) { cat ->
                        val selected = selectedCategoryId == cat.id
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getIconForName(cat.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.common_note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = {
                        val amount = MoneyParser.parseMinorUnits(amountStr) ?: 0L
                        onSave(selectedAccountId, amount, type, selectedCategoryId, note)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = (MoneyParser.parseMinorUnits(amountStr) ?: 0L) > 0L && selectedAccountId.isNotEmpty()
                ) {
                    Text(stringResource(R.string.tx_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
