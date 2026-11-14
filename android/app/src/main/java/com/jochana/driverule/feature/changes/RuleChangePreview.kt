package com.jochana.driverule.feature.changes

import com.jochana.driverule.feature.home.HomePreviewStatus
import java.time.LocalDate

/** Figma `Rule row` icon tiles used on the monthly list (00-1). */
enum class RuleChangeIcon(val asset: String) {
    Drink("drink"),
    Calendar("calendar"),
    Camera("camera"),
    Drug("drug"),
    License("license"),
}

/**
 * One law change on the monthly list. Display model only: not the server card DTO.
 * Debug samples carry Figma design copy and are labelled as samples on screen.
 */
data class RuleChangePreview(
    val id: String,
    val title: String,
    /** Korean calendar date the change takes effect. */
    val effectiveOn: LocalDate,
    /** e.g. `제21728호`. Shown after the date; never inferred. */
    val lawNo: String,
    val status: HomePreviewStatus,
    val icon: RuleChangeIcon,
    /** Opens this card detail when the row is tapped, if the app has one. */
    val cardId: String? = null,
    /**
     * Sample-only stand-in for "already told on the home list". A real per-driver history
     * will replace it; the banner must not repeat rules the driver already heard.
     */
    val alreadyHeard: Boolean = false,
)

data class RuleChangeMonth(val year: Int, val month: Int, val items: List<RuleChangePreview>)

data class RuleChangeSections(val upcoming: List<RuleChangeMonth>, val recent: List<RuleChangeMonth>)

/** Figma 00-b banner: the next effective date among rules the driver has not heard yet. */
data class UpcomingRulesBanner(val effectiveOn: LocalDate, val items: List<RuleChangePreview>)

/**
 * Figma 00-1 groups by status, then by month, newest month first in both sections, and keeps
 * the given order inside a month. The status comes from the reviewed copy; the date is not used
 * to promote a scheduled change to active.
 */
fun groupRuleChanges(changes: List<RuleChangePreview>): RuleChangeSections {
    fun months(status: HomePreviewStatus) = changes
        .filter { it.status == status }
        .groupBy { it.effectiveOn.year to it.effectiveOn.monthValue }
        .map { (key, items) -> RuleChangeMonth(key.first, key.second, items) }
        .sortedWith(compareByDescending<RuleChangeMonth> { it.year }.thenByDescending { it.month })
    return RuleChangeSections(
        upcoming = months(HomePreviewStatus.Scheduled),
        recent = months(HomePreviewStatus.Active),
    )
}

fun upcomingRulesBanner(changes: List<RuleChangePreview>): UpcomingRulesBanner? {
    val unheard = changes.filter { it.status == HomePreviewStatus.Scheduled && !it.alreadyHeard }
    val next = unheard.minOfOrNull { it.effectiveOn } ?: return null
    return UpcomingRulesBanner(next, unheard.filter { it.effectiveOn == next })
}
