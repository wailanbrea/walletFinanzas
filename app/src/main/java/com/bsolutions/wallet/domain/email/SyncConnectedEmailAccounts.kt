package com.bsolutions.wallet.domain.email

import com.bsolutions.wallet.data.repository.EmailConnectionStatus
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import javax.inject.Inject

/** Sincroniza, de forma aislada, todas las cuentas de correo conectadas. */
class SyncConnectedEmailAccounts @Inject constructor(
    private val repository: EmailConnectionsRepository
) {
    suspend operator fun invoke(): EmailSyncSweepResult {
        val providers = repository.getConnections()
            .filter { it.status == EmailConnectionStatus.CONNECTED }
            .map { it.provider }
            .distinct()

        val failures = providers.count { provider ->
            runCatching { repository.sync(provider) }.isFailure
        }

        return EmailSyncSweepResult(
            attempted = providers.size,
            failed = failures
        )
    }
}

data class EmailSyncSweepResult(
    val attempted: Int,
    val failed: Int
)
