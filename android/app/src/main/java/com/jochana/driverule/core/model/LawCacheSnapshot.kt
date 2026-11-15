package com.jochana.driverule.core.model

import java.time.LocalDate

/** One committed cache generation. Cards contain only supported, non-retired copies. */
data class LawCacheSnapshot(
    val syncToken: String? = null,
    val lastSuccessfulSyncAtMillis: Long? = null,
    val cards: List<CachedLawCard> = emptyList(),
    val conditions: List<CachedCondition> = emptyList(),
)

/** Validated server text; this model does not generate legal content or match Gleo questions. */
data class CachedLawCard(
    val id: Long,
    val version: Int,
    val cardType: CachedCardType,
    val changeKind: CachedChangeKind,
    val audience: CachedAudience,
    val eventTag: CachedEventTag?,
    val sourceOrg: String?,
    val topic: String?,
    val title: String,
    val dontText: String?,
    val doText: String?,
    val sceneKey: String?,
    val ttsText: String,
    val legalBasis: String,
    val lawExcerpt: String,
    val penaltyText: String?,
    val lawName: String?,
    val lawNo: String?,
    val promulgatedOn: LocalDate?,
    val effectiveOn: LocalDate?,
    val exceptions: String?,
    /** Schema 2 JSON, preserving table, condition and row order. No answer matching is performed. */
    val decisionJson: String?,
    val verifiedAtMillis: Long,
)

enum class CachedCardType { SHORT, CONDITIONAL, FIXED_RESPONSE }
enum class CachedChangeKind { LAW, OPERATION }
enum class CachedAudience { GENERAL, LIMITED }
enum class CachedEventTag {
    SCHOOL_ZONE_ENTRY, PARKING_DESTINATION, PARKING_HIGHWAY, RIGHT_TURN_CROSSWALK,
}

data class CachedCondition(
    val key: String,
    val question: String,
    val options: List<CachedConditionOption>,
)

data class CachedConditionOption(val key: String, val label: String)
