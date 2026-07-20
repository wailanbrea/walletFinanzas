package com.bsolutions.wallet.presentation.settings

import androidx.lifecycle.ViewModel
import com.bsolutions.wallet.domain.repository.AccountRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Genera el CSV de todos los movimientos para backup/exportación manual. */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    suspend fun buildCsv(): String {
        val transactions = transactionRepository.getTransactions().first()
            .sortedByDescending { it.date }
        val accounts = accountRepository.getAccounts().first().associateBy { it.id }
        val categories = categoryRepository.getCategories().first().associateBy { it.id }
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Fecha,Descripción,Monto,Tipo,Cuenta,Categoría")
        for (tx in transactions) {
            val sign = if (tx.type == "INCOME") "" else "-"
            val amount = "$sign%.2f".format(Locale.US, tx.amount / 100.0)
            val type = when (tx.type) {
                "INCOME" -> "Ingreso"
                "TRANSFER" -> "Transferencia"
                else -> "Gasto"
            }
            sb.appendLine(
                listOf(
                    dateFmt.format(Date(tx.date)),
                    csvField(tx.note),
                    amount,
                    type,
                    csvField(accounts[tx.accountId]?.name ?: ""),
                    csvField(categories[tx.categoryId]?.name ?: "")
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /** Escapa comas/comillas/saltos de línea según RFC 4180. */
    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
