package com.jochana.driverule.core.data.repository

import com.jochana.driverule.core.data.local.law.InvalidCardSyncException
import com.jochana.driverule.core.data.local.law.LawCardCache
import com.jochana.driverule.core.data.remote.BackendResult
import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.model.CardCatalog
import com.jochana.driverule.core.model.CachedAudience
import com.jochana.driverule.core.model.LawCacheSnapshot
import com.jochana.driverule.core.model.LawSyncFailure
import com.jochana.driverule.core.model.LawSyncOutcome
import com.jochana.driverule.core.model.LawSyncState
import com.jochana.driverule.core.model.RuleCardSummary
import com.jochana.driverule.domain.repository.RuleCardRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local reads never cause network requests. WorkManager or an explicit command owns refresh.
 * One shared repository serializes responses so a slower request cannot roll back the cursor.
 * Profile names, profile IDs and driving history never enter this dependency boundary.
 */
class CachedRuleCardRepository(
    private val cache: LawCardCache,
    private val fetch: suspend (syncToken: String?) -> BackendResult<CardSyncDto>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RuleCardRepository {
    private val syncMutex = Mutex()
    private val mutableSyncState = MutableStateFlow<LawSyncState>(LawSyncState.Idle)
    val syncState: StateFlow<LawSyncState> = mutableSyncState.asStateFlow()

    /** Full local snapshot for later UI/event adapters; no driver-specific history is implied. */
    fun observeCache(): Flow<LawCacheSnapshot> = cache.observe()

    /** Re-read after a durable attempt reservation; a queued observer must not hide retirement. */
    suspend fun readCache(): LawCacheSnapshot = cache.read()

    override fun observeCatalog(): Flow<CardCatalog> = cache.observe().map { snapshot ->
        if (snapshot.lastSuccessfulSyncAtMillis == null) CardCatalog.Unavailable
        else CardCatalog.Available(snapshot.cards.filter { it.audience == CachedAudience.GENERAL }
            .map { RuleCardSummary(it.id, it.version, it.title) })
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(CardCatalog.Unavailable)
    }

    suspend fun synchronize(): LawSyncOutcome = syncMutex.withLock {
        mutableSyncState.value = LawSyncState.Running
        try {
            val outcome = synchronizeLocked()
            mutableSyncState.value = LawSyncState.Finished(outcome)
            outcome
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            // Unexpected programmer/adapter failures also must not strand the state at Running.
            if (mutableSyncState.value == LawSyncState.Running) mutableSyncState.value = LawSyncState.Idle
        }
    }

    private suspend fun synchronizeLocked(): LawSyncOutcome {
        val previous = try {
            cache.read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LawSyncOutcome.Failure(LawSyncFailure.Storage, retryable = true)
        }

        var requestedToken = previous.syncToken
        var response = request(requestedToken)
        if (response is BackendResult.HttpFailure && response.statusCode == 410 &&
            response.problem?.code == "SYNC_TOKEN_EXPIRED"
        ) {
            // Do not clear the persisted token separately: even this recovery is atomic.
            requestedToken = null
            response = request(null)
        }
        when (response) {
            is BackendResult.Success -> {
                val payload = response.value
                // A tokenless recovery must establish a complete baseline, never a partial delta.
                if (requestedToken == null && !payload.fullSync) {
                    return LawSyncOutcome.Failure(LawSyncFailure.InvalidResponse, retryable = false)
                }
                return try {
                    val applied = cache.apply(payload, nowMillis())
                    LawSyncOutcome.Success(applied.appliedCardCount, applied.ignoredCardCount, applied.retiredCardCount)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: InvalidCardSyncException) {
                    LawSyncOutcome.Failure(LawSyncFailure.InvalidResponse, retryable = false)
                } catch (_: Exception) {
                    LawSyncOutcome.Failure(LawSyncFailure.Storage, retryable = true)
                }
            }
            BackendResult.NetworkFailure -> return LawSyncOutcome.Failure(LawSyncFailure.Network, retryable = true)
            is BackendResult.HttpFailure -> return LawSyncOutcome.Failure(
                LawSyncFailure.Server,
                // App API v1: other 4xx responses are terminal for this scheduled run.
                retryable = response.statusCode in 500..599,
            )
            is BackendResult.MalformedResponse, is BackendResult.NotModified ->
                return LawSyncOutcome.Failure(LawSyncFailure.InvalidResponse, retryable = false)
        }
    }

    private suspend fun request(token: String?): BackendResult<CardSyncDto> = try {
        fetch(token)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        BackendResult.NetworkFailure
    }
}
