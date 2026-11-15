package com.jochana.driverule.core.data.local.law

import androidx.room.withTransaction
import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.model.LawCacheSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomLawCardCache(private val database: LawCardDatabase) : LawCardCache {
    private val dao = database.lawCardDao()

    override fun observe(): Flow<LawCacheSnapshot> = dao.observe().map { it.snapshot() }
        .distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun read(): LawCacheSnapshot = withContext(Dispatchers.IO) { dao.read().snapshot() }

    override suspend fun apply(response: CardSyncDto, syncedAtEpochMs: Long): CacheApplyResult =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dao.read().singleOrNull()?.cards.orEmpty().map { it.record() }
                val merged = LawCacheMergePolicy.merge(existing, response, syncedAtEpochMs)
                dao.saveCards(merged.records.map {
                    LawCardEntity(it.id, it.version, it.topic, it.payloadJson, it.isRetired, it.isSupported, it.verifiedAtMillis)
                })
                // Stored last in the same transaction: failure also rolls back all cards/retirements.
                dao.saveState(
                    LawCacheStateEntity(
                        syncToken = response.syncToken, lastSuccessfulSyncAtMillis = syncedAtEpochMs,
                        conditionsJson = LawCacheCodec.encodeConditions(response.conditions),
                    ),
                )
                merged.result
            }
        }

    private fun List<StoredLawCacheSnapshot>.snapshot(): LawCacheSnapshot {
        val stored = singleOrNull() ?: return LawCacheSnapshot()
        val conditions = LawCacheCodec.decodeConditions(stored.state.conditionsJson)
        return LawCacheSnapshot(
            syncToken = stored.state.syncToken,
            lastSuccessfulSyncAtMillis = stored.state.lastSuccessfulSyncAtMillis,
            cards = LawCacheCodec.visibleCards(stored.cards.map { it.record() }, conditions),
            conditions = conditions,
        )
    }
}
