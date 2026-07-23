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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        var categoryId by remember(candidate.id, state.categories) {
            mutableStateOf(
                state.categories.firstOrNull {
                    it.name.equals(candidate.categorySuggestion, ignoreCase = true)
                }?.id.orEmpty()
            )
        }
        AlertDialog(
            onDismissRequest = { classifyCandidate = null },
            title = { Text(stringResource(R.string.email_candidate_classify_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.email_candidate_category_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.categories, key = { it.id }) { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { categoryId = category.id },
                                label = { Text(category.name) }
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.email_candidate_classify_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        classifyCandidate = null
                        val categoryName = state.categories.firstOrNull { it.id == categoryId }?.name
                            ?: return@TextButton
                        viewModel.classify(candidate.id, categoryName)
                    },
                    enabled = categoryId.isNotBlank()
                ) { Text(stringResource(R.string.email_candidate_classify)) }
            },
            dismissButton = {
                TextButton(onClick = { classifyCandidate = null }) {
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
                        enabled = state.phase != EmailConnectionsPhase.LOADING
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
                        actionsEnabled = state.actionProvider == null,
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
                        actionsEnabled = state.actionProvider == null,
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
                    EmailProvider.entries.forEach { provider ->
                        val providerCandidates = state.candidatesByProvider[provider].orEmpty()
                        if (providerCandidates.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(
                                        R.string.email_candidates_provider_title,
                                        providerName(provider)
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            items(providerCandidates, key = { it.id }) { candidate ->
                                CandidateCard(
                                    candidate = candidate,
                                    isReviewing = state.reviewCandidateId == candidate.id,
                                    onClassify = { classifyCandidate = candidate },
                                    onDismiss = { dismissCandidate = candidate }
                                )
                            }
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
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Text(
                        text = stringResource(
                            R.string.email_candidate_received_at,
                            formatEmailCandidateDate(candidate.occurredAt)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        }
    }
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
