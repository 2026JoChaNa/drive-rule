package com.jochana.driverule.feature.card

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Card scene motion designed in the app (Figma cannot express motion; see docs/android/motion-spec.md).
 * Coordinates are Figma px in the 759 × 677 `Scene (crop)` space. Heading is degrees clockwise from
 * north (screen up), matching the car artwork which points north.
 */
internal data class Pose(val x: Float, val y: Float, val heading: Float)

/** Turtle-style path: straight runs and circular arcs, so heading always follows the tangent. */
internal class ScenePath(startX: Float, startY: Float, startHeading: Float) {
    private sealed interface Segment {
        val length: Float
        fun poseAt(distance: Float): Pose
    }

    private class Straight(val from: Pose, override val length: Float) : Segment {
        override fun poseAt(distance: Float): Pose {
            val h = Math.toRadians(from.heading.toDouble())
            return Pose(
                (from.x + distance * sin(h)).toFloat(),
                (from.y - distance * cos(h)).toFloat(),
                from.heading,
            )
        }
    }

    private class Arc(val from: Pose, val radius: Float, val degrees: Float, val right: Boolean) : Segment {
        override val length = (radius * degrees * PI / 180.0).toFloat()
        private val h0 = Math.toRadians(from.heading.toDouble())
        // Right turns pivot around the point on the driver's right, left turns on the left.
        private val cx = if (right) from.x + radius * cos(h0) else from.x - radius * cos(h0)
        private val cy = if (right) from.y + radius * sin(h0) else from.y - radius * sin(h0)

        override fun poseAt(distance: Float): Pose {
            val turned = distance / radius * (if (right) 1f else -1f)
            val h = h0 + turned
            val x = if (right) cx - radius * cos(h) else cx + radius * cos(h)
            val y = if (right) cy - radius * sin(h) else cy + radius * sin(h)
            return Pose(x.toFloat(), y.toFloat(), (from.heading + Math.toDegrees(turned.toDouble())).toFloat())
        }
    }

    private val segments = mutableListOf<Segment>()
    private var end = Pose(startX, startY, startHeading)

    val length: Float get() = segments.sumOf { it.length.toDouble() }.toFloat()
    val endPose: Pose get() = end

    fun forward(length: Float) = apply { add(Straight(end, length)) }
    fun turnRight(radius: Float, degrees: Float) = apply { add(Arc(end, radius, degrees, right = true)) }
    fun turnLeft(radius: Float, degrees: Float) = apply { add(Arc(end, radius, degrees, right = false)) }

    private fun add(segment: Segment) {
        segments += segment
        end = segment.poseAt(segment.length)
    }

    /** Pose after travelling [distance] (clamped to the path). */
    fun poseAt(distance: Float): Pose {
        var left = distance.coerceIn(0f, length)
        for (segment in segments) {
            if (left <= segment.length) return segment.poseAt(left)
            left -= segment.length
        }
        return end
    }
}

