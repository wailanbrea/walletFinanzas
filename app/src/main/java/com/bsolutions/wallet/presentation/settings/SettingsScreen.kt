package com.bsolutions.wallet.presentation.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.bsolutions.wallet.R
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import com.bsolutions.wallet.presentation.auth.AuthViewModel
import com.bsolutions.wallet.presentation.profile.ProfileViewModel
import com.bsolutions.wallet.presentation.sync.SyncViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToCategoryRules: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToSyncSettings: () -> Unit = {},
    onNavigateToImportCsv: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    profileViewModel: ProfileViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val profile by profileViewModel.profile.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val syncState by syncViewModel.uiState.collectAsState()
    val pendingCount by syncViewModel.pendingCount.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val exportSuccessMessage = stringResource(R.string.settings_export_ok)
    val exportErrorMessage = stringResource(R.string.settings_export_error, "")
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val csv = exportViewModel.buildCsv()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, exportSuccessMessage, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "$exportErrorMessage ${e.message.orEmpty()}".trim(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm, authState.userEmail)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    authViewModel.logout()
                    onLogout()
                }) { Text(stringResource(R.string.settings_logout), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = walletTopBarColors()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Profile summary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.userName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (authState.isLoggedIn) authState.userEmail
                                else profile.email.ifBlank { stringResource(R.string.settings_no_session) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onNavigateToProfile) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.settings_edit_profile),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsItem(
                    title = stringResource(R.string.accounts_title),
                    subtitle = stringResource(R.string.settings_accounts_sub),
                    icon = Icons.Default.AccountBalance,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onNavigateToAccounts
                )
                SettingsItem(
                    title = stringResource(R.string.categories_title),
                    subtitle = stringResource(R.string.settings_categories_sub),
                    icon = Icons.Default.Category,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onNavigateToCategories
                )
                SettingsItem(
                    title = stringResource(R.string.category_rules_title),
                    subtitle = stringResource(R.string.category_rules_sub),
                    icon = Icons.Default.AutoAwesome,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onNavigateToCategoryRules
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingsItem(
                    title = stringResource(R.string.security_title),
                    subtitle = stringResource(R.string.settings_security_sub),
                    icon = Icons.Default.Fingerprint,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onNavigateToSecurity
                )
                SettingsItem(
                    title = stringResource(R.string.settings_sync),
                    subtitle = stringResource(R.string.settings_sync_sub),
                    icon = Icons.Default.Sync,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onNavigateToSyncSettings
                )
                // Sincronización con el backend (push/pull offline-first)
                SettingsItem(
                    title = stringResource(R.string.settings_cloud_sync),
                    subtitle = when {
                        syncState.isSyncing -> stringResource(R.string.settings_cloud_sync_running)
                        syncState.lastResult != null -> syncState.lastResult!!
                        pendingCount > 0 -> pluralStringResource(
                            R.plurals.settings_cloud_sync_pending,
                            pendingCount,
                            pendingCount
                        )
                        else -> stringResource(R.string.settings_cloud_sync_sub)
                    },
                    icon = Icons.Default.CloudSync,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { syncViewModel.syncNow() }
                )
                SettingsItem(
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.settings_import_sub),
                    icon = Icons.Default.Download,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onNavigateToImportCsv
                )
                SettingsItem(
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(R.string.settings_export_sub),
                    icon = Icons.Default.Upload,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { exportLauncher.launch("wallet_movimientos.csv") }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Sesión Wallet/Sanctum: iniciar o cerrar según el estado
                if (authState.isLoggedIn) {
                    SettingsItem(
                        title = stringResource(R.string.settings_logout),
                        subtitle = authState.userEmail,
                        icon = Icons.Default.Logout,
                        color = MaterialTheme.colorScheme.errorContainer,
                        onClick = { confirmLogout = true }
                    )
                } else {
                    SettingsItem(
                        title = stringResource(R.string.settings_login),
                        subtitle = stringResource(R.string.settings_login_sub),
                        icon = Icons.Default.Login,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onNavigateToLogin
                    )
                }

                SettingsItem(
                    title = stringResource(R.string.settings_support),
                    subtitle = stringResource(R.string.settings_support_sub),
                    icon = Icons.Default.Help,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = {}
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                // Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Límites MVP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Versión 1.0.0 (Build 42)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
