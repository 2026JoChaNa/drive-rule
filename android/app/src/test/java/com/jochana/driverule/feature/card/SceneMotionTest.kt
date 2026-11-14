package com.jochana.driverule.feature.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMotionTest {
    private val eps = 1e-3f

    @Test
    fun `track returns keyframe values exactly at keyframes`() {
        val car = FigmaSceneMotion.crosswalkCarY
        assertEquals(184.465637f, car.valueAt(0f), eps)
        assertEquals(0f, car.valueAt(0.18182f), eps)
        assertEquals(0f, car.valueAt(0.5f), eps)
        assertEquals(-262.50885f, car.valueAt(1f), eps)
    }

    @Test
    fun `ease-out approach covers more than half the distance in the first half of the segment`() {
        val car = FigmaSceneMotion.crosswalkCarY
        val halfway = car.valueAt(0.18182f / 2)
        // Linear would be 92.2; ease-out decelerates, so the car is already past the midpoint.
        assertTrue("ease-out expected, got $halfway", halfway < 184.465637f / 2)
    }

    @Test
    fun `highlight is visible only while the car waits at the stop line`() {
        val alpha = FigmaSceneMotion.crosswalkHighlightAlpha
        assertEquals(0f, alpha.valueAt(0.10f), eps)
        assertEquals(1f, alpha.valueAt(0.30f), eps)
        assertEquals(1f, alpha.valueAt(0.54f), eps)
        assertEquals(0f, alpha.valueAt(0.70f), eps)
    }

    @Test
    fun `car turns a quarter clockwise and the pedestrian crosses before the turn`() {
        assertEquals(Math.PI.toFloat() / 2, FigmaSceneMotion.carRotation.valueAt(0.95f), eps)
        assertEquals(0f, FigmaSceneMotion.carRotation.valueAt(0.55f), eps)
        assertEquals(292.915222f, FigmaSceneMotion.rightTurnPedestrianX.valueAt(0.55f), eps)
        assertEquals(-241.224335f, FigmaSceneMotion.rightTurnPedestrianX.valueAt(0.05f), eps)
    }

    @Test
    fun `fractions outside the cycle are clamped`() {
        val x = FigmaSceneMotion.rightTurnCarX
        assertEquals(x.valueAt(0f), x.valueAt(-1f), eps)
        assertEquals(x.valueAt(1f), x.valueAt(2f), eps)
    }
}
