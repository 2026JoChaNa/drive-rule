package com.jochana.driverule.feature.changes

import com.jochana.driverule.feature.home.HomePreviewStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleChangeGroupingTest {

    private fun change(id: String, date: String, status: HomePreviewStatus, heard: Boolean = false) =
        RuleChangePreview(id, id, LocalDate.parse(date), "제1호", status, RuleChangeIcon.Calendar, alreadyHeard = heard)

    private val figmaOrder = listOf(
        change("drink", "2027-06-03", HomePreviewStatus.Scheduled),
        change("repeat", "2027-06-03", HomePreviewStatus.Scheduled),
        change("highway", "2027-04-14", HomePreviewStatus.Scheduled, heard = true),
        change("drug-def", "2026-07-01", HomePreviewStatus.Active),
        change("drug", "2026-04-02", HomePreviewStatus.Active),
        change("license", "2026-01-01", HomePreviewStatus.Active),
        change("breath", "2025-06-04", HomePreviewStatus.Active),
    )

    @Test
    fun `sections are split by status and months run newest first`() {
        val sections = groupRuleChanges(figmaOrder.shuffled(java.util.Random(7)).sortedBy { it.id })

        assertEquals(listOf(2027 to 6, 2027 to 4), sections.upcoming.map { it.year to it.month })
        assertEquals(
            listOf(2026 to 7, 2026 to 4, 2026 to 1, 2025 to 6),
            sections.recent.map { it.year to it.month },
        )
        assertEquals(setOf("drink", "repeat"), sections.upcoming.first().items.map { it.id }.toSet())
    }

    @Test
    fun `banner shows the next date among unheard scheduled changes`() {
        val banner = requireNotNull(upcomingRulesBanner(figmaOrder))

        assertEquals(LocalDate.parse("2027-06-03"), banner.effectiveOn)
        assertEquals(listOf("drink", "repeat"), banner.items.map { it.id })
    }

    @Test
    fun `no banner when every scheduled change was heard or nothing is scheduled`() {
        assertNull(upcomingRulesBanner(figmaOrder.map { it.copy(alreadyHeard = true) }))
        assertNull(upcomingRulesBanner(figmaOrder.filter { it.status == HomePreviewStatus.Active }))
    }

    @Test
    fun `pre-departure alert takes the first sentence from the banner group`() {
        val withMessages = figmaOrder.map {
            when (it.id) {
                "repeat" -> it.copy(alertMessage = "반복")
                "highway" -> it.copy(alertMessage = "이미 들음")
                else -> it
            }
        }
        assertEquals("repeat", preDepartureAlert(withMessages)?.id)
        assertNull(preDepartureAlert(figmaOrder))
    }
}
