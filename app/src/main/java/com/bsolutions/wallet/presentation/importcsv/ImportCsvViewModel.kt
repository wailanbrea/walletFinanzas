package com.bsolutions.wallet.presentation.importcsv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.core.common.CsvParser
import com.bsolutions.wallet.domain.model.Account
import com.bsolutions.wallet.domain.model.Transaction
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/** Mapeo de columnas del CSV a campos de transacción (-1 = sin asignar). */
data class ColumnMapping(
    val dateCol: Int = -1,
    val descriptionCol: Int = -1,
    val amountCol: Int = -1
)

data class PreviewRow(
    val index: Int,
    val date: Long?,
    val description: String,
    /** Monto en centavos; negativo = gasto. */
    val amount: Long?,
    val isDuplicate: Boolean
) {
    val isValid: Boolean get() = date != null && amount != null && amount != 0L
}

data class ImportResult(
    val imported: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int
)

data class ImportCsvUiState(
    val fileName: String? = null,
    val headers: List<String> = emptyList(),
    val mapping: ColumnMapping = ColumnMapping(),
    val previewRows: List<PreviewRow> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String = "",
    val includeDuplicates: Boolean = false,
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCsvUiState())
    val uiState: StateFlow<ImportCsvUiState> = _uiState.asStateFlow()

    private var csvRows: List<List<String>> = emptyList()
    private var existingSignatures: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            accountRepository.getAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccountId = _uiState.value.selectedAccountId
                        .ifEmpty { accounts.firstOrNull()?.id ?: "" }
                )
            }
        }
    }

    fun loadCsv(fileName: String, content: String) {
        viewModelScope.launch {
            try {
                val data = CsvParser.parse(content)
                if (data.headers.isEmpty() || data.rows.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        error = "El archivo está vacío o no tiene filas de datos.",
                        fileName = null, headers = emptyList(), previewRows = emptyList()
                    )
                    return@launch
                }
                csvRows = data.rows

                // Firmas de las transacciones existentes para deduplicar
                val existing = transactionRepository.getTransactions().first()
                existingSignatures = existing.map { signature(it.date, it.amount, it.note) }.toSet()

                val mapping = autoDetectMapping(data.headers)
                _uiState.value = _uiState.value.copy(
                    fileName = fileName,
                    headers = data.headers,
                    mapping = mapping,
                    previewRows = buildPreview(mapping),
                    result = null,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "No se pudo leer el archivo: ${e.message}")
            }
        }
    }

    fun updateMapping(mapping: ColumnMapping) {
        _uiState.value = _uiState.value.copy(
            mapping = mapping,
            previewRows = buildPreview(mapping)
        )
    }

    fun selectAccount(accountId: String) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun toggleIncludeDuplicates() {
        _uiState.value = _uiState.value.copy(includeDuplicates = !_uiState.value.includeDuplicates)
    }

    fun import() {
        val state = _uiState.value
        if (state.selectedAccountId.isBlank() || state.isImporting) return

        viewModelScope.launch {
            _uiState.value = state.copy(isImporting = true)

            var imported = 0
            var skippedDup = 0
            var skippedInvalid = 0
            var balanceDelta = 0L

            for (row in state.previewRows) {
                if (!row.isValid) { skippedInvalid++; continue }
                if (row.isDuplicate && !state.includeDuplicates) { skippedDup++; continue }

                val amountAbs = kotlin.math.abs(row.amount!!)
                val isIncome = row.amount > 0
                transactionRepository.addTransaction(
                    Transaction(
                        id = UUID.randomUUID().toString(),
                        accountId = state.selectedAccountId,
                        amount = amountAbs,
                        type = if (isIncome) "INCOME" else "EXPENSE",
                        categoryId = "",
                        date = row.date!!,
                        note = row.description
                    )
                )
                balanceDelta += row.amount
                imported++
            }

            // Un solo ajuste de balance al final
            accountRepository.getAccount(state.selectedAccountId)?.let { account ->
                accountRepository.updateAccount(account.copy(balance = account.balance + balanceDelta))
            }

            _uiState.value = _uiState.value.copy(
                isImporting = false,
                result = ImportResult(imported, skippedDup, skippedInvalid)
            )
        }
    }

    fun reset() {
        csvRows = emptyList()
        _uiState.value = ImportCsvUiState(
            accounts = _uiState.value.accounts,
            selectedAccountId = _uiState.value.selectedAccountId
        )
    }

    private fun buildPreview(mapping: ColumnMapping): List<PreviewRow> {
        return csvRows.mapIndexed { index, row ->
            val date = row.getOrNull(mapping.dateCol)?.let { CsvParser.parseDate(it) }
            val desc = row.getOrNull(mapping.descriptionCol)?.trim().orEmpty()
            val amount = row.getOrNull(mapping.amountCol)?.let { CsvParser.parseAmount(it) }

            val duplicate = if (date != null && amount != null) {
                signature(date, kotlin.math.abs(amount), desc) in existingSignatures
            } else false

            PreviewRow(index, date, desc, amount, duplicate)
        }
    }

    private fun autoDetectMapping(headers: List<String>): ColumnMapping {
        fun find(vararg keys: String): Int =
            headers.indexOfFirst { h -> keys.any { h.lowercase().contains(it) } }

        return ColumnMapping(
            dateCol = find("fecha", "date", "fec."),
            descriptionCol = find("descrip", "concepto", "detalle", "referencia", "description", "memo"),
            amountCol = find("monto", "importe", "amount", "valor", "total")
        )
    }

    /** Firma de deduplicación: día (sin hora) + monto + descripción normalizada. */
    private fun signature(dateMillis: Long, amount: Long, note: String): String {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val day = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        return "$day|$amount|${note.trim().lowercase()}"
    }
}
