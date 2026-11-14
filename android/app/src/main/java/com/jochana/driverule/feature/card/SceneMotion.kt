package com.jochana.driverule.feature.card

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/** CSS `ease-in` / `ease-out`, as used by the Figma motion export. */
internal val CssEaseIn: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
internal val CssEaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** A keyframe at [percent] of the cycle. [easing] shapes the segment that starts here (CSS semantics). */
internal data class Keyframe(val percent: Float, val value: Float, val easing: Easing = LinearEasing)

/** Piecewise keyframe track over one cycle, matching CSS `@keyframes` interpolation. */
internal class KeyframeTrack(private val frames: List<Keyframe>) {
    init {
        require(frames.isNotEmpty()) { "track needs at least one keyframe" }
        require(frames.zipWithNext().all { (a, b) -> a.percent < b.percent }) { "keyframes must be ordered" }
    }

    /** [fraction] is the position in the cycle, 0..1. */
    fun valueAt(fraction: Float): Float {
        val percent = fraction.coerceIn(0f, 1f) * 100f
        if (percent <= frames.first().percent) return frames.first().value
        for ((a, b) in frames.zipWithNext()) {
            if (percent <= b.percent) {
                val local = (percent - a.percent) / (b.percent - a.percent)
                return a.value + (b.value - a.value) * a.easing.transform(local)
            }
        }
        return frames.last().value
    }
}

/**
 * Figma motion export (get_motion_context, 2026-11-14) for H-1 (`1849:28391`) and I-1
 * (`1849:29053`). Day frames (`1870:31790`, `1870:32087`) carry identical tracks.
 * Distances are Figma px in the scene; rotation is radians, clockwise.
 */
internal object FigmaSceneMotion {
    const val CYCLE_MILLIS = 6_600

    /** H-1 `Stop highlight` (1849:28700) opacity. */
    val crosswalkHighlightAlpha = KeyframeTrack(listOf(
        Keyframe(0f, 0f),
        Keyframe(15.152f, 0f, CssEaseOut),
        Keyframe(19.697f, 1f),
        Keyframe(54.545f, 1f, CssEaseIn),
        Keyframe(59.091f, 0f),
        Keyframe(100f, 0f),
    ))

    /** Car rotation: identical for H-1 (1849:28705) and I-1 (1849:29366). */
    val carRotation = KeyframeTrack(listOf(
        Keyframe(0f, 0f),
        Keyframe(59.091f, 0f, CssEaseIn),
        Keyframe(66.667f, 0.087266f),
        Keyframe(74.242f, 0.872665f),
        Keyframe(80.303f, 1.53589f, CssEaseOut),
        Keyframe(90.909f, 1.570796f),
        Keyframe(100f, 1.570796f),
    ))

    val crosswalkCarX = carTrackX(2.837934f, 75.205231f, 202.912247f, 515.084839f)
    val crosswalkCarY = carTrackY(184.465637f, -130.544937f, -231.291565f, -262.50885f)
    val rightTurnCarX = carTrackX(2.75685f, 73.056511f, 197.114761f, 500.368164f)
    val rightTurnCarY = carTrackY(179.19519f, -126.815086f, -224.683243f, -255.008591f)

    /** I-1 `Pedestrian` (1849:29364) horizontal offset. */
    val rightTurnPedestrianX = KeyframeTrack(listOf(
        Keyframe(0f, -241.224335f),
        Keyframe(12.121f, -241.224335f),
        Keyframe(51.515f, 292.915222f),
        Keyframe(100f, 292.915222f),
    ))

    private fun carTrackX(a: Float, b: Float, c: Float, end: Float) = KeyframeTrack(listOf(
        Keyframe(0f, 0f),
        Keyframe(59.091f, 0f, CssEaseIn),
        Keyframe(66.667f, a),
        Keyframe(74.242f, b),
        Keyframe(80.303f, c, CssEaseOut),
        Keyframe(90.909f, end),
        Keyframe(100f, end),
    ))

    private fun carTrackY(start: Float, a: Float, b: Float, end: Float) = KeyframeTrack(listOf(
        Keyframe(0f, start, CssEaseOut),
        Keyframe(18.182f, 0f),
        Keyframe(59.091f, 0f, CssEaseIn),
        Keyframe(66.667f, a),
        Keyframe(74.242f, b),
        Keyframe(80.303f, end),
        Keyframe(100f, end),
    ))
}
