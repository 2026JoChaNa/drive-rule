package com.jochana.driverule.core.platform.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jochana.driverule.DriveRuleApplication
import com.jochana.driverule.core.model.LawSyncOutcome

/** Deferred cache refresh only. No foreground service, vehicle signal or personal input data. */
class LawCardSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        // Do not catch cancellation: constraints and explicit cancellation must stop the sync.
        val outcome = (applicationContext as DriveRuleApplication)
            .appContainer.ruleCardRepository.synchronize()
        return when (outcome) {
            is LawSyncOutcome.Success -> Result.success()
            is LawSyncOutcome.Failure -> {
                if (outcome.retryable && runAttemptCount < MAX_RETRIES) Result.retry()
                else Result.failure()
            }
        }
    }

    private companion object {
        // runAttemptCount starts at zero: one initial attempt, then at most five retries.
        const val MAX_RETRIES = 5
    }
}
