package com.bsolutions.wallet.presentation.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.bsolutions.wallet.presentation.common.privacyBlur
import com.bsolutions.wallet.presentation.dashboard.TransactionItem
import com.bsolutions.wallet.presentation.dashboard.getIconForName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    transactions = uiState.selectedAccountTransactions,
                    hidden = uiState.balancesHidden
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
                onConfirm = { name, type, balance, institutionName, cardLastFour ->
                    viewModel.addAccount(name, type, balance, uiState.financialCountryCode, institutionName, cardLastFour)
                    showAddAccountDialog = false
                }
            )
        }

        // Edit Account Dialog
        if (showEditAccountDialog && selectedAccount != null) {
            EditAccountDialog(
                account = selectedAccount,
                onDismiss = { showEditAccountDialog = false },
                onConfirm = { name, type ->
                    viewModel.updateAccount(selectedAccount, name, type)
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
    onConfirm: (name: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var type by remember { mutableStateOf(account.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, type) },
                enabled = name.isNotBlank()
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

        items(accounts) { account ->
            AccountRow(
                account = account,
                hidden = hidden,
                onClick = { onAccountClick(account) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AccountRow(
    account: Account,
    hidden: Boolean = false,
    onClick: () -> Unit
) {
    if (account.type == "DEBIT_CARD" || account.type == "CREDIT_CARD") {
        WalletCard(account = account, hidden = hidden, onClick = onClick)
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
                Icon(
                    imageVector = icon,
                    contentDescription = account.name,
                    tint = iconColor
                )
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
            modifier = Modifier.privacyBlur(hidden, radius = 10.dp),
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
    transactions: List<Transaction>,
    hidden: Boolean = false
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
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
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
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.accounts_available_balance),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = MoneyFormat.symbol(account.currency),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%,.2f", account.balance / 100.0),
                            modifier = Modifier.privacyBlur(hidden, radius = 14.dp),
                            style = CurrencyDisplayTextStyle,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
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
    onConfirm: (String, String, Long, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BANK") }
    var balanceStr by remember { mutableStateOf("") }
    var institutionExpanded by remember { mutableStateOf(false) }
    var institutionName by remember { mutableStateOf<String?>(null) }
    var cardLastFour by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(institutionExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = institutionExpanded, onDismissRequest = { institutionExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.accounts_institution_other)) }, onClick = { institutionName = null; institutionExpanded = false })
                            FinancialInstitutions.forCountry(countryCode).forEach { institution ->
                                DropdownMenuItem(text = { Text(institution.name) }, onClick = { institutionName = institution.name; institutionExpanded = false })
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
                    label = { Text(stringResource(R.string.accounts_initial_balance, MoneyFormat.symbol())) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = MoneyParser.parseMinorUnits(balanceStr) ?: 0L
                    onConfirm(name, type, balance, institutionName, cardLastFour.takeIf { it.length == 4 })
                },
                enabled = name.isNotEmpty()
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
private fun WalletCard(account: Account, hidden: Boolean = false, onClick: () -> Unit) {
    val colors = if (account.type == "CREDIT_CARD") listOf(Color(0xFF5B1A88), Color(0xFF21134B)) else listOf(Color(0xFF075E54), Color(0xFF003B73))
    Column(
        modifier = Modifier.fillMaxWidth().height(204.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(if (account.type == "CREDIT_CARD") "CRÉDITO" else "DÉBITO", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), color = Color.White.copy(alpha = 0.82f))
            Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
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
            Text(
                MoneyFormat.format(account.balance, account.currency),
                modifier = Modifier.privacyBlur(hidden, radius = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
