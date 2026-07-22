package com.bsolutions.wallet.core.notifications

import com.bsolutions.wallet.core.common.CategoryPlaceholders
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bsolutions.wallet.MainActivity
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.domain.repository.BudgetRepository
import com.bsolutions.wallet.domain.repository.CategoryRepository
import com.bsolutions.wallet.domain.repository.PlannedPaymentRepository
import com.bsolutions.wallet.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker periódico de recordatorios locales (sin backend):
 * - Pagos planificados vencidos o que vencen en las próximas 24 horas.
 * - Presupuestos excedidos en el mes en curso.
 */
@HiltWorker
class PlannedPaymentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val plannedPaymentRepository: PlannedPaymentRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Sin permiso de notificaciones (Android 13+) no hay nada que hacer
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val windowEnd = now + TimeUnit.HOURS.toMillis(24)

        val duePayments = plannedPaymentRepository.getPlannedPayments().first()
            .filter { it.isActive && it.nextDueDate <= windowEnd }
        val exceededBudgets = findExceededBudgets()

        if (duePayments.isEmpty() && exceededBudgets.isEmpty()) return Result.success()

        ensureChannel()
        val manager = NotificationManagerCompat.from(applicationContext)

        exceededBudgets.forEach { (categoryName, overAmount) ->
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(applicationContext.getString(R.string.notif_budget_exceeded, categoryName))
                .setContentText(
                    applicationContext.getString(R.string.notif_budget_over_by, MoneyFormat.format(overAmount))
                )
                .setContentIntent(mainActivityIntent("budget_$categoryName".hashCode()))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify("budget_$categoryName".hashCode(), notification)
        }

        duePayments.forEach { payment ->
            val overdue = payment.nextDueDate < now
            val title = applicationContext.getString(
                if (overdue) R.string.notif_payment_overdue else R.string.notif_payment_due,
                payment.name
            )
            val text = applicationContext.getString(
                R.string.notif_payment_amount,
                MoneyFormat.format(payment.amount)
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(mainActivityIntent(payment.id.hashCode()))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify(payment.id.hashCode(), notification)
        }

        return Result.success()
    }

    /** Presupuestos cuyo gasto del mes en curso supera el límite: (categoría, excedente). */
    private suspend fun findExceededBudgets(): List<Pair<String, Long>> {
        val budgets = budgetRepository.getBudgets().first()
        if (budgets.isEmpty()) return emptyList()

        val categories = categoryRepository.getCategories().first().associateBy { it.id }
        val now = Calendar.getInstance()
        val spentByCategory = transactionRepository.getTransactions().first()
            .filter { it.type == "EXPENSE" }
            .filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        return budgets.mapNotNull { budget ->
            val spent = spentByCategory[budget.categoryId] ?: 0L
            if (spent > budget.limitAmount) {
                val name = categories[budget.categoryId]?.name
                    ?: CategoryPlaceholders.deleted(budget.categoryId).name
                name to (spent - budget.limitAmount)
            } else null
        }
    }

    private fun mainActivityIntent(requestCode: Int): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = applicationContext.getString(R.string.notif_channel_desc)
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "planned_payments"
        const val WORK_NAME = "planned_payments_check"
    }
}
