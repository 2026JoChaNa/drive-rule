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
        assertPose(SceneScripts.RightTurn.rest, SceneScripts.RightTurn.car(1_499.9f))
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
    fun `pedestrian crosses north to south while the car waits`() {
        val s = SceneScripts.RightTurn
        assertTrue(s.pedestrianY(1_000f) < s.pedestrianY(2_500f))
        assertTrue(s.pedestrianY(2_500f) < s.pedestrianY(3_700f))
        // The car is still at the stop mark until the crossing is done.
        assertPose(s.rest, s.car(3_700f))
    }
}
