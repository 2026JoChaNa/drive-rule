package com.jochana.driverule.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset

/**
 * App motion (docs/android/motion-spec.md). Figma cannot express motion, so these are app-designed
 * values on Material 3 curves. Everything stays under 300 ms so it marks a change without holding the
 * driver's eyes. Compose follows the system animator scale, so "Remove animations" turns all of it off.
 */
object DriveRuleMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    private const val AXIS_MILLIS = 300
    private const val OUT_FADE_MILLIS = 90
    private const val IN_FADE_MILLIS = 210

    /**
     * Shared X axis: moving deeper (home → list → card) slides 30 dp from the right, back slides from
     * the left. The old screen fades out within 90 ms before the new one fades in, so they never overlap.
     */
    fun sharedAxisX(forward: Boolean, distancePx: Int): ContentTransform {
        val dir = if (forward) 1 else -1
        return (slideInHorizontally(tween(AXIS_MILLIS, easing = Standard)) { dir * distancePx } +
            fadeIn(tween(IN_FADE_MILLIS, delayMillis = OUT_FADE_MILLIS, easing = Standard))) togetherWith
            (slideOutHorizontally(tween(AXIS_MILLIS, easing = Standard)) { -dir * distancePx } +
                fadeOut(tween(OUT_FADE_MILLIS, easing = EmphasizedAccelerate)))
    }

    /** Fade through: for unrelated steps (setup → permission guide → home). */
    fun fadeThrough(): ContentTransform =
        (fadeIn(tween(IN_FADE_MILLIS, delayMillis = OUT_FADE_MILLIS, easing = Standard)) +
            scaleIn(tween(IN_FADE_MILLIS, delayMillis = OUT_FADE_MILLIS, easing = Standard), initialScale = 0.96f)) togetherWith
            fadeOut(tween(OUT_FADE_MILLIS, easing = EmphasizedAccelerate))

    /** Pre-departure alert: rises 24 dp and grows from 96 % at its bottom-left anchor. */
    fun alertEnter(risePx: Int): EnterTransition =
        fadeIn(tween(280, easing = EmphasizedDecelerate)) +
            slideInVertically(tween(280, easing = EmphasizedDecelerate)) { risePx } +
            scaleIn(tween(280, easing = EmphasizedDecelerate), initialScale = 0.96f, transformOrigin = TransformOrigin(0f, 1f))

    fun alertExit(dropPx: Int): ExitTransition =
        fadeOut(tween(180, easing = EmphasizedAccelerate)) +
            slideOutVertically(tween(180, easing = EmphasizedAccelerate)) { dropPx }

    /** Bottom sheet: critically damped spring up (no bounce), accelerating slide down. */
    val sheetEnter: EnterTransition =
        slideInVertically(spring<IntOffset>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) { it }
    val sheetExit: ExitTransition = slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it }
    val scrimEnter: EnterTransition = fadeIn(tween(250, easing = Standard))
    val scrimExit: ExitTransition = fadeOut(tween(200, easing = EmphasizedAccelerate))
}
