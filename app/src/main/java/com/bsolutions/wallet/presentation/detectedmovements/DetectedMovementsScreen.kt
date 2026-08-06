package com.bsolutions.wallet.presentation.detectedmovements

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bsolutions.wallet.R
import com.bsolutions.wallet.data.local.entity.DetectedMovementEntity
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Currency
import java.util.Date
import java.util.Locale

private val IncomeGreen = Color(0xFF1B873F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedMovementsScreen(
    onOpenDrawer: () -> Unit,
    viewModel: DetectedMovementsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var bookingGroup by remember { mutableStateOf<DetectedMovementGroup?>(null) }
    var duplicateGroup by remember { mutableStateOf<DetectedMovementGroup?>(null) }
    var dismissGroup by remember { mutableStateOf<DetectedMovementGroup?>(null) }
    var showDateFilter by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (showDateFilter) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDateFilter = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setCustomDate(picker.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        })
                        showDateFilter = false
                    },
                    enabled = picker.selectedDateMillis != null
                ) { Text(stringResource(R.string.common_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDateFilter = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) { DatePicker(picker) }
    }

    duplicateGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { duplicateGroup = null },
            title = { Text(stringResource(R.string.detected_movement_confirm_same_title)) },
            text = { Text(stringResource(R.string.detected_movement_confirm_same_body)) },
            confirmButton = {
                TextButton(onClick = {
                    duplicateGroup = null
                    viewModel.resolvePossibleDuplicate(group.root.id, keepSeparate = false)
                }) {
                    Text(stringResource(R.string.detected_movement_confirm_same))
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateGroup = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    dismissGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { dismissGroup = null },
            title = { Text(stringResource(R.string.detected_movement_discard_title)) },
            text = { Text(stringResource(R.string.detected_movement_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dismissGroup = null
                    viewModel.dismiss(group.root.id)
                }) {
                    Text(
                        stringResource(R.string.detected_movement_discard),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissGroup = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    bookingGroup?.let { group ->
        BookingDialog(
            group = group,
            state = state,
            onDismiss = { bookingGroup = null },
            onConfirm = { request ->
                bookingGroup = null
                viewModel.book(request)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detected_movements_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.drawer_expand))
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing && state.activeMovementId == null
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.detected_movements_refresh)
                            )
                        }
                    }
                },
                colors = walletTopBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (state.phase) {
            DetectedMovementsPhase.LOADING -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            DetectedMovementsPhase.EMPTY -> EmptyState(
                state = state,
                onFilterSelected = viewModel::setDateFilter,
                onDateSelected = { showDateFilter = true },
                onClearDate = { viewModel.setCustomDate(null) },
                onToggleSort = viewModel::toggleSortOrder,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )

            DetectedMovementsPhase.CONTENT -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DetectedMovementFilterHeader(
                        state = state,
                        onFilterSelected = viewModel::setDateFilter,
                        onDateSelected = { showDateFilter = true },
                        onClearDate = { viewModel.setCustomDate(null) },
                        onToggleSort = viewModel::toggleSortOrder
                    )
                }
                items(state.groups, key = { it.root.id }) { group ->
                    DetectedMovementCard(
                        group = group,
                        busy = state.activeMovementId != null,
                        onBook = { bookingGroup = group },
                        onDismiss = { dismissGroup = group },
                        onKeepSeparate = { viewModel.resolvePossibleDuplicate(group.root.id, keepSeparate = true) },
                        onConfirmDuplicate = { duplicateGroup = group },
                        onRetryEmail = { viewModel.retryEmailConfirmation(group.root.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(
    state: DetectedMovementsUiState,
    onFilterSelected: (DetectedMovementDateFilter) -> Unit,
    onDateSelected: () -> Unit,
    onClearDate: () -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        DetectedMovementFilterHeader(state, onFilterSelected, onDateSelected, onClearDate, onToggleSort)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.detected_movements_empty_filtered))
            }
        }
    }
}

@Composable
private fun DetectedMovementFilterHeader(
    state: DetectedMovementsUiState,
    onFilterSelected: (DetectedMovementDateFilter) -> Unit,
    onDateSelected: () -> Unit,
    onClearDate: () -> Unit,
    onToggleSort: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(DetectedMovementDateFilter.entries) { filter ->
                FilterChip(
                    selected = state.selectedDateFilter == filter && state.selectedDate == null,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(dateFilterLabel(filter)) }
                )
            }
            item {
                OutlinedButton(onClick = onDateSelected) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.selectedDate?.let { formatFilterDate(it) } ?: stringResource(R.string.detected_movements_filter_date))
                }
            }
            if (state.selectedDate != null) {
                item {
                    TextButton(onClick = onClearDate) {
                        Text(stringResource(R.string.detected_movements_filter_clear_date))
                    }
                }
            }
            item {
                FilterChip(selected = !state.sortAscending, onClick = { if (state.sortAscending) onToggleSort() }, label = { Text(stringResource(R.string.detected_movements_sort_newest)) })
            }
            item {
                FilterChip(selected = state.sortAscending, onClick = { if (!state.sortAscending) onToggleSort() }, label = { Text(stringResource(R.string.detected_movements_sort_oldest)) })
            }
        }
        Text(
            text = pluralStringResource(R.plurals.detected_movements_filter_summary, state.allActionableCount, state.groups.size, state.allActionableCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatFilterDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
@Composable
private fun dateFilterLabel(filter: DetectedMovementDateFilter): String = stringResource(
    when (filter) {
        DetectedMovementDateFilter.TODAY -> R.string.detected_movements_filter_today
        DetectedMovementDateFilter.YESTERDAY -> R.string.detected_movements_filter_yesterday
        DetectedMovementDateFilter.THIS_WEEK -> R.string.detected_movements_filter_week
        DetectedMovementDateFilter.THIS_MONTH -> R.string.detected_movements_filter_month
    }
)

@Composable
private fun DetectedMovementCard(
    group: DetectedMovementGroup,
    busy: Boolean,
    onBook: () -> Unit,
    onDismiss: () -> Unit,
    onKeepSeparate: () -> Unit,
    onConfirmDuplicate: () -> Unit,
    onRetryEmail: () -> Unit
) {
    val movement = group.root
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MovementReviewStatus(group)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(group.evidence.distinctBy { it.source }) { evidence ->
                    SourceBadge(evidence.source)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        movement.merchant ?: stringResource(R.string.detected_movement_unknown_merchant),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDate(movement.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    movement.last4Digits?.let {
                        Text(
                            stringResource(R.string.detected_movement_card, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    formatAmount(movement.amountMinor, movement.currency, movement.direction),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (movement.direction == "income") IncomeGreen else MaterialTheme.colorScheme.error
                )
            }

            if (group.hasPendingEmailConfirmation) {
                WarningBlock(
                    title = stringResource(R.string.detected_movement_pending_email),
                    body = null
                )
                Button(onClick = onRetryEmail, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.detected_movement_retry_email))
                }
            } else {
                if (group.isPossibleDuplicate) {
                    WarningBlock(
                        title = stringResource(R.string.detected_movement_possible_title),
                        body = movement.dedupeReason
                            ?: stringResource(R.string.detected_movement_possible_default)
                    )
                }

                Text(
                    stringResource(R.string.detected_movement_evidence),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                group.evidence.forEach { evidence -> EvidenceRow(evidence) }

                if (movement.direction == "transfer") {
                    Text(
                        stringResource(R.string.detected_movement_transfer_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (group.isPossibleDuplicate) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onKeepSeparate,
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.detected_movement_keep_separate)) }
                        Button(
                            onClick = onConfirmDuplicate,
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.detected_movement_confirm_same)) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.detected_movement_discard)) }
                        Button(
                            onClick = onBook,
                            enabled = !busy && movement.direction != "transfer",
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.detected_movement_add)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementReviewStatus(group: DetectedMovementGroup) {
    val registered = group.root.status == "APPROVED"
    Surface(
        color = if (registered) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (registered) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = stringResource(
                if (registered) {
                    R.string.detected_movement_status_registered_pending_sync
                } else {
                    R.string.detected_movement_status_pending_review
                }
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun WarningBlock(title: String, body: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                body?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun EvidenceRow(evidence: DetectedMovementEntity) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (evidence.source.isEmailEvidence()) Icons.Outlined.Email else Icons.Outlined.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = sourceColor(evidence.source)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(evidence.senderOrApp.ifBlank { sourceLabel(evidence.source) }, style = MaterialTheme.typography.bodySmall)
            evidence.title.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(formatDate(evidence.occurredAt), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SourceBadge(source: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = sourceColor(source).copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (source.isEmailEvidence()) Icons.Outlined.Email else Icons.Outlined.AccountBalance,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = sourceColor(source)
            )
            Text(sourceLabel(source), color = sourceColor(source), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun sourceLabel(source: String): String = when (source) {
    "EMAIL_GMAIL" -> stringResource(R.string.detected_source_gmail)
    "EMAIL_MICROSOFT" -> stringResource(R.string.detected_source_microsoft)
    "BANK_NOTIFICATION", "NOTIFICATION" -> stringResource(R.string.detected_source_bank)
    else -> stringResource(R.string.detected_source_email)
}

private fun sourceColor(source: String): Color = when (source) {
    "EMAIL_GMAIL" -> Color(0xFFC5221F)
    "EMAIL_MICROSOFT" -> Color(0xFF0078D4)
    else -> Color(0xFF1B873F)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingDialog(
    group: DetectedMovementGroup,
    state: DetectedMovementsUiState,
    onDismiss: () -> Unit,
    onConfirm: (DetectedMovementBookingRequest) -> Unit
) {
    val root = group.root
    var direction by remember(root.id) { mutableStateOf(root.direction) }
    val compatibleAccounts = state.accounts.filter { amountForAccount(root, it, state.currentUsdDopRateMicros) != null }
    var accountId by remember(root.id, compatibleAccounts, state.currentUsdDopRateMicros) {
        mutableStateOf(preselectedAccountId(root, compatibleAccounts, state.currentUsdDopRateMicros))
    }
    val selectedAccount = compatibleAccounts.firstOrNull { it.id == accountId }
    val automaticAmount = selectedAccount?.let { amountForAccount(root, it, state.currentUsdDopRateMicros) }
    var amountText by remember(root.id, accountId) {
        mutableStateOf(automaticAmount?.let(::formatEditableAmount).orEmpty())
    }
    val amountMinor = parseAmount(amountText)
    val usesCurrentUsdRate = selectedAccount != null &&
        root.currency.equals("USD", ignoreCase = true) &&
        selectedAccount?.currency.equals("DOP", ignoreCase = true) &&
        state.currentUsdDopRateMicros != null
    val usesHistoricalEstimate = selectedAccount != null &&
        !root.currency.equals(selectedAccount.currency, ignoreCase = true) &&
        root.baseCurrency.equals(selectedAccount.currency, ignoreCase = true)
    val usesEstimatedAmount = usesCurrentUsdRate || usesHistoricalEstimate
    var categoryId by remember(root.id, direction, root.suggestedCategoryId, state.categories) {
        mutableStateOf(
            root.suggestedCategoryId?.takeIf { suggestion ->
                state.categories.any { it.id == suggestion && it.accepts(direction) }
            }.orEmpty()
        )
    }
    var selectedDate by remember(root.id) { mutableLongStateOf(root.occurredAt) }
    var rememberCategory by remember(root.id, categoryId) { mutableStateOf(false) }
    var showDatePicker by remember(root.id) { mutableStateOf(false) }

    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = utcDay(selectedDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = picker.selectedDateMillis
                            ?.let(::pickerDateToLocalMillis)
                            ?: selectedDate
                        showDatePicker = false
                    },
                    enabled = picker.selectedDateMillis != null
                ) { Text(stringResource(R.string.common_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) { DatePicker(picker) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detected_movement_review_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.detected_movement_direction), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == "expense",
                        onClick = { direction = "expense"; categoryId = "" },
                        label = { Text(stringResource(R.string.quick_expense)) }
                    )
                    FilterChip(
                        selected = direction == "income",
                        onClick = { direction = "income"; categoryId = "" },
                        label = { Text(stringResource(R.string.quick_income)) }
                    )
                }
                Text(stringResource(R.string.detected_movement_account), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(compatibleAccounts, key = { it.id }) { account ->
                        FilterChip(
                            selected = account.id == accountId,
                            onClick = { accountId = account.id },
                            label = { Text("${account.name} (${account.currency})") }
                        )
                    }
                }
                if (compatibleAccounts.isEmpty()) {
                    Text(
                        stringResource(R.string.detected_movement_no_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                selectedAccount?.let { account ->
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(stringResource(R.string.detected_movement_amount, account.currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = amountMinor == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (amountMinor == null) {
                        Text(
                            stringResource(R.string.detected_movement_amount_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (usesCurrentUsdRate) {
                    Text(
                        stringResource(
                            R.string.detected_movement_current_rate,
                            formatRate(state.currentUsdDopRateMicros)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (usesEstimatedAmount) {
                    Text(
                        stringResource(
                            R.string.detected_movement_amount_estimated,
                            formatAmount(root.amountMinor, root.currency, root.direction)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(stringResource(R.string.detected_movement_category), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories.filter { it.accepts(direction) }, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberCategory, onCheckedChange = { rememberCategory = it })
                    Text(stringResource(R.string.detected_movement_remember_category))
                }
                Text(stringResource(R.string.detected_movement_date), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(selectedDate))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        DetectedMovementBookingRequest(
                            canonicalId = root.id,
                            accountId = accountId,
                            categoryId = categoryId,
                            amountMinor = requireNotNull(amountMinor),
                            direction = direction,
                            occurredAt = selectedDate,
                            rememberCategory = rememberCategory
                        )
                    )
                },
                enabled = accountId.isNotBlank() && categoryId.isNotBlank() && amountMinor != null
            ) { Text(stringResource(R.string.detected_movement_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

private fun preselectedAccountId(
    movement: DetectedMovementEntity,
    accounts: List<Account>,
    usdDopRateMicros: Long?
): String {
    val byCard = movement.last4Digits?.let { last4 -> accounts.filter { it.cardLastFour == last4 } }.orEmpty()
    val dopAccounts = accounts.filter {
        it.currency.equals("DOP", ignoreCase = true) && amountForAccount(movement, it, usdDopRateMicros) != null
    }
    return when {
        byCard.size == 1 -> byCard.single().id
        movement.currency.equals("USD", ignoreCase = true) && dopAccounts.isNotEmpty() -> dopAccounts.first().id
        accounts.size == 1 -> accounts.single().id
        else -> ""
    }
}

private fun com.bsolutions.wallet.domain.model.Category.accepts(direction: String): Boolean {
    val wanted = if (direction == "income") "INCOME" else "EXPENSE"
    return type == wanted || type == "BOTH"
}

private fun formatEditableAmount(amountMinor: Long): String = BigDecimal.valueOf(amountMinor, 2)
    .setScale(2, RoundingMode.UNNECESSARY)
    .toPlainString()

private fun parseAmount(value: String): Long? = runCatching {
    BigDecimal(value.trim().replace(',', '.')).movePointRight(2).longValueExact().takeIf { it > 0L }
}.getOrNull()

private fun formatRate(rateMicros: Long?): String = rateMicros?.let {
    BigDecimal.valueOf(it, 6).stripTrailingZeros().toPlainString()
} ?: "—"

private fun formatAmount(amountMinor: Long?, currencyCode: String?, direction: String): String {
    if (amountMinor == null || currencyCode.isNullOrBlank()) return "—"
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-DO"))
    runCatching { formatter.currency = Currency.getInstance(currencyCode) }
    val value = formatter.format(BigDecimal.valueOf(amountMinor, 2))
    return if (direction == "income") "+$value" else "−$value"
}

private fun formatDate(epochMillis: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
    Locale.forLanguageTag("es-DO")
).format(Date(epochMillis))

private fun utcDay(epochMillis: Long): Long = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()

private fun pickerDateToLocalMillis(epochMillis: Long): Long = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneOffset.UTC)
    .toLocalDate()
    .atStartOfDay(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()
