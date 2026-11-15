package com.jochana.driverule.core.platform.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jochana.driverule.DriveRuleApplication
import com.jochana.driverule.core.model.SchoolZoneSyncResult

/** Downloads public school facilities only. Never receives vehicle/destination coordinates. */
class SchoolZoneSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when (val outcome =
        (applicationContext as DriveRuleApplication).appContainer.schoolZoneRepository.sync()) {
        is SchoolZoneSyncResult.Updated, SchoolZoneSyncResult.Unchanged -> Result.success()
        is SchoolZoneSyncResult.Failure ->
            if (outcome.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
