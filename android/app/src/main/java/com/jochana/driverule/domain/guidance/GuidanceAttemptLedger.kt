package com.jochana.driverule.domain.guidance

/** Local attempt reservation, never evidence that speech completed or a driver read a card. */
data class GuidanceAttemptRequest(
    val profileId: Long,
    val cardId: Long,
    val stage: String,
    val nowEpochMs: Long,
    val nowElapsedMs: Long,
    val bootId: String,
)

interface GuidanceAttemptLedger {
    /** Persist before output. False includes suppression, unavailable storage and invalid data. */
    suspend fun reserve(request: GuidanceAttemptRequest): Boolean
}

data class GuidanceAttemptRecord(
    val profileId: Long,
    val cardId: Long,
    val stage: String,
    val epochMs: Long,
    val elapsedMs: Long,
    val bootId: String,
)

/** Conservative per-profile limits until a real trip boundary is verified. No card version key. */
internal object GuidanceAttemptPolicy {
    const val RETENTION_MS = 24 * 60 * 60 * 1_000L
    const val PROFILE_COOLDOWN_MS = 60_000L
    const val MAX_PROFILE_ATTEMPTS = 2
    const val MAX_RECORDS = 128
    private val stages = setOf("Current", "Upcoming", "Unspecified")

    /** Null means fail closed. Unexpired entries are never evicted just to make room. */
    fun reserve(records: List<GuidanceAttemptRecord>, request: GuidanceAttemptRequest): List<GuidanceAttemptRecord>? {
        val candidate = GuidanceAttemptRecord(request.profileId, request.cardId, request.stage,
            request.nowEpochMs, request.nowElapsedMs, request.bootId)
        if (!valid(candidate) || records.size > MAX_RECORDS || records.any { !valid(it) }) return null
        val retained = mutableListOf<Pair<GuidanceAttemptRecord, Long>>()
        for (record in records) {
            // Do not persist a rolled-back wall timestamp that could expire too early after reboot.
            if (request.nowEpochMs < record.epochMs) return null
            val age = if (record.bootId == request.bootId) {
                // Within a boot, wall-clock adjustments cannot expire a previous attempt early.
                if (request.nowElapsedMs < record.elapsedMs) return null
                request.nowElapsedMs - record.elapsedMs
            } else {
                request.nowEpochMs - record.epochMs
            }
            if (age < RETENTION_MS) retained += record to age
        }
        val profile = retained.filter { it.first.profileId == request.profileId }
        if (profile.any { (record, _) -> record.cardId == request.cardId && record.stage == request.stage } ||
            profile.any { (_, age) -> age < PROFILE_COOLDOWN_MS } ||
            profile.size >= MAX_PROFILE_ATTEMPTS || retained.size >= MAX_RECORDS) return null
        return retained.map { it.first } + candidate
    }

    private fun valid(record: GuidanceAttemptRecord): Boolean = record.profileId > 0 && record.cardId > 0 &&
        record.stage in stages && record.epochMs >= 0 && record.elapsedMs >= 0 &&
        record.bootId.isNotBlank() && record.bootId.length <= 128 && record.bootId.none(Char::isISOControl)
}
