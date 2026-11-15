package com.jochana.driverule.core.data.local.law

import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.model.LawCacheSnapshot
import kotlinx.coroutines.flow.Flow

interface LawCardCache {
    fun observe(): Flow<LawCacheSnapshot>
    suspend fun read(): LawCacheSnapshot
    suspend fun apply(response: CardSyncDto, syncedAtEpochMs: Long): CacheApplyResult
}

data class CacheApplyResult(
    /** Supported response entries accepted for use; an overlapping retirement takes precedence. */
    val appliedCardCount: Int,
    /** Invalid, unsupported, older or retired response entries. */
    val ignoredCardCount: Int,
    /** Known copies newly marked retired by this response, including newly received copies. */
    val retiredCardCount: Int,
)

/** Invalid response-wide data. Its entire transaction must leave the previous cache untouched. */
class InvalidCardSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)
