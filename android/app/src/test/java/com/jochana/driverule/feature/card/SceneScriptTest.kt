package com.jochana.driverule.feature.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class SceneScriptTest {
    private val eps = 0.05f

    private fun assertPose(expected: Pose, actual: Pose) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
        assertEquals("heading", expected.heading, actual.heading, eps)
    }

    @Test
    fun `right turn of 90 degrees ends one radius over and one radius up facing east`() {
        val path = ScenePath(0f, 0f, 0f).turnRight(100f, 90f)
        assertPose(Pose(100f, -100f, 90f), path.endPose)
        assertEquals((Math.PI * 50).toFloat(), path.length, eps)
    }

    @Test
    fun `heading follows the tangent along an arc`() {
        val path = ScenePath(0f, 0f, 0f).turnRight(100f, 90f)
        val a = path.poseAt(path.length * 0.5f)
        val b = path.poseAt(path.length * 0.5f + 0.5f)
        // Direction of travel between two close samples, converted to degrees clockwise from north.
        val travelled = Math.toDegrees(atan2((b.x - a.x).toDouble(), -(b.y - a.y).toDouble())).toFloat()
        assertEquals(a.heading, travelled, 0.5f)
    }

    @Test
    fun `every scene ends on its Figma rest frame`() {
        assertPose(SceneScripts.Crosswalk.rest, SceneScripts.Crosswalk.car(SceneScripts.Crosswalk.TOTAL_MS.toFloat()))
        assertEquals(1f, SceneScripts.Crosswalk.carAlpha(SceneScripts.Crosswalk.TOTAL_MS.toFloat()), eps)
        assertEquals(1f, SceneScripts.Crosswalk.stopLineAlpha(SceneScripts.Crosswalk.TOTAL_MS.toFloat()), eps)

        val endI = SceneScripts.RightTurn.TOTAL_MS.toFloat()
        assertPose(SceneScripts.RightTurn.rest, SceneScripts.RightTurn.car(endI))
        assertEquals(SceneScripts.RightTurn.PEDESTRIAN_REST_Y, SceneScripts.RightTurn.pedestrianY(endI), eps)
        assertEquals(1f, SceneScripts.RightTurn.pedestrianAlpha(endI), eps)

        assertPose(SceneScripts.Highway.rest, SceneScripts.Highway.car(SceneScripts.Highway.TOTAL_MS.toFloat()))
        assertEquals(1f, SceneScripts.Highway.parkingAlpha(SceneScripts.Highway.TOTAL_MS.toFloat()), eps)
        assertEquals(0f, SceneScripts.Destination.rippleAlpha(SceneScripts.Destination.TOTAL_MS.toFloat()), eps)
    }

    @Test
    fun `cars arrive exactly at their stop pose without a jump`() {
        assertPose(SceneScripts.Crosswalk.rest, SceneScripts.Crosswalk.car(1_499.9f))
        assertPose(SceneScripts.RightTurn.stopPose, SceneScripts.RightTurn.car(1_799.9f))
        // The shoulder swerve is two opposite arcs and must land on the rest pose by construction.
        assertPose(SceneScripts.Highway.rest, SceneScripts.Highway.approachEnd)
        assertPose(SceneScripts.Highway.rest, SceneScripts.Highway.car(1_799.9f))
    }

    @Test
    fun `motion is continuous frame to frame`() {
        // At 60 fps no step may jump more than a car length's fraction while the car is visible.
        for ((pose, alpha) in listOf(
            SceneScripts.Crosswalk::car to SceneScripts.Crosswalk::carAlpha,
            SceneScripts.RightTurn::car to SceneScripts.RightTurn::carAlpha,
            SceneScripts.Highway::car to { _: Float -> 1f },
        )) {
            var t = 0f
            while (t < 6_000f) {
                val a = pose(t)
                val b = pose(t + 16.7f)
                if (alpha(t) > 0f && alpha(t + 16.7f) > 0f) {
                    val step = hypot(b.x - a.x, b.y - a.y)
                    assertTrue("jump of $step px at $t ms", step < 25f)
                    assertTrue("heading jump at $t ms", abs(b.heading - a.heading) < 6f)
                }
                t += 16.7f
            }
        }
    }

    @Test
    fun `right-turn car comes up the northbound lane, waits mid-turn, then completes the turn east`() {
        val s = SceneScripts.RightTurn
        // Approaching: heading north, below the stop.
        assertEquals(0f, s.car(300f).heading, eps)
        // Waiting part-way through the right turn, front bumper short of the red stop mark (x 421.5).
        assertEquals(15f, s.stopPose.heading, 0.5f)
        val frontX = s.stopPose.x + s.CAR_HALF_LENGTH * kotlin.math.sin(Math.toRadians(s.stopPose.heading.toDouble())).toFloat()
        assertTrue("front $frontX past the stop mark", frontX < 421.5f)
        // Leaving: the turn is completed, facing east.
        assertEquals(90f, s.car(5_500f).heading, eps)
    }

    @Test
    fun `pedestrian crosses north to south while the car waits and never touches it`() {
        val s = SceneScripts.RightTurn
        assertTrue(s.pedestrianY(1_000f) < s.pedestrianY(2_500f))
        assertTrue(s.pedestrianY(2_500f) < s.pedestrianY(3_800f))
        // The car is still waiting until the crossing is done.
        assertPose(s.stopPose, s.car(3_800f))
        val h = Math.toRadians(s.stopPose.heading.toDouble())
        val frontX = s.stopPose.x + s.CAR_HALF_LENGTH * kotlin.math.sin(h).toFloat()
        val frontY = s.stopPose.y - s.CAR_HALF_LENGTH * kotlin.math.cos(h).toFloat()
        var t = 1_200f
        while (t <= 3_800f) {
            val gap = hypot(frontX - s.PEDESTRIAN_X, frontY - s.pedestrianY(t)) - s.PEDESTRIAN_RADIUS
            assertTrue("pedestrian touches the car at $t ms (gap $gap)", gap > 0f)
            t += 50f
        }
    }
}
