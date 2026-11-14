package com.jochana.driverule.feature.card

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jochana.driverule.ui.components.FigmaImage
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/** Figma `Scene (crop)` export box. All layer positions below are in this space (Figma px). */
private const val SCENE_W = 759f
private const val SCENE_H = 677f

/** Scene-only Figma variables (H-1/I-1 `get_variable_defs`, night / day). */
private class SceneColors(dark: Boolean) {
    val ground = if (dark) Color(0xFF1D1E21) else Color(0xFFE0E1E6)
    val red = if (dark) Color(0xFFFF4C28) else Color(0xFFC7321A)
    val pink = if (dark) Color(0xFFFF7EB6) else Color(0xFFC2185B)
    val system = if (dark) Color(0xFF131417) else Color(0xFFEDEEF2)
}

/** `true` when the user turned animations off (Remove animations / animator scale 0). */
@Composable
internal fun animationsDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Whether [scene] has a Figma motion definition this file can play. */
internal fun CardScene.hasMotion(): Boolean =
    this == CardScene.UnsignalizedCrosswalk || this == CardScene.RightTurnCrosswalk

/**
 * H-1 / I-1 scenes rebuilt from separate Figma layers so the car, pedestrian and stop highlight
 * can follow the Figma keyframes (6.6 s loop). Cropped like the static export: fill the slot,
 * centred horizontally, top aligned. Callers fall back to the static export when animations are
 * off, which equals the Figma frame at rest.
 */
@Composable
internal fun AnimatedCardScene(scene: CardScene, modifier: Modifier = Modifier) {
    val dark = LocalDriveRuleColors.current.isDark
    val colors = remember(dark) { SceneColors(dark) }
    val transition = rememberInfiniteTransition(label = "card-scene")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FigmaSceneMotion.CYCLE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "card-scene-cycle",
    )
    BoxWithConstraints(modifier.background(colors.ground).testTag("card_scene_motion_${scene.name}")) {
        val scale = maxOf(maxWidth.value / SCENE_W, maxHeight.value / SCENE_H)
        val left = (maxWidth.value - SCENE_W * scale) / 2f
        fun x(px: Float): Dp = (left + px * scale).dp
        fun y(px: Float): Dp = (px * scale).dp
        fun s(px: Float): Dp = (px * scale).dp

        when (scene) {
            CardScene.UnsignalizedCrosswalk -> {
                // Inner `Scene · 신호등 없는 횡단보도 정지` (1849:28615) sits at (-356, -80.836).
                val ox = -356f
                val oy = -80.8359f
                Layer("road_crosswalk", x(0f), y(0f), s(SCENE_W), s(585f))
                Box(
                    Modifier.placeAt(x(ox + 737.51f), y(oy + 496.64f), s(134.092f), s(14.899f))
                        .graphicsLayer { alpha = FigmaSceneMotion.crosswalkHighlightAlpha.valueAt(cycle) }
                        .background(colors.red, RoundedCornerShape(s(7.45f))),
                )
                // `Car` (1849:28705): node box 90.814 × 158.924, image overflows it for the shadow.
                SceneCar(
                    asset = "scene_car_crosswalk",
                    imageLeft = x(ox + 758.72f - 15.611f), imageTop = y(oy + 520f - 8.502f),
                    imageWidth = s(122.031f), imageHeight = s(192.979f),
                    baseRotationDegrees = 0f,
                    translationX = FigmaSceneMotion.crosswalkCarX.valueAt(cycle) * scale,
                    translationY = FigmaSceneMotion.crosswalkCarY.valueAt(cycle) * scale,
                    rotationRadians = FigmaSceneMotion.carRotation.valueAt(cycle),
                )
            }
            CardScene.RightTurnCrosswalk -> {
                // Inner `Scene · 우회전 후 횡단보도 정지` (1849:29276) sits at (-420, 59.164).
                val ox = -420f
                val oy = 59.1641f
                Layer("road_right_turn", x(0f), y(oy), s(SCENE_W), s(618f))
                Pedestrian(
                    left = x(ox + 839.8f), top = y(oy + 200.6f), size = s(89.598f),
                    translationX = FigmaSceneMotion.rightTurnPedestrianX.valueAt(cycle) * scale,
                    scale = scale, colors = colors,
                )
                // `Car` (1849:29366): a vertical car turned 90° clockwise in a 154.384 × 88.219 box.
                // Its local forward motion maps to screen as (-y, x) under that turn.
                val carX = FigmaSceneMotion.rightTurnCarX.valueAt(cycle)
                val carY = FigmaSceneMotion.rightTurnCarY.valueAt(cycle)
                val centerX = ox + 682.02f + 154.384f / 2f
                val centerY = oy + 302.69f + 88.219f / 2f
                SceneCar(
                    asset = "scene_car_right_turn",
                    imageLeft = x(centerX - 88.219f / 2f - 15.165f), imageTop = y(centerY - 154.384f / 2f - 8.26f),
                    imageWidth = s(118.545f), imageHeight = s(187.466f),
                    baseRotationDegrees = 90f,
                    translationX = -carY * scale,
                    translationY = carX * scale,
                    rotationRadians = FigmaSceneMotion.carRotation.valueAt(cycle),
                )
                Box(
                    Modifier.placeAt(x(ox + 841.5f), y(oy + 290.7f), s(11.9f), s(112.2f))
                        .background(colors.red),
                )
                Layer("walk_signal", x(ox + 931.6f), y(oy + 30.6f), s(71.4f), s(125.8f))
            }
            else -> Unit
        }
    }
}

