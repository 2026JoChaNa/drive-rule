package com.jochana.driverule.feature.card

import com.jochana.driverule.domain.repository.CardReportTarget
import com.jochana.driverule.feature.home.HomePreviewStatus

/** Event tags from Figma `이벤트 태그` (1848:27392). Not the server `eventTag` wire enum. */
enum class CardEventTag(val icon: String) {
    SchoolZoneEntry("tag_child_zone"),
    Parking("tag_parking"),
    RightTurnCrosswalk("tag_turn_right"),
}

/**
 * Scene illustrations exported from the Figma `Scene (crop)` frames (A-1 · B-1 · H-1 · I-1).
 * [designWidth]/[designHeight] are the exported SVG root dimensions.
 */
enum class CardScene(val asset: String, val designWidth: Float, val designHeight: Float, val fillsWindow: Boolean) {
    HighwayShoulder("scene_highway", 759f, 677f, fillsWindow = true),
    SchoolZoneDestination("scene_destination", 759f, 499f, fillsWindow = false),
    UnsignalizedCrosswalk("scene_crosswalk", 759f, 677f, fillsWindow = true),
    RightTurnCrosswalk("scene_right_turn", 759f, 677f, fillsWindow = true),
}

/** The three rows of the Figma evidence sheet (A-2 · B-2 · H-2 · I-2). */
data class CardEvidencePreview(
    val law: String,
    val dates: String,
    val penalty: String,
    /** Official law page. `null` disables 'law source'; the app never guesses a URL. */
    val sourceUrl: String? = null,
    /** `null` for sample cards: the report button stays disabled and nothing is sent. */
    val reportTarget: CardReportTarget? = null,
)

/**
 * Display model for the parked card detail. Sample instances come only from the debug
 * `AppEnvironment` and are labelled as samples on screen; they are not reviewed law text.
 */
data class CardDetailPreview(
    val id: String,
    val status: HomePreviewStatus,
    val tag: CardEventTag,
    val message: String,
    val scene: CardScene,
    val evidence: CardEvidencePreview,
    val legend: String? = null,
)
