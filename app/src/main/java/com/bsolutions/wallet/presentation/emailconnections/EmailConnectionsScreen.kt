package com.bsolutions.wallet.presentation.emailconnections

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.net.toUri

import com.bsolutions.wallet.R
import com.bsolutions.wallet.data.repository.EmailConnection
import com.bsolutions.wallet.data.repository.EmailConnectionStatus
import com.bsolutions.wallet.data.repository.EmailCandidate
import com.bsolutions.wallet.data.repository.EmailProvider
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Frecuencias que se ofrecen al marcar un correo como movimiento fijo. */
private val recurringFrequencies = listOf(
    "SEMIMONTHLY" to R.string.email_candidate_freq_semimonthly,
    "MONTHLY" to R.string.email_candidate_freq_monthly,
    "WEEKLY" to R.string.email_candidate_freq_weekly,
    "EVERY_15_DAYS" to R.string.email_candidate_freq_every15,
    "EVERY_30_DAYS" to R.string.email_candidate_freq_every30,
    "YEARLY" to R.string.email_candidate_freq_yearly
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailConnectionsScreen(
    onNavigateBack: () -> Unit,
    oauthReturnNonce: Long = 0L,
    viewModel: EmailConnectionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var disconnectCandidate by remember { mutableStateOf<EmailProvider?>(null) }
    var classifyCandidate by remember { mutableStateOf<EmailCandidate?>(null) }
    var dismissCandidate by remember { mutableStateOf<EmailCandidate?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showDateFilter by remember { mutableStateOf(false) }
    var duplicatePair by remember { mutableStateOf<Pair<EmailCandidate, EmailCandidate>?>(null) }

    LaunchedEffect(oauthReturnNonce) {
        if (oauthReturnNonce > 0L) viewModel.onAuthorizationReturn()
    }

    LaunchedEffect(state.authorizationUrl) {
        state.authorizationUrl?.let { rawUrl ->
            viewModel.consumeAuthorizationUrl()
            val uri = runCatching { rawUrl.toUri() }.getOrNull()
            if (uri != null && uri.scheme in setOf("https", "http")) {
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
            }
        }
    }

    disconnectCandidate?.let { provider ->
        val providerName = providerName(provider)
        AlertDialog(
            onDismissRequest = { disconnectCandidate = null },
            title = { Text(stringResource(R.string.email_disconnect_title)) },
            text = { Text(stringResource(R.string.email_disconnect_confirm, providerName)) },
            confirmButton = {
                TextButton(onClick = {
                    disconnectCandidate = null
                    viewModel.disconnect(provider)
                }) {
                    Text(stringResource(R.string.email_disconnect_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnectCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    classifyCandidate?.let { candidate ->
        val bookedTransaction = state.bookedCandidates[candidate.id]
        var accountId by remember(candidate.id, state.accounts, bookedTransaction) {
            mutableStateOf(
                bookedTransaction?.accountId ?: preselectedAccountId(candidate, state.accounts)
            )
        }
        var categoryId by remember(candidate.id, state.categories, bookedTransaction) {
            mutableStateOf(
                bookedTransaction?.categoryId ?: state.categories.firstOrNull {
                    it.name.equals(candidate.categorySuggestion, ignoreCase = true)
                }?.id.orEmpty()
            )
        }
        var selectedDateMillis by remember(candidate.id, bookedTransaction) {
            mutableStateOf<Long?>(null)
        }
        val selectedAccount = state.accounts.firstOrNull { it.id == accountId }
        val automaticAmount = selectedAccount?.let { candidateAmountForAccount(candidate, it) }
        // Se precarga con el importe calculado: casi siempre sirve, y cuando la
        // conversion no coincide con lo que cobro el banco basta con corregirlo.
        var amountText by remember(candidate.id, accountId, bookedTransaction) {
            mutableStateOf(automaticAmount?.let { formatAmountForEditing(it) }.orEmpty())
        }
        val editedAmount = parseEditedAmountMinor(amountText)
        var chargeToDebtId by remember(candidate.id) { mutableStateOf<String?>(null) }
        // Corregible porque la detección se equivoca: un aviso de nómina que dice "pago"
        // se leía como gasto, y una vez creado el movimiento el tipo ya no se puede cambiar.
        var direction by remember(candidate.id) { mutableStateOf(candidate.direction) }
        var recurringFrequency by remember(candidate.id) { mutableStateOf<String?>(null) }
        var showDatePicker by remember(candidate.id) { mutableStateOf(false) }
        if (showDatePicker && bookedTransaction == null) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDateMillis
                    ?: candidateDatePickerInitialMillis(candidate.occurredAt)
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedDateMillis = datePickerState.selectedDateMillis
                            showDatePicker = false
                        },
                        enabled = datePickerState.selectedDateMillis != null
                    ) {
                        Text(stringResource(R.string.common_accept))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        AlertDialog(
            onDismissRequest = { classifyCandidate = null },
            title = { Text(stringResource(R.string.email_candidate_classify_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (bookedTransaction == null) {
                        Text(
                            text = stringResource(R.string.email_candidate_direction_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = direction == "expense",
                                onClick = { direction = "expense"; categoryId = ""; recurringFrequency = null },
                                label = { Text(stringResource(R.string.quick_expense)) }
                            )
                            FilterChip(
                                selected = direction == "income",
                                onClick = { direction = "income"; categoryId = "" },
                                label = { Text(stringResource(R.string.quick_income)) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.email_candidate_account_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.accounts, key = { it.id }) { account ->
                            FilterChip(
                                selected = accountId == account.id,
                                onClick = { accountId = account.id },
                                enabled = bookedTransaction == null && candidateAmountForAccount(candidate, account) != null,
                                label = { Text("${account.name} (${account.currency})") }
                            )
                        }
                    }
                    if (state.accounts.none { candidateAmountForAccount(candidate, it) != null }) {
                        Text(
                            stringResource(R.string.email_candidate_no_compatible_account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (bookedTransaction == null && automaticAmount != null) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.email_candidate_amount_label,
                                        selectedAccount?.currency.orEmpty()
                                    )
                                )
                            },
                            singleLine = true,
                            isError = editedAmount == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = when {
                                editedAmount == null ->
                                    stringResource(R.string.email_candidate_amount_invalid)
                                editedAmount != automaticAmount ->
                                    stringResource(R.string.email_candidate_amount_edited)
                                else -> stringResource(R.string.email_candidate_amount_help)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (editedAmount == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // Si el cargo es por algo que le prestaste a alguien, se le suma a esa
                    // deuda en vez de contarse como consumo propio.
                    if (bookedTransaction == null && state.openReceivables.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.email_candidate_debt_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.openReceivables, key = { it.id }) { debt ->
                                FilterChip(
                                    selected = chargeToDebtId == debt.id,
                                    onClick = {
                                        chargeToDebtId = if (chargeToDebtId == debt.id) null else debt.id
                                    },
                                    label = { Text(debt.name) }
                                )
                            }
                        }
                        if (chargeToDebtId != null) {
                            Text(
                                text = stringResource(R.string.email_candidate_debt_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.email_candidate_category_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Solo las del tipo elegido: un ingreso no debe poder etiquetarse
                        // con una categoría de gasto ni al revés.
                        val wanted = if (direction == "income") "INCOME" else "EXPENSE"
                        val offered = state.categories.filter { it.type == wanted || it.type == "BOTH" }
                        items(offered, key = { it.id }) { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { categoryId = category.id },
                                enabled = bookedTransaction == null,
                                label = { Text(category.name) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.email_candidate_transaction_date_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = bookedTransaction == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                bookedTransaction != null -> stringResource(
                                    R.string.email_candidate_booked_date,
                                    formatTransactionDate(bookedTransaction.date)
                                )
                                selectedDateMillis != null -> stringResource(
                                    R.string.email_candidate_selected_date,
                                    formatDatePickerSelection(checkNotNull(selectedDateMillis))
                                )
                                else -> stringResource(
                                    R.string.email_candidate_use_email_date,
                                    formatEmailCandidateDate(candidate.occurredAt)
                                )
                            }
                        )
                    }
                    if (selectedDateMillis != null && bookedTransaction == null) {
                        TextButton(
                            onClick = { selectedDateMillis = null },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.email_candidate_reset_date))
                        }
                    }
                    // Un sueldo llega siempre: dejarlo anotado desde el propio aviso evita
                    // ir a crearlo aparte repitiendo monto, cuenta y categoría.
                    // Solo para ingresos: un gasto fijo se lleva desde Pagos planificados.
                    if (bookedTransaction == null && direction == "income") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = recurringFrequency != null,
                                onCheckedChange = { recurringFrequency = if (it) "SEMIMONTHLY" else null }
                            )
                            Text(
                                text = stringResource(R.string.email_candidate_recurring),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (recurringFrequency != null) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recurringFrequencies) { (value, label) ->
                                    FilterChip(
                                        selected = recurringFrequency == value,
                                        onClick = { recurringFrequency = value },
                                        label = { Text(stringResource(label)) }
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(
                            if (bookedTransaction == null) R.string.email_candidate_classify_help
                            else R.string.email_candidate_pending_confirmation_help
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        classifyCandidate = null
                        viewModel.classify(
                            candidate.id,
                            accountId,
                            categoryId,
                            selectedDateMillis,
                            editedAmount,
                            chargeToDebtId,
                            direction,
                            recurringFrequency
                        )
                    },
                    enabled = accountId.isNotBlank() &&
                        (categoryId.isNotBlank() || bookedTransaction != null || chargeToDebtId != null) &&
                        // Un importe escrito a medias no debe poder guardarse.
                        (bookedTransaction != null || automaticAmount == null || editedAmount != null)
                ) { Text(stringResource(R.string.email_candidate_add_movement)) }
            },
            dismissButton = {
                TextButton(onClick = { classifyCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    duplicatePair?.let { (candidate, original) ->
        AlertDialog(
            onDismissRequest = { duplicatePair = null },
            title = { Text(stringResource(R.string.email_candidate_mark_duplicate)) },
            text = {
                Text(
                    stringResource(
                        R.string.email_candidate_mark_duplicate_confirm,
                        formatCandidateAmount(candidate.amount, candidate.currency, candidate.direction),
                        formatCandidateAmount(original.amount, original.currency, original.direction)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markAsDuplicate(candidate.id, original.id)
                    duplicatePair = null
                }) {
                    Text(stringResource(R.string.email_candidate_mark_duplicate))
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicatePair = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDateFilter) {
        val filterState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDateFilter = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // El selector devuelve el día en UTC; se lee tal cual porque solo
                        // interesa la fecha, no la hora.
                        viewModel.selectDate(
                            filterState.selectedDateMillis?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            }
                        )
                        showDateFilter = false
                    },
                    enabled = filterState.selectedDateMillis != null
                ) {
                    Text(stringResource(R.string.common_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.selectDate(null)
                    showDateFilter = false
                }) {
                    Text(stringResource(R.string.email_candidates_all_dates))
                }
            }
        ) {
            DatePicker(state = filterState)
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.email_candidates_clear_all)) },
            text = { Text(stringResource(R.string.email_candidates_clear_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.removeAll()
                }) {
                    Text(stringResource(R.string.email_candidates_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    dismissCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { dismissCandidate = null },
            title = { Text(stringResource(R.string.email_candidate_dismiss_title)) },
            text = { Text(stringResource(R.string.email_candidate_dismiss_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    dismissCandidate = null
                    viewModel.dismiss(candidate.id)
                }) {
                    Text(stringResource(R.string.email_candidate_not_movement), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.email_connections_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = state.phase != EmailConnectionsPhase.LOADING &&
                            state.actionProvider == null && state.reviewCandidateId == null
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.email_refresh))
                    }
                },
                colors = walletTopBarColors()
            )
        }
    ) { innerPadding ->
        when (state.phase) {
            EmailConnectionsPhase.LOADING -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            EmailConnectionsPhase.ERROR -> ErrorState(
                message = state.message.orEmpty(),
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )

            EmailConnectionsPhase.EMPTY,
            EmailConnectionsPhase.CONTENT -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.email_connections_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.message?.let { message ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                if (state.phase == EmailConnectionsPhase.EMPTY) {
                    item {
                        Text(
                            text = stringResource(R.string.email_connections_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    ProviderCard(
                        provider = EmailProvider.GMAIL,
                        connection = state.connections.firstOrNull { it.provider == EmailProvider.GMAIL },
                        isWorking = state.actionProvider == EmailProvider.GMAIL,
                        actionsEnabled = state.actionProvider == null && state.reviewCandidateId == null,
                        onConnect = { viewModel.connect(EmailProvider.GMAIL) },
                        onSync = { viewModel.sync(EmailProvider.GMAIL) },
                        onDisconnect = { disconnectCandidate = EmailProvider.GMAIL }
                    )
                }
                item {
                    ProviderCard(
                        provider = EmailProvider.MICROSOFT,
                        connection = state.connections.firstOrNull { it.provider == EmailProvider.MICROSOFT },
                        isWorking = state.actionProvider == EmailProvider.MICROSOFT,
                        actionsEnabled = state.actionProvider == null && state.reviewCandidateId == null,
                        onConnect = { viewModel.connect(EmailProvider.MICROSOFT) },
                        onSync = { viewModel.sync(EmailProvider.MICROSOFT) },
                        onDisconnect = { disconnectCandidate = EmailProvider.MICROSOFT }
                    )
                }
                state.syncResult?.let { result ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F6EC))) {
                            Text(
                                text = stringResource(
                                    R.string.email_sync_summary,
                                    result.messagesDiscovered,
                                    result.messagesCreated,
                                    result.candidatesCreated,
                                    result.conversionsBackfilled
                                ),
                                modifier = Modifier.padding(12.dp),
                                color = Color(0xFF165C2D),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (state.candidates.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.email_candidates_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { confirmClearAll = true },
                                enabled = state.actionProvider == null && state.reviewCandidateId == null
                            ) {
                                Text(stringResource(R.string.email_candidates_clear_all))
                            }
                        }
                    }
                    // Filtro por día: un gasto se recuerda por cuándo pasó, no por
                    // el proveedor de correo que lo trajo.
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { showDateFilter = true }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.selectedDate?.let { formatCandidateDay(it) }
                                        ?: stringResource(R.string.email_candidates_all_dates)
                                )
                            }
                            if (state.selectedDate != null) {
                                TextButton(onClick = { viewModel.selectDate(null) }) {
                                    Text(stringResource(R.string.email_candidates_all_dates))
                                }
                            }
                        }
                    }

                    val byDate = state.candidatesByDate
                    if (byDate.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.email_candidates_none_that_day),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    byDate.forEach { (day, dayCandidates) ->
                        item {
                            Text(
                                text = formatCandidateDay(day),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(dayCandidates, key = { it.id }) { candidate ->
                            CandidateCard(
                                candidate = candidate,
                                isReviewing = state.reviewCandidateId != null || state.actionProvider != null,
                                onClassify = { classifyCandidate = candidate },
                                onDismiss = { dismissCandidate = candidate },
                                onRemove = { viewModel.remove(candidate.id) },
                                // Solo se ofrece si hay otro candidato con el que
                                // emparejar: sin pareja, marcar duplicado no dice nada.
                                onMarkDuplicate = state.duplicateCandidateFor(candidate)
                                    ?.let { original -> { duplicatePair = candidate to original } }
                            )
                        }
                    }
                } else if (state.syncResult != null) {
                    item {
                        Text(
                            text = stringResource(R.string.email_candidates_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.email_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: EmailProvider,
    connection: EmailConnection?,
    isWorking: Boolean,
    actionsEnabled: Boolean,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit
) {
    val configured = connection?.configurationReady != false
    val status = connection?.status ?: EmailConnectionStatus.DISCONNECTED
    val connected = status == EmailConnectionStatus.CONNECTED
    val statusInfo = when {
        !configured -> Triple(R.string.email_status_configuration, Icons.Default.SettingsSuggest, MaterialTheme.colorScheme.tertiary)
        connected -> Triple(R.string.email_status_connected, Icons.Default.CheckCircle, Color(0xFF1B873F))
        status == EmailConnectionStatus.ERROR -> Triple(R.string.email_status_error, Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error)
        else -> Triple(R.string.email_status_disconnected, Icons.Default.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(
                        if (provider == EmailProvider.GMAIL) Color(0xFFFFE9E7) else Color(0xFFE4F2FF),
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = if (provider == EmailProvider.GMAIL) Color(0xFFC5221F) else Color(0xFF0078D4)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(providerName(provider), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        connection?.email ?: stringResource(R.string.email_no_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusLabel(stringResource(statusInfo.first), statusInfo.second, statusInfo.third)
            }

            if (!configured) {
                Text(
                    stringResource(R.string.email_configuration_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (connected) {
                Button(
                    onClick = onSync,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isWorking) stringResource(R.string.email_syncing) else stringResource(R.string.email_sync))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onConnect,
                        enabled = actionsEnabled && configured,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.email_reconnect))
                    }
                    TextButton(
                        onClick = onDisconnect,
                        enabled = actionsEnabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.email_disconnect_action), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = actionsEnabled && configured,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (!configured) stringResource(R.string.email_not_available)
                        else if (status == EmailConnectionStatus.ERROR) stringResource(R.string.email_reconnect)
                        else stringResource(R.string.email_connect)
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: EmailCandidate,
    isReviewing: Boolean,
    onClassify: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onMarkDuplicate: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val providerAccent = providerAccent(candidate.provider)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, providerAccent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProviderBadge(candidate.provider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.email_candidate_received_at,
                            formatEmailCandidateDate(candidate.occurredAt)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Quitar de la lista sin abrir diálogo: es la salida rápida del correo.
                    IconButton(
                        onClick = onRemove,
                        enabled = !isReviewing,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.email_candidate_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.merchant ?: stringResource(R.string.email_candidate_unknown_merchant),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    candidate.subject?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    candidate.cardLastFour?.takeIf { it.isNotBlank() }?.let { last4 ->
                        Text(
                            text = stringResource(R.string.email_candidate_card, last4),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = formatCandidateAmount(candidate.amount, candidate.currency, candidate.direction),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (candidate.direction == "expense") MaterialTheme.colorScheme.error else Color(0xFF1B873F)
                )
            }
            candidate.convertedAmount?.let { convertedAmount ->
                Text(
                    text = stringResource(
                        R.string.email_candidate_converted,
                        formatCandidateAmount(convertedAmount, candidate.convertedCurrency ?: "DOP", candidate.direction)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            candidate.exchangeRateMicros?.let { rateMicros ->
                Text(
                    text = stringResource(
                        R.string.email_candidate_rate,
                        formatExchangeRate(rateMicros),
                        formatEmailCandidateDate(candidate.exchangeRateAt ?: candidate.occurredAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/fawazahmed0/exchange-api")
                    }
                )
            }
            if (candidate.currency == "USD" && candidate.conversionStatus == "unavailable") {
                Text(
                    text = stringResource(R.string.email_candidate_conversion_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = candidate.categorySuggestion ?: stringResource(R.string.email_candidate_uncategorized),
                    style = MaterialTheme.typography.labelMedium
                )
                Text("·", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = stringResource(R.string.email_candidate_confidence, candidate.confidence),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = stringResource(R.string.email_candidate_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClassify,
                    enabled = !isReviewing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.email_candidate_classify))
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isReviewing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.email_candidate_not_movement),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            onMarkDuplicate?.let { markDuplicate ->
                TextButton(onClick = markDuplicate, enabled = !isReviewing) {
                    Text(stringResource(R.string.email_candidate_mark_duplicate))
                }
            }
        }
    }
}

@Composable
private fun ProviderBadge(provider: EmailProvider) {
    val accent = providerAccent(provider)
    Surface(
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = providerName(provider),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun providerAccent(provider: EmailProvider): Color = when (provider) {
    EmailProvider.GMAIL -> Color(0xFFC5221F)
    EmailProvider.MICROSOFT -> Color(0xFF0078D4)
}

private fun formatMinorAmount(amount: Long, currency: String): String {
    val formatted = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(amount, 2).abs())
    val symbol = when (currency) {
        "DOP" -> "RD$"
        "USD" -> "US$"
        "EUR" -> "€"
        else -> "$currency "
    }

    return (if (amount < 0) "-" else "+") + symbol + formatted
}

private fun formatCandidateAmount(amount: Long, currency: String, direction: String): String {
    val absoluteAmount = if (amount == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amount)
    return formatMinorAmount(if (direction == "expense") -absoluteAmount else absoluteAmount, currency)
}

private fun formatExchangeRate(rateMicros: Long): String =
    BigDecimal.valueOf(rateMicros, 6).stripTrailingZeros().toPlainString()

internal fun formatEmailCandidateDate(
    value: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
        .withZone(zoneId)
        .format(Instant.parse(value))
}.getOrDefault("—")

internal fun candidateDatePickerInitialMillis(
    occurredAt: String,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long? = runCatching {
    Instant.parse(occurredAt)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}.getOrNull()

internal fun formatDatePickerSelection(value: Long): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

private fun formatTransactionDate(
    value: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): String = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
    .withZone(zoneId)
    .format(Instant.ofEpochMilli(value))

@Composable
private fun StatusLabel(text: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.email_retry)) }
    }
}

@Composable
private fun providerName(provider: EmailProvider): String = when (provider) {
    EmailProvider.GMAIL -> stringResource(R.string.email_provider_gmail)
    EmailProvider.MICROSOFT -> stringResource(R.string.email_provider_microsoft)
}

/**
 * Encabezado de día en lenguaje corriente: "Hoy" y "Ayer" son como uno piensa las
 * fechas recientes; el resto lleva día, mes y año.
 */
@Composable
private fun formatCandidateDay(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.email_candidates_today)
        today.minusDays(1) -> stringResource(R.string.email_candidates_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern("d 'de' MMMM yyyy"))
    }
}
