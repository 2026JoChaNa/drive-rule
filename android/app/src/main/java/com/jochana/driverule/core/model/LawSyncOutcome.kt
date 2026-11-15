package com.jochana.driverule.core.model

/** Transport/cache progress is separate from whether a cached law is currently in force. */
sealed interface LawSyncOutcome {
    data class Success(
        val updatedCount: Int,
        val ignoredCount: Int,
        val retiredCount: Int,
    ) : LawSyncOutcome

    data class Failure(val reason: LawSyncFailure, val retryable: Boolean) : LawSyncOutcome
}

enum class LawSyncFailure { Network, Server, InvalidResponse, Storage }

sealed interface LawSyncState {
    data object Idle : LawSyncState
    data object Running : LawSyncState
    data class Finished(val outcome: LawSyncOutcome) : LawSyncState
}
