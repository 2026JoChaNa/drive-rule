package com.jochana.driverule.core.model

sealed interface CardCatalog {
    data object Unavailable : CardCatalog

    /** An empty list means a successful lookup with no cards, not a failed lookup. */
    data class Available(val cards: List<RuleCardSummary>) : CardCatalog
}
