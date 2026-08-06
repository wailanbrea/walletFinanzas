package com.bsolutions.wallet.presentation.banknotifications

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.FilterChip
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.notifications.NotificationSourceKind
import com.bsolutions.wallet.core.notifications.notificationSourceKind
import com.bsolutions.wallet.data.local.entity.NotificationSourceEntity
import com.bsolutions.wallet.data.local.entity.RawBankNoticeEntity
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankNotificationsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BankNotificationsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var hasListenerAccess by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // Null es "todas": el filtro ordena la lista, no decide nada por ti.
    var sourceFilter by remember { mutableStateOf<NotificationSourceKind?>(null) }

    fun refreshAccess() {
        hasListenerAccess = context.packageName in
            NotificationManagerCompat.getEnabledListenerPackages(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        refreshAccess()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.buildAnonymizedExport()
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    Toast.makeText(context, R.string.bank_notices_export_success, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, R.string.bank_notices_export_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.bank_notices_clear_title)) },
            text = { Text(stringResource(R.string.bank_notices_clear_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearNotices()
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bank_notices_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = walletTopBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { CaptureStatusCard(hasListenerAccess, state.sources.count { it.isEnabled }, state.notices.size) }
            item {
                PrivacyCard()
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.bank_notices_access_title), fontWeight = FontWeight.Bold)
                        Text(
                            if (hasListenerAccess) stringResource(R.string.bank_notices_access_granted)
                            else stringResource(R.string.bank_notices_access_missing),
                            color = if (hasListenerAccess) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.bank_notices_open_settings))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.bank_notices_sources_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.bank_notices_sources_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                // La lista trae todo lo que ha notificado alguna vez: el clima, la tienda
                // de apps, los mensajes. Encontrar el banco ahi dentro era buscar entre
                // veinte, y es justo donde hay que fijarse para autorizar.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sourceFilter == null,
                        onClick = { sourceFilter = null },
                        label = { Text(stringResource(R.string.bank_notices_filter_all)) }
                    )
                    FilterChip(
                        selected = sourceFilter == NotificationSourceKind.BANK,
                        onClick = { sourceFilter = NotificationSourceKind.BANK },
                        label = { Text(stringResource(R.string.bank_notices_filter_banks)) }
                    )
                    FilterChip(
                        selected = sourceFilter == NotificationSourceKind.EMAIL,
                        onClick = { sourceFilter = NotificationSourceKind.EMAIL },
                        label = { Text(stringResource(R.string.bank_notices_filter_email)) }
                    )
                }
            }
            val shownSources = state.sources.filter { source ->
                sourceFilter == null || notificationSourceKind(source.packageName) == sourceFilter
            }
            if (shownSources.isEmpty()) {
                item {
                    EmptyCard(
                        stringResource(
                            if (state.sources.isEmpty()) R.string.bank_notices_sources_empty
                            else R.string.bank_notices_sources_empty_filtered
                        )
                    )
                }
            } else {
                items(shownSources, key = { "${it.ownerId}:${it.packageName}" }) { source ->
                    SourceRow(source, viewModel::setSourceEnabled)
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.bank_notices_captures_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.bank_notices_retention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { exportLauncher.launch("wallet_notificaciones_anonimas.json") },
                        enabled = state.notices.isNotEmpty()
                    ) { Icon(Icons.Default.UploadFile, stringResource(R.string.bank_notices_export)) }
                    IconButton(onClick = { confirmClear = true }, enabled = state.notices.isNotEmpty()) {
                        Icon(Icons.Default.DeleteOutline, stringResource(R.string.bank_notices_clear))
                    }
                }
            }
            if (state.notices.isEmpty()) {
                item { EmptyCard(stringResource(R.string.bank_notices_captures_empty)) }
            } else {
                items(state.notices, key = { "${it.ownerId}:${it.id}" }) { notice ->
                    NoticeRow(notice)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CaptureStatusCard(hasAccess: Boolean, enabledSources: Int, captures: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusItem(Icons.Default.Security, if (hasAccess) stringResource(R.string.bank_notices_status_access_ok) else stringResource(R.string.bank_notices_status_access_pending))
            StatusItem(Icons.Default.Notifications, pluralStringResource(R.plurals.bank_notices_status_apps, enabledSources, enabledSources))
            StatusItem(Icons.Default.CheckCircle, pluralStringResource(R.plurals.bank_notices_status_captures, captures, captures))
        }
    }
}

@Composable
private fun StatusItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4D6)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            stringResource(R.string.bank_notices_privacy),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5F4500)
        )
    }
}

@Composable
private fun SourceRow(source: NotificationSourceEntity, onToggle: (String, Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(source.displayName, fontWeight = FontWeight.SemiBold)
                Text(source.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (source.observedCount == 0) {
                        stringResource(R.string.bank_notices_installed_suggestion)
                    } else {
                        pluralStringResource(
                            R.plurals.bank_notices_seen_count,
                            source.observedCount,
                            source.observedCount
                        )
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Switch(checked = source.isEnabled, onCheckedChange = { onToggle(source.packageName, it) })
        }
    }
}

@Composable
private fun NoticeRow(notice: RawBankNoticeEntity) {
    val preview = notice.bigText.ifBlank { notice.text }.ifBlank { notice.title }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(notice.appLabel, fontWeight = FontWeight.Bold)
            if (notice.title.isNotBlank()) Text(notice.title, fontWeight = FontWeight.Medium)
            Text(preview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(notice.postTime)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text(message)
    }
}
