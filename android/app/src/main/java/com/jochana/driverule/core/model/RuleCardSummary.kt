package com.jochana.driverule.core.model

/** Minimal display model; it is not an API DTO or a database entity. */
data class RuleCardSummary(
    val id: Long,
    val version: Int,
    val title: String,
)
