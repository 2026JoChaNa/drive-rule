package com.jochana.driverule.core.data.local.law

import com.jochana.driverule.core.data.remote.CardSyncDto
import com.jochana.driverule.core.data.remote.ConditionDefinitionDto
import com.jochana.driverule.core.data.remote.SyncedCardDto
import com.jochana.driverule.core.model.CachedCondition
import com.jochana.driverule.core.model.CachedLawCard
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Storage-neutral policy, tested on the JVM independently of Room transaction mechanics. */
internal data class LawCardRecord(
    val id: Long,
    val version: Int,
    val topic: String?,
    val payloadJson: String,
    val isRetired: Boolean = false,
    val isSupported: Boolean,
    val verifiedAtMillis: Long? = null,
)

internal data class MergedLawCache(
    val records: List<LawCardRecord>,
    val conditions: List<CachedCondition>,
    val result: CacheApplyResult,
)

internal object LawCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeCard(card: SyncedCardDto): String = json.encodeToString(card)
    fun encodeConditions(conditions: List<ConditionDefinitionDto>): String = json.encodeToString(conditions)

    fun decodeCard(record: LawCardRecord): SyncedCardDto {
        val dto = decode<SyncedCardDto>(record.payloadJson)
        if (dto.cardId != record.id || dto.version != record.version) {
            throw InvalidCardSyncException("Cached card identity does not match its payload")
        }
        return dto
    }

    fun decodeConditions(value: String): List<CachedCondition> =
        LawCardValidator.conditions(decode<List<ConditionDefinitionDto>>(value))

    fun visibleCards(records: List<LawCardRecord>, conditions: List<CachedCondition>): List<CachedLawCard> =
        records.asSequence().filter { !it.isRetired && it.isSupported }
            .mapNotNull { record ->
                LawCardValidator.card(decodeCard(record), conditions)
                    ?.takeIf { it.verifiedAtMillis == record.verifiedAtMillis }
            }.sortedBy { it.id }.toList()

    private inline fun <reified T> decode(value: String): T = try {
        json.decodeFromString<T>(value)
    } catch (failure: SerializationException) {
        throw InvalidCardSyncException("Unreadable cached law data", failure)
    }
}

internal object LawCacheMergePolicy {
    fun merge(existing: List<LawCardRecord>, response: CardSyncDto, syncedAtEpochMs: Long): MergedLawCache {
        if (response.syncToken.isBlank() || syncedAtEpochMs < 0 || response.retiredCardIds.any { it <= 0 }) {
            throw InvalidCardSyncException("Invalid card sync envelope")
        }
        // A bad shared dictionary invalidates the entire response, not just one card.
        val conditions = LawCardValidator.conditions(response.conditions)
        val records = existing.associateByTo(linkedMapOf()) { it.id }
        val retirements = response.retiredCardIds.toSet()
        var applied = 0
        var ignored = 0
        for (dto in response.cards) {
            val previous = records[dto.cardId]
            if (dto.cardId <= 0 || dto.version <= 0 || previous?.isRetired == true ||
                (previous != null && dto.version < previous.version)
            ) {
                ignored++
                continue
            }
            val card = LawCardValidator.card(dto, conditions)
            records[dto.cardId] = LawCardRecord(
                id = dto.cardId, version = dto.version, topic = dto.topic,
                payloadJson = LawCacheCodec.encodeCard(dto), isSupported = card != null,
                verifiedAtMillis = card?.verifiedAtMillis,
            )
            if (card == null || dto.cardId in retirements) ignored++ else applied++
        }
        var retired = 0
        for (id in retirements) {
            val record = records[id] ?: continue
            if (!record.isRetired) {
                records[id] = record.copy(isRetired = true)
                retired++
            }
        }
        // Full sync is also a merge. Omitted published copies may currently be under re-review.
        // A higher unsupported version is retained separately from retirement and blocks fallback.
        // Recheck omitted conditional copies against the replacement dictionary as well.
        val revalidated = records.values.map { record ->
            if (record.isRetired) {
                record
            } else {
                val card = LawCardValidator.card(LawCacheCodec.decodeCard(record), conditions)
                record.copy(isSupported = card != null, verifiedAtMillis = card?.verifiedAtMillis)
            }
        }
        return MergedLawCache(revalidated, conditions, CacheApplyResult(applied, ignored, retired))
    }
}
