package com.jochana.driverule.core.data.local.law

import com.jochana.driverule.core.data.remote.ConditionDefinitionDto
import com.jochana.driverule.core.data.remote.SyncedCardDto
import com.jochana.driverule.core.model.CachedAudience
import com.jochana.driverule.core.model.CachedCardType
import com.jochana.driverule.core.model.CachedChangeKind
import com.jochana.driverule.core.model.CachedCondition
import com.jochana.driverule.core.model.CachedConditionOption
import com.jochana.driverule.core.model.CachedEventTag
import com.jochana.driverule.core.model.CachedLawCard
import java.time.DateTimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Validation only, using the received condition dictionary; never Gleo answer selection. */
internal object LawCardValidator {
    fun conditions(dtos: List<ConditionDefinitionDto>): List<CachedCondition> {
        val keys = mutableSetOf<String>()
        return dtos.map { condition ->
            if (condition.key.isBlank() || condition.question.isBlank() || !keys.add(condition.key) ||
                condition.options.isEmpty() || condition.options.any { it.key.isBlank() || it.label.isBlank() } ||
                condition.options.map { it.key }.distinct().size != condition.options.size
            ) {
                throw InvalidCardSyncException("Invalid condition dictionary")
            }
            CachedCondition(
                condition.key,
                condition.question,
                condition.options.map { CachedConditionOption(it.key, it.label) },
            )
        }
    }

    fun card(dto: SyncedCardDto, conditions: List<CachedCondition>): CachedLawCard? {
        if (dto.cardId <= 0 || dto.version <= 0) return null
        val type = CachedCardType.entries.firstOrNull { it.name == dto.cardType } ?: return null
        val kind = CachedChangeKind.entries.firstOrNull { it.name == dto.changeKind } ?: return null
        val audience = CachedAudience.entries.firstOrNull { it.name == dto.audience } ?: return null
        // Future event tags disable only event triggering; the reviewed card remains usable.
        val event = CachedEventTag.entries.firstOrNull { it.name == dto.eventTag }
        if (!required(dto.title, 100) || !required(dto.ttsText, 500) ||
            !required(dto.legalBasis, 200) || !required(dto.lawExcerpt, 20_000) ||
            !optional(dto.sourceOrg, 100) || !optional(dto.topic, 40) ||
            !optional(dto.dontText, 300) || !optional(dto.doText, 300) ||
            !optional(dto.sceneKey, 40) || !optional(dto.penaltyText, 300) ||
            !optional(dto.exceptions, 20_000) ||
            dto.lawName?.isBlank() == true || dto.lawNo?.isBlank() == true
        ) return null
        when (type) {
            CachedCardType.SHORT -> if ((dto.dontText == null) != (dto.doText == null) || dto.decision != null) return null
            CachedCardType.CONDITIONAL -> if (dto.dontText != null || dto.doText != null ||
                dto.sceneKey != null || !validDecision(dto.decision, conditions)
            ) return null
            CachedCardType.FIXED_RESPONSE -> if (dto.decision != null) return null
        }
        when (kind) {
            CachedChangeKind.OPERATION -> if (dto.sourceOrg.isNullOrBlank() || dto.lawName != null ||
                dto.lawNo != null || dto.promulgatedOn != null
            ) return null
            CachedChangeKind.LAW -> if (dto.sourceOrg != null) return null
        }
        if (audience == CachedAudience.LIMITED && event != null) return null
        return try {
            CachedLawCard(
                id = dto.cardId, version = dto.version, cardType = type, changeKind = kind,
                audience = audience, eventTag = event, sourceOrg = dto.sourceOrg, topic = dto.topic,
                title = dto.title, dontText = dto.dontText, doText = dto.doText, sceneKey = dto.sceneKey,
                ttsText = dto.ttsText, legalBasis = dto.legalBasis, lawExcerpt = dto.lawExcerpt,
                penaltyText = dto.penaltyText, lawName = dto.lawName, lawNo = dto.lawNo,
                promulgatedOn = dto.promulgatedOn?.let(LocalDate::parse),
                effectiveOn = dto.effectiveOn?.let(LocalDate::parse), exceptions = dto.exceptions,
                decisionJson = dto.decision?.toString(),
                verifiedAtMillis = OffsetDateTime.parse(dto.verifiedAt).toInstant().toEpochMilli(),
            )
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun required(value: String, max: Int) = value.isNotBlank() && value.length <= max
    private fun optional(value: String?, max: Int) = value == null || required(value, max)
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun validDecision(decision: JsonObject?, conditions: List<CachedCondition>): Boolean {
        if (decision == null) return false
        val schema = decision["schema"] as? JsonPrimitive ?: return false
        if (schema.isString || schema.intOrNull != 2) return false
        val tables = decision["tables"] as? JsonArray ?: return false
        if (tables.size !in 1..2) return false
        val available = conditions.associateBy { it.key }
        val tableKeys = mutableSetOf<String>()
        for (element in tables) {
            val table = element as? JsonObject ?: return false
            val key = table["key"].text()?.takeIf { it.isNotBlank() } ?: return false
            if (!tableKeys.add(key) || table["title"].text().isNullOrBlank()) return false
            val keysJson = table["conditions"] as? JsonArray ?: return false
            if (keysJson.size !in 1..3) return false
            val keys = keysJson.map { it.text() ?: return false }
            if (keys.distinct().size != keys.size || keys.any { it !in available }) return false
            // Bound before expanding: coverage is at most eight combinations per sub-table.
            var combinationCount = 1
            for (conditionKey in keys) {
                val size = available.getValue(conditionKey).options.size
                if (size == 0 || size > 8 / combinationCount) return false
                combinationCount *= size
            }
            val rows = table["rows"] as? JsonArray ?: return false
            if (rows.isEmpty() || rows.size > combinationCount) return false
            val predicates = rows.map { rowElement ->
                val row = rowElement as? JsonObject ?: return false
                if (row["text"].text().isNullOrBlank() || row["tts"].text().isNullOrBlank()) return false
                val scene = row["scene"]
                if (scene != null && scene != JsonNull && scene.text() == null) return false
                val predicate = when (val value = row["when"]) {
                    null, JsonNull -> emptyMap()
                    is JsonObject -> value.mapValues { (_, option) -> option.text() ?: return false }
                    else -> return false
                }
                if (predicate.any { (conditionKey, option) ->
                        conditionKey !in keys || available[conditionKey]?.options?.none { it.key == option } != false
                    }
                ) return false
                predicate
            }
            var combinations = listOf(emptyMap<String, String>())
            for (conditionKey in keys) {
                combinations = combinations.flatMap { partial ->
                    available.getValue(conditionKey).options.map { partial + (conditionKey to it.key) }
                }
            }
            if (combinations.any { combination ->
                    predicates.count { predicate -> predicate.all { (k, v) -> combination[k] == v } } != 1
                }
            ) return false
        }
        return true
    }
}
