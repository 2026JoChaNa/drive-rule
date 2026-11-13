package com.jochana.driverule.domain.repository

/** A published server card copy. Sample UI cards have no reference and are never reported. */
data class CardReportTarget(val cardId: Long, val cardVersion: Int) {
    init {
        require(cardId > 0) { "cardId must be positive" }
        require(cardVersion > 0) { "cardVersion must be positive" }
    }
}

enum class CardReportOutcome {
    /** 202: the server accepted the report. */
    Sent,

    /** 400 · 404 · 422: retrying the same request cannot succeed. */
    Rejected,

    /** Network failure, 5xx or an unexpected response: the driver may try again later. */
    RetryLater,
}

/** Sends only the card id and the app copy's version. No driver, location or vehicle data. */
fun interface CardErrorReportRepository {
    suspend fun report(target: CardReportTarget): CardReportOutcome
}
