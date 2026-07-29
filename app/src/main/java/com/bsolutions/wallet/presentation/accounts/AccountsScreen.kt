package com.bsolutions.wallet.presentation.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.core.common.MoneyParser
import com.bsolutions.wallet.core.designsystem.CurrencyDisplayTextStyle
import com.bsolutions.wallet.core.financial.FinancialInstitutions
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Transaction
import androidx.compose.ui.platform.LocalContext
import com.bsolutions.wallet.presentation.common.GradientSummaryCard
import com.bsolutions.wallet.presentation.common.authenticateBiometric
import com.bsolutions.wallet.presentation.dashboard.TransactionItem
import com.bsolutions.wallet.presentation.dashboard.getIconForName
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showEditAccountDialog by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val privacyAuthTitle = stringResource(R.string.privacy_auth_title)
    val privacyAuthSubtitle = stringResource(R.string.privacy_auth_subtitle)

    val selectedAccount = uiState.accounts.find { it.id == uiState.selectedAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(if (selectedAccount != null) R.string.accounts_detail_title else R.string.accounts_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (selectedAccount != null) {
                        // Detalle de cuenta: es una pantalla hija → flecha
                        IconButton(onClick = { viewModel.selectAccount(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    } else {
                        // Sección del drawer → hamburguesa
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_open_menu))
                        }
                    }
                },
                actions = {
                    if (selectedAccount != null) {
                        IconButton(onClick = { showEditAccountDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.accounts_edit))
                        }
                        IconButton(onClick = { confirmDeleteAccount = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.accounts_delete),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
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
            if (selectedAccount == null) {
                FloatingActionButton(
                    onClick = { showAddAccountDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedAccount != null) {
                // Account Detail View
                AccountDetailView(
                    account = selectedAccount,
                    transactions = uiState.selectedAccountTransactions
                )
            } else {
                // Accounts Master List View
                AccountsListView(
                    totalBalance = uiState.totalBalance,
                    foreignSubtitle = uiState.foreignBalancesSubtitle,
                    accounts = uiState.accounts,
                    hidden = uiState.balancesHidden,
                    onAccountClick = { viewModel.selectAccount(it.id) }
                )
            }
        }

        // Add Account Dialog
        if (showAddAccountDialog) {
            AddAccountDialog(
                countryCode = uiState.financialCountryCode,
                onDismiss = { showAddAccountDialog = false },
                onConfirm = { name, type, balance, institutionName, cardLastFour, creditLimit ->
                    viewModel.addAccount(
                        name, type, balance, uiState.financialCountryCode,
                        institutionName, cardLastFour, creditLimit
                    )
                    showAddAccountDialog = false
                }
            )
        }

        // Edit Account Dialog
        if (showEditAccountDialog && selectedAccount != null) {
            EditAccountDialog(
                account = selectedAccount,
                onDismiss = { showEditAccountDialog = false },
                onConfirm = { name, type, balance, creditLimit ->
                    viewModel.updateAccount(selectedAccount, name, type, balance, creditLimit)
                    showEditAccountDialog = false
                }
            )
        }

        // Confirm Delete Account
        if (confirmDeleteAccount && selectedAccount != null) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAccount = false },
                title = { Text(stringResource(R.string.accounts_delete)) },
                text = { Text(stringResource(R.string.accounts_delete_confirm, selectedAccount.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteAccount = false
                        viewModel.deleteAccount(selectedAccount.id)
                    }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteAccount = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
    }
}

@Composable
fun EditAccountDialog(
    account: Account,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, displayedBalance: Long, creditLimit: Long?) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var type by remember { mutableStateOf(account.type) }
    var balanceStr by remember {
        mutableStateOf(minorUnitsInput(if (account.type == "CREDIT_CARD") creditCardDebt(account.balance) else account.balance))
    }
    var creditLimitStr by remember { mutableStateOf(account.creditLimit?.let(::minorUnitsInput).orEmpty()) }
    val balance = MoneyParser.parseMinorUnits(balanceStr)
    val creditLimit = MoneyParser.parseMinorUnits(creditLimitStr)
    val valid = name.isNotBlank() && balance != null &&
        (type != "CREDIT_CARD" || (balance >= 0L && (creditLimit ?: 0L) > 0L))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.common_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "BANK", onClick = { type = "BANK" }, label = { Text(stringResource(R.string.account_type_bank)) })
                    FilterChip(selected = type == "CASH", onClick = { type = "CASH" }, label = { Text(stringResource(R.string.account_type_cash)) })
                    FilterChip(selected = type == "SAVINGS", onClick = { type = "SAVINGS" }, label = { Text(stringResource(R.string.account_type_savings)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "DEBIT_CARD", onClick = { type = "DEBIT_CARD" }, label = { Text(stringResource(R.string.account_type_debit)) })
                    FilterChip(selected = type == "CREDIT_CARD", onClick = { type = "CREDIT_CARD" }, label = { Text(stringResource(R.string.account_type_credit)) })
                }
                OutlinedTextField(
                    value = balanceStr,
                    onValueChange = { balanceStr = it },
                    label = {
                        Text(stringResource(if (type == "CREDIT_CARD") R.string.accounts_current_debt else R.string.accounts_current_balance, MoneyFormat.symbol(account.currency)))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == "CREDIT_CARD") {
                    OutlinedTextField(
                        value = creditLimitStr,
                        onValueChange = { creditLimitStr = it },
                        label = { Text(stringResource(R.string.accounts_credit_limit_input, MoneyFormat.symbol(account.currency))) },
                        supportingText = {
                            if (creditLimitStr.isNotEmpty() && (creditLimit ?: 0L) <= 0L) {
                                Text(stringResource(R.string.accounts_credit_limit_error))
                            }
                        },
                        isError = creditLimitStr.isNotEmpty() && (creditLimit ?: 0L) <= 0L,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, type, balance ?: 0L, creditLimit) },
                enabled = valid
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun AccountsListView(
    totalBalance: Long,
    foreignSubtitle: String?,
    accounts: List<Account>,
    hidden: Boolean,
    onAccountClick: (Account) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Balance total con degradado de marca y monto animado
            GradientSummaryCard(
                title = stringResource(R.string.dashboard_total_balance),
                amount = totalBalance,
                subtitle = foreignSubtitle?.let { stringResource(R.string.dashboard_other_currencies, it) },
                hidden = hidden
            )
        }

        // Cuentas y tarjetas no son lo mismo: una guarda dinero propio y la otra es
        // credito del banco. Van en bloques separados para no leerlas como una sola lista.
        val (cards, deposits) = accounts.partition { it.type == "CREDIT_CARD" || it.type == "DEBIT_CARD" }

        if (deposits.isNotEmpty()) {
            item { AccountsSectionHeader(stringResource(R.string.accounts_section_accounts)) }
            // Los saldos por cuenta se ven siempre: el modo privacidad solo cubre el Balance Total.
            items(deposits, key = { it.id }) { account ->
                AccountRow(account = account, onClick = { onAccountClick(account) })
            }
        }

        if (cards.isNotEmpty()) {
            item { AccountsSectionHeader(stringResource(R.string.accounts_section_cards)) }
            items(cards, key = { it.id }) { account ->
                AccountRow(account = account, onClick = { onAccountClick(account) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccountsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun AccountRow(
    account: Account,
    onClick: () -> Unit
) {
    if (account.type == "DEBIT_CARD" || account.type == "CREDIT_CARD") {
        WalletCard(account = account, onClick = onClick)
        return
    }
    val icon = when (account.type) {
        "BANK" -> Icons.Default.AccountBalance
        "SAVINGS" -> Icons.Default.CreditCard
        else -> Icons.Default.Wallet
    }
    val iconBgColor = when (account.type) {
        "BANK" -> MaterialTheme.colorScheme.primaryContainer
        "SAVINGS" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val iconColor = when (account.type) {
        "BANK" -> MaterialTheme.colorScheme.onPrimaryContainer
        "SAVINGS" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // El nombre cede espacio al monto: nunca lo aplasta ni lo parte en dos líneas
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                if (account.institutionName != null) {
                    InstitutionLogo(account.institutionName, size = 48.dp)
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = account.name,
                        tint = iconColor
                    )
                }
            }
            Column {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.institutionName ?: when (account.type) {
                        "BANK" -> stringResource(R.string.account_type_bank)
                        "SAVINGS" -> stringResource(R.string.account_type_savings)
                        else -> stringResource(R.string.account_type_cash)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = MoneyFormat.format(account.balance, account.currency),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = if (account.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AccountDetailView(
    account: Account,
    transactions: List<Transaction>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Account Identity Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    InstitutionLogo(account.institutionName ?: account.name, size = 56.dp)
                }
                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = when (account.type) {
                            "BANK" -> stringResource(R.string.account_type_checking)
                            "SAVINGS" -> stringResource(R.string.account_type_savings)
                            "DEBIT_CARD" -> stringResource(R.string.account_type_debit)
                            "CREDIT_CARD" -> stringResource(R.string.account_type_credit)
                            else -> stringResource(R.string.account_type_cash)
                        } + (account.institutionName?.let { " • $it" } ?: "") + " • " + account.currency,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            // Balance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (account.type == "CREDIT_CARD") {
                        CreditMetric(
                            label = stringResource(R.string.accounts_balance_to_date),
                            amount = creditCardDebt(account.balance),
                            currency = account.currency,
                            prominent = true
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CreditMetric(
                                    label = stringResource(R.string.accounts_credit_limit),
                                    amount = account.creditLimit ?: 0L,
                                    currency = account.currency
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CreditMetric(
                                    label = stringResource(R.string.accounts_credit_available),
                                    amount = availableCredit(account.balance, account.creditLimit),
                                    currency = account.currency
                                )
                            }
                        }
                    } else {
                        CreditMetric(
                            label = stringResource(R.string.accounts_available_balance),
                            amount = account.balance,
                            currency = account.currency,
                            prominent = true
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.accounts_movements),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.accounts_no_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(transactions) { tx ->
                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", LocalConfiguration.current.locales[0]).format(Date(tx.date))
                TransactionItem(
                    title = tx.note.ifEmpty { stringResource(R.string.tx_generic) },
                    subtitle = dateStr,
                    amount = tx.amount,
                    type = tx.type,
                    icon = getIconForName("shopping_cart"),
                    currency = tx.currency
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    countryCode: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long, String?, String?, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BANK") }
    var balanceStr by remember { mutableStateOf("") }
    var institutionExpanded by remember { mutableStateOf(false) }
    var institutionName by remember { mutableStateOf<String?>(null) }
    var cardLastFour by remember { mutableStateOf("") }
    var creditLimitStr by remember { mutableStateOf("") }
    val parsedBalance = MoneyParser.parseMinorUnits(balanceStr)
    val balance = parsedBalance ?: 0L
    val creditLimit = MoneyParser.parseMinorUnits(creditLimitStr)
    val valid = name.isNotBlank() && (balanceStr.isBlank() || parsedBalance != null) &&
        (type != "CREDIT_CARD" || (balance >= 0L && (creditLimit ?: 0L) > 0L))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.accounts_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.accounts_type), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { type = "BANK" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "BANK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "BANK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.account_type_bank)) }

                    Button(
                        onClick = { type = "CASH" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "CASH") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (type == "CASH") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.account_type_cash)) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { type = "DEBIT_CARD" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (type == "DEBIT_CARD") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    ) { Text(stringResource(R.string.account_type_debit)) }
                    OutlinedButton(
                        onClick = { type = "CREDIT_CARD" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (type == "CREDIT_CARD") MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    ) { Text(stringResource(R.string.account_type_credit)) }
                }

                if (type == "BANK" || type == "DEBIT_CARD" || type == "CREDIT_CARD") {
                    ExposedDropdownMenuBox(
                        expanded = institutionExpanded,
                        onExpandedChange = { institutionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = institutionName ?: stringResource(R.string.accounts_institution_other),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.accounts_institution_label, FinancialInstitutions.countryName(countryCode))) },
                            leadingIcon = institutionName?.let { selected ->
                                { InstitutionLogo(selected, size = 32.dp) }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(institutionExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = institutionExpanded, onDismissRequest = { institutionExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.accounts_institution_other)) },
                                leadingIcon = { InstitutionLogo(null, size = 32.dp) },
                                onClick = { institutionName = null; institutionExpanded = false }
                            )
                            FinancialInstitutions.forCountry(countryCode).forEach { institution ->
                                DropdownMenuItem(
                                    text = { Text(institution.name) },
                                    leadingIcon = { InstitutionLogo(institution.name, size = 32.dp) },
                                    onClick = { institutionName = institution.name; institutionExpanded = false }
                                )
                            }
                        }
                    }
                }

                if (type == "DEBIT_CARD" || type == "CREDIT_CARD") {
                    OutlinedTextField(
                        value = cardLastFour,
                        onValueChange = { cardLastFour = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.accounts_card_last_four)) },
                        supportingText = { Text(stringResource(R.string.accounts_card_security_note)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = balanceStr,
                    onValueChange = { balanceStr = it },
                    label = {
                        Text(stringResource(if (type == "CREDIT_CARD") R.string.accounts_current_debt else R.string.accounts_initial_balance, MoneyFormat.symbol()))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == "CREDIT_CARD") {
                    OutlinedTextField(
                        value = creditLimitStr,
                        onValueChange = { creditLimitStr = it },
                        label = { Text(stringResource(R.string.accounts_credit_limit_input, MoneyFormat.symbol())) },
                        supportingText = {
                            if (creditLimitStr.isNotEmpty() && (creditLimit ?: 0L) <= 0L) {
                                Text(stringResource(R.string.accounts_credit_limit_error))
                            }
                        },
                        isError = creditLimitStr.isNotEmpty() && (creditLimit ?: 0L) <= 0L,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, type, balance, institutionName, cardLastFour.takeIf { it.length == 4 }, creditLimit)
                },
                enabled = valid
            ) {
                Text(stringResource(R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun WalletCard(account: Account, onClick: () -> Unit) {
    val colors = if (account.type == "CREDIT_CARD") listOf(Color(0xFF5B1A88), Color(0xFF21134B)) else listOf(Color(0xFF075E54), Color(0xFF003B73))
    Column(
        modifier = Modifier.fillMaxWidth().height(204.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (account.type == "CREDIT_CARD") R.string.accounts_card_credit else R.string.accounts_card_debit),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
                color = Color.White.copy(alpha = 0.82f)
            )
            InstitutionLogo(account.institutionName ?: account.name, size = 34.dp, onDarkBackground = true)
        }
        Text("••••  ••••  ••••  ${account.cardLastFour ?: "••••"}", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp), color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.name.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    account.institutionName
                        ?: stringResource(if (account.type == "CREDIT_CARD") R.string.account_type_credit else R.string.account_type_debit),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(if (account.type == "CREDIT_CARD") R.string.accounts_balance_to_date else R.string.accounts_available_balance),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Text(
                    MoneyFormat.format(if (account.type == "CREDIT_CARD") creditCardDebt(account.balance) else account.balance, account.currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CreditMetric(
    label: String,
    amount: Long,
    currency: String,
    prominent: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = MoneyFormat.format(amount, currency),
            style = if (prominent) CurrencyDisplayTextStyle else MaterialTheme.typography.titleMedium,
            color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun minorUnitsInput(amount: Long): String = BigDecimal.valueOf(amount, 2).toPlainString()
