package com.bsolutions.wallet.presentation.importcsv

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.bsolutions.wallet.R
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import com.bsolutions.wallet.core.common.MoneyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCsvScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportCsvViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "archivo.csv"
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                viewModel.loadCsv(name, content)
            } catch (_: Exception) {
                // loadCsv reporta errores de parseo; aquí solo fallos de lectura del stream
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = walletTopBarColors()
            )
        }
    ) { innerPadding ->
        when {
            uiState.result != null -> ImportResultView(
                result = uiState.result!!,
                modifier = Modifier.padding(innerPadding),
                onDone = onNavigateBack,
                onImportAnother = { viewModel.reset() }
            )
            uiState.headers.isNotEmpty() -> ImportPreviewView(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
            else -> ImportStartView(
                error = uiState.error,
                modifier = Modifier.padding(innerPadding),
                onPickFile = {
                    filePicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
                }
            )
        }
    }
}

@Composable
private fun ImportStartView(
    error: String?,
    modifier: Modifier = Modifier,
    onPickFile: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    stringResource(R.string.import_header),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.import_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPickFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(stringResource(R.string.import_pick_file), fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = stringResource(R.string.import_format_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun ImportPreviewView(
    uiState: ImportCsvUiState,
    viewModel: ImportCsvViewModel,
    modifier: Modifier = Modifier
) {
    val validCount = uiState.previewRows.count { it.isValid && (!it.isDuplicate || uiState.includeDuplicates) }
    val dupCount = uiState.previewRows.count { it.isValid && it.isDuplicate }
    val invalidCount = uiState.previewRows.count { !it.isValid }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.fileName ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.previewRows.size} filas · $validCount a importar · $dupCount duplicadas · $invalidCount inválidas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mapeo de columnas
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.import_mapping), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        ColumnSelector(stringResource(R.string.import_col_date), uiState.headers, uiState.mapping.dateCol) {
                            viewModel.updateMapping(uiState.mapping.copy(dateCol = it))
                        }
                        ColumnSelector(stringResource(R.string.import_col_description), uiState.headers, uiState.mapping.descriptionCol) {
                            viewModel.updateMapping(uiState.mapping.copy(descriptionCol = it))
                        }
                        ColumnSelector(stringResource(R.string.import_col_amount), uiState.headers, uiState.mapping.amountCol) {
                            viewModel.updateMapping(uiState.mapping.copy(amountCol = it))
                        }
                    }
                }
            }

            // Cuenta destino
            item {
                Text(stringResource(R.string.import_target_account), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                val selected = uiState.accounts.find { it.id == uiState.selectedAccountId }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selected?.name ?: stringResource(R.string.common_select_account))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        uiState.accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    viewModel.selectAccount(acc.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (dupCount > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.import_include_duplicates,
                                dupCount,
                                dupCount
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = uiState.includeDuplicates,
                            onCheckedChange = { viewModel.toggleIncludeDuplicates() }
                        )
                    }
                }
            }

            item {
                Text(stringResource(R.string.import_preview), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            items(uiState.previewRows.take(50), key = { it.index }) { row ->
                PreviewRowItem(row)
            }

            if (uiState.previewRows.size > 50) {
                item {
                    Text(
                        "… y ${uiState.previewRows.size - 50} filas más",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Barra inferior de acciones
        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) { Text(stringResource(R.string.common_cancel)) }
                Button(
                    onClick = { viewModel.import() },
                    enabled = validCount > 0 && uiState.selectedAccountId.isNotBlank() && !uiState.isImporting,
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            pluralStringResource(R.plurals.import_action, validCount, validCount),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnSelector(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headers.getOrNull(selectedIndex) ?: "— sin asignar —",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selectedIndex < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                headers.forEachIndexed { index, header ->
                    DropdownMenuItem(
                        text = { Text(header) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRowItem(row: PreviewRow) {
    val dateStr = row.date?.let { SimpleDateFormat("dd MMM yyyy", LocalConfiguration.current.locales[0]).format(Date(it)) } ?: "¿fecha?"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !row.isValid -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                row.isDuplicate -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when {
                    !row.isValid -> Icons.Default.ErrorOutline
                    row.isDuplicate -> Icons.Default.ContentCopy
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = when {
                    !row.isValid -> MaterialTheme.colorScheme.error
                    row.isDuplicate -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.description.ifBlank { "(sin descripción)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateStr + if (row.isDuplicate) " · duplicada" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            row.amount?.let { amount ->
                Text(
                    text = MoneyFormat.formatSigned(kotlin.math.abs(amount), isIncome = amount > 0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (amount > 0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ImportResultView(
    result: ImportResult,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
    onImportAnother: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.import_done_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${result.imported} movimientos importados" +
                (if (result.skippedDuplicates > 0) " · ${result.skippedDuplicates} duplicados omitidos" else "") +
                (if (result.skippedInvalid > 0) " · ${result.skippedInvalid} filas inválidas" else ""),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) { Text(stringResource(R.string.import_done), fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onImportAnother) {
            Text(stringResource(R.string.import_another))
        }
    }
}
