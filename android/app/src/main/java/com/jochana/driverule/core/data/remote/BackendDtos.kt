package com.jochana.driverule.core.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Wire DTOs only. Parsing a response does not approve a card for display or cache it. */
@Serializable
data class CardSyncDto(
    val syncToken: String,
    val fullSync: Boolean,
    val cards: List<SyncedCardDto>,
    val retiredCardIds: List<Long>,
    val conditions: List<ConditionDefinitionDto>,
)

@Serializable
data class SyncedCardDto(
    val cardId: Long,
    val version: Int,
    // Keep wire values so one new enum does not prevent parsing the entire response.
    // A later mapper must reject unsupported card types and event/schema values.
    val cardType: String,
    val changeKind: String,
    val audience: String,
    val eventTag: String? = null,
    val sourceOrg: String? = null,
    val topic: String? = null,
    val title: String,
    val dontText: String? = null,
    val doText: String? = null,
    val sceneKey: String? = null,
    val ttsText: String,
    val legalBasis: String,
    val lawExcerpt: String,
    val penaltyText: String? = null,
    val lawName: String? = null,
    val lawNo: String? = null,
    val promulgatedOn: String? = null,
    val effectiveOn: String? = null,
    val exceptions: String? = null,
    // Preserve the object, including schema, until a separately tested rule mapper exists.
    // Assuming a tables[] shape here would break parsing when a future schema arrives.
    val decision: JsonObject? = null,
    val verifiedAt: String,
)

@Serializable
data class ConditionDefinitionDto(
    val key: String,
    val question: String,
    val options: List<ConditionOptionDto>,
)

@Serializable
data class ConditionOptionDto(val key: String, val label: String)

@Serializable
data class SchoolZoneManifestDto(
    val dataset: String,
    val schema: Int,
    val referenceDate: String,
    val count: Int,
    val sha256: String,
    val bytes: Long,
    val url: String,
)

@Serializable
data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val errors: List<String>? = null,
)

@Serializable
internal data class CardErrorReportDto(val cardVersion: Int)

@Serializable
internal data class AnswerHoldDto(val topic: String, val conditions: Map<String, String>? = null)