/** Material 3 motion curves plus the two used for vehicles. */
internal object SceneEasing {
    /** Arrive and brake to a gentle stop: full speed in, decelerating out. */
    val Brake: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    /** Pull away from rest and keep speed when leaving the frame. */
    val PullAway: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    /** People and highlights: symmetric start and stop. */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/** Eased 0..1 progress of [t] between [startMs] and [endMs]. */
internal fun phase(t: Float, startMs: Int, endMs: Int, easing: Easing = SceneEasing.Standard): Float =
    easing.transform(((t - startMs) / (endMs - startMs)).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * One scene = approach → stop at the rule point → proceed safely → settle on the Figma frame.
 * Played once when the card opens (and again on tap); the last frame equals the static design.
 */
internal object SceneScripts {

    // H-1 신호등 없는 횡단보도: car stops before the crosswalk, stop line lights up, then turns right.
    object Crosswalk {
        const val TOTAL_MS = 5_600
        /** `Car` node centre at rest (inner scene offset -356, -80.836 applied). */
        val rest = Pose(448.13f, 518.62f, 0f)
        private val approach = ScenePath(rest.x, rest.y + 260f, 0f).forward(260f)
        private val proceed = ScenePath(rest.x, rest.y, 0f).forward(99f).turnRight(150f, 90f).forward(360f)

        fun car(t: Float): Pose = when {
            t < 1_500 -> approach.poseAt(approach.length * phase(t, 0, 1_500, SceneEasing.Brake))
            t < 2_700 -> rest
            t < 4_900 -> proceed.poseAt(proceed.length * phase(t, 2_700, 4_900, SceneEasing.PullAway))
            else -> rest
        }

        fun carAlpha(t: Float): Float = when {
            t < 4_900 -> 1f
            t < 5_200 -> 0f
            else -> phase(t, 5_200, TOTAL_MS)
        }

        fun stopLineAlpha(t: Float): Float = when {
            t < 2_600 -> phase(t, 1_100, 1_400)
            t < 5_200 -> 1f - phase(t, 2_600, 2_900)
            else -> phase(t, 5_200, TOTAL_MS)
        }
    }

    // I-1 우회전 후 횡단보도: car waits at the stop mark while the pedestrian crosses, then goes straight on.
    object RightTurn {
        const val TOTAL_MS = 6_100
        /** `Car` box centre at rest, facing east (inner scene offset -420, 59.164 applied). */
        val rest = Pose(339.21f, 405.97f, 90f)
        /** `Pedestrian` centre at rest, on the east crosswalk. */
        const val PEDESTRIAN_X = 464.6f
        const val PEDESTRIAN_REST_Y = 304.6f
        private const val PEDESTRIAN_START_Y = 183f
        private const val PEDESTRIAN_END_Y = 510f
        private val approach = ScenePath(rest.x - 500f, rest.y, 90f).forward(500f)
        private val proceed = ScenePath(rest.x, rest.y, 90f).forward(520f)

        fun car(t: Float): Pose = when {
            t < 1_500 -> approach.poseAt(approach.length * phase(t, 0, 1_500, SceneEasing.Brake))
            t < 3_800 -> rest
            t < 5_600 -> proceed.poseAt(proceed.length * phase(t, 3_800, 5_600, SceneEasing.PullAway))
            else -> rest
        }

        fun carAlpha(t: Float): Float = if (t < 5_600) 1f else phase(t, 5_700, TOTAL_MS)

        fun pedestrianY(t: Float): Float = when {
            t < 5_600 -> lerp(PEDESTRIAN_START_Y, PEDESTRIAN_END_Y, phase(t, 1_300, 3_700, SceneEasing.Standard))
            else -> PEDESTRIAN_REST_Y
        }

        fun pedestrianAlpha(t: Float): Float = when {
            t < 5_600 -> 1f
            t < 5_800 -> 0f
            else -> phase(t, 5_800, TOTAL_MS)
        }
    }

    // A-1 고속도로 정차·주차: car leaves the lane for the shoulder and stops; P appears; the camera flashes once.
    object Highway {
        const val TOTAL_MS = 2_600
        /** `Car` node centre at rest on the shoulder. */
        val rest = Pose(577.08f, 397.07f, 0f)
        private const val LANE_X = 400f
        private const val SWERVE_DEGREES = 40f
        // Two opposite arcs of this radius shift the car sideways by (rest.x - LANE_X) and end facing north.
        private val swerveRadius =
            ((rest.x - LANE_X) / (2 * (1 - cos(Math.toRadians(SWERVE_DEGREES.toDouble()))))).toFloat()
        private val swerveForward = (2 * swerveRadius * sin(Math.toRadians(SWERVE_DEGREES.toDouble()))).toFloat()
        private val approach = ScenePath(LANE_X, rest.y + swerveForward + 30f, 0f)
            .forward(30f).turnRight(swerveRadius, SWERVE_DEGREES).turnLeft(swerveRadius, SWERVE_DEGREES)

        val approachEnd: Pose get() = approach.endPose

        fun car(t: Float): Pose =
            if (t < 1_800) approach.poseAt(approach.length * phase(t, 0, 1_800, SceneEasing.Brake)) else rest

        /** `Icon/Parking` pops in once the car has stopped. */
        fun parkingScale(t: Float): Float = lerp(0.6f, 1f, phase(t, 1_700, 2_000, SceneEasing.EmphasizedDecelerate))
        fun parkingAlpha(t: Float): Float = phase(t, 1_700, 2_000)

        /** Camera lens flash: up then down once. */
        fun flashAlpha(t: Float): Float = when {
            t < 2_250 -> phase(t, 2_100, 2_250)
            else -> 1f - phase(t, 2_250, 2_500)
        }
    }

    // B-1 목적지 보호구역: one ripple spreads from the destination to the 300 m zone edge.
    object Destination {
        const val TOTAL_MS = 1_400
        const val CENTER_X = 452.94f
        const val CENTER_Y = 293.29f
        const val START_RADIUS = 28.92f
        const val ZONE_RADIUS = 191.07f

        fun rippleRadius(t: Float): Float =
            lerp(START_RADIUS, ZONE_RADIUS, phase(t, 0, TOTAL_MS, SceneEasing.EmphasizedDecelerate))

        fun rippleAlpha(t: Float): Float = if (t >= TOTAL_MS) 0f else 0.55f * (1f - phase(t, 200, TOTAL_MS))
    }

    fun totalMillis(scene: CardScene): Int = when (scene) {
        CardScene.HighwayShoulder -> Highway.TOTAL_MS
        CardScene.SchoolZoneDestination -> Destination.TOTAL_MS
        CardScene.UnsignalizedCrosswalk -> Crosswalk.TOTAL_MS
        CardScene.RightTurnCrosswalk -> RightTurn.TOTAL_MS
    }
}
