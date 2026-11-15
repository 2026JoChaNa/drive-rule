package com.jochana.driverule.core.platform.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** App-entry and daily cache refresh; neither an ignition trigger nor real-time event detection. */
object SchoolZoneSyncScheduler {
    private val scheduledThisProcess = AtomicBoolean(false)

    /** Called by MainActivity only; composing a screen or creating the Application schedules nothing. */
    fun onAppEntered(context: Context) {
        if (!scheduledThisProcess.compareAndSet(false, true)) return

        val workManager = WorkManager.getInstance(context.applicationContext)
        val connected = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val appEntry = OneTimeWorkRequestBuilder<SchoolZoneSyncWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(SYNC_TAG)
            .build()
        val daily = PeriodicWorkRequestBuilder<SchoolZoneSyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(SYNC_TAG)
            .build()

        workManager.enqueueUniqueWork(APP_ENTRY_WORK, ExistingWorkPolicy.KEEP, appEntry)
        // KEEP preserves the existing cadence when the process or Activity is recreated.
        workManager.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, daily)
    }

    private const val APP_ENTRY_WORK = "school-zone-sync-app-entry-v1"
    private const val DAILY_WORK = "school-zone-sync-daily-v1"
    private const val SYNC_TAG = "school-zone-sync"
    private const val BACKOFF_SECONDS = 30L
}