@Composable
private fun Layer(asset: String, left: Dp, top: Dp, width: Dp, height: Dp) {
    FigmaImage(
        asset,
        Modifier.placeAt(left, top, width, height),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * The car pivots around the centre of its Figma node box, not of its (shadow-padded) image:
 * 0.5 horizontally and 0.4558 of the image height (8.5 px top pad, 25.6 px bottom shadow).
 */
@Composable
private fun SceneCar(
    asset: String,
    imageLeft: Dp,
    imageTop: Dp,
    imageWidth: Dp,
    imageHeight: Dp,
    baseRotationDegrees: Float,
    translationX: Float,
    translationY: Float,
    rotationRadians: Float,
) {
    FigmaImage(
        asset,
        Modifier.placeAt(imageLeft, imageTop, imageWidth, imageHeight)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.4558f)
                rotationZ = baseRotationDegrees + Math.toDegrees(rotationRadians.toDouble()).toFloat()
                this.translationX = translationX.dp.toPx()
                this.translationY = translationY.dp.toPx()
            },
        contentScale = ContentScale.FillBounds,
    )
}

/** I-1 `Pedestrian` (1849:29364): pink disc, system-colour ring, warm glow, user glyph. */
@Composable
private fun Pedestrian(left: Dp, top: Dp, size: Dp, translationX: Float, scale: Float, colors: SceneColors) {
    val glow = Color(0x73FFB23D) // rgba(255,178,61,0.45)
    Box(
        Modifier.placeAt(left, top, size, size)
            .graphicsLayer { this.translationX = translationX.dp.toPx() }
            .drawBehind {
                // CSS shadow 0 0 31px 3.4px, approximated with a radial fade.
                val radius = this.size.minDimension / 2f
                val outer = radius + (3.446f + 31.015f) * scale * density
                drawCircle(
                    Brush.radialGradient(
                        0f to glow, (radius / outer) to glow, 1f to Color.Transparent,
                        center = Offset(this.size.width / 2f, this.size.height / 2f), radius = outer,
                    ),
                    radius = outer,
                )
            }
            .background(colors.pink, CircleShape)
            .border((5.169f * scale).dp, colors.system, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FigmaImage("walker", Modifier.requiredSize((55.137f * scale).dp), contentScale = ContentScale.FillBounds)
    }
}

/** Places a layer at Figma coordinates even when it is larger than the slot (no re-centring). */
private fun Modifier.placeAt(left: Dp, top: Dp, width: Dp, height: Dp): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).offset(left, top).requiredSize(width, height)
