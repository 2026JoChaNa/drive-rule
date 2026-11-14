package com.jochana.driverule.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * App-level transition timings. Figma defines no motion for these surfaces (checked with
 * get_motion_context on 2026-11-14), so these are short app defaults, not design values:
 * kept at 180–260 ms so they do not pull the driver's eyes. Compose scales them with the
 * system animator duration, so "Remove animations" turns them off.
 */
object DriveRuleMotion {
    const val ENTER_MILLIS = 250
    const val EXIT_MILLIS = 180
    const val SCREEN_MILLIS = 220

    fun <T> enter() = tween<T>(ENTER_MILLIS, easing = LinearOutSlowInEasing)
    fun <T> exit() = tween<T>(EXIT_MILLIS, easing = FastOutLinearInEasing)
    fun <T> screen() = tween<T>(SCREEN_MILLIS, easing = LinearOutSlowInEasing)
}
