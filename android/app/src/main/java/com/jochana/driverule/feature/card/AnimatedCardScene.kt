package com.jochana.driverule.feature.card

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jochana.driverule.R
import com.jochana.driverule.ui.components.FigmaImage
import com.jochana.driverule.ui.theme.LocalDriveRuleColors

/** Figma `Scene (crop)` export box. All layer positions below are in this space (Figma px). */
private const val SCENE_W = 759f
private const val SCENE_H = 677f

/** Car artwork pivots on the centre of its Figma node box (0.4558 of the shadow-padded image height). */
private val CAR_PIVOT = TransformOrigin(0.5f, 0.4558f)

/** Scene-only Figma variables (`get_variable_defs`, night / day). */
private class SceneColors(dark: Boolean) {
    val ground = if (dark) Color(0xFF1D1E21) else Color(0xFFE0E1E6)
    val road = if (dark) Color(0xFF44464E) else Color(0xFFA4A8AE)
    val marking = Color.White
    val red = if (dark) Color(0xFFFF4C28) else Color(0xFFC7321A)
    val pink = if (dark) Color(0xFFFF7EB6) else Color(0xFFC2185B)
    val system = if (dark) Color(0xFF131417) else Color(0xFFEDEEF2)
    val tertiary = if (dark) Color(0xFFA4A8AE) else Color(0xFF686A72)
    val secondary = if (dark) Color(0xFFC7C9CE) else Color(0xFF44464E)
    /** `Zone 300m` fill/stroke in the B-1 map export. */
    val zone = Color(0xFFFF4D4F)
}

/** `true` when the user turned animations off (Remove animations / animator scale 0). */
@Composable
internal fun animationsDisabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Layered card scene that plays its [SceneScripts] story once and settles on the Figma frame.
 * Tapping replays it. Positions are Figma px, cropped like the static export (fill, top-centre),
 * except the B-1 map which keeps its aspect ratio. Animated values are read only in draw/layer
 * lambdas, so playback redraws without recomposing.
 */
@Composable
internal fun AnimatedCardScene(scene: CardScene, modifier: Modifier = Modifier) {
    val dark = LocalDriveRuleColors.current.isDark
    val colors = remember(dark) { SceneColors(dark) }
    val total = SceneScripts.totalMillis(scene).toFloat()
    val clock = remember(scene) { Animatable(0f) }
    var plays by remember(scene) { mutableIntStateOf(0) }
    LaunchedEffect(scene, plays) {
        clock.snapTo(0f)
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
    }
    val replayLabel = stringResource(R.string.card_scene_replay)
    val t: () -> Float = { clock.value }

    BoxWithConstraints(
        modifier
            .background(colors.ground)
            .pointerInput(scene) { detectTapGestures { plays++ } }
            .semantics { onClick(label = replayLabel) { plays++; true } }
            .testTag("card_scene_motion_${scene.name}"),
    ) {
        val fill = scene != CardScene.SchoolZoneDestination
        val scale = if (fill) maxOf(maxWidth.value / SCENE_W, maxHeight.value / SCENE_H) else maxWidth.value / SCENE_W
        val left = if (fill) (maxWidth.value - SCENE_W * scale) / 2f else 0f
        val geo = SceneGeometry(scale, left)

        when (scene) {
            CardScene.UnsignalizedCrosswalk -> CrosswalkScene(geo, colors, t)
            CardScene.RightTurnCrosswalk -> RightTurnScene(geo, colors, t)
            CardScene.HighwayShoulder -> HighwayScene(geo, colors, t)
            CardScene.SchoolZoneDestination -> DestinationScene(geo, colors, t)
        }
    }
}

private class SceneGeometry(val scale: Float, val left: Float) {
    fun x(px: Float): Dp = (left + px * scale).dp
    fun y(px: Float): Dp = (px * scale).dp
    fun s(px: Float): Dp = (px * scale).dp
}

/** Places a layer at Figma coordinates even when it is larger than the slot (no re-centring). */
private fun Modifier.placeAt(left: Dp, top: Dp, width: Dp, height: Dp): Modifier =
    wrapContentSize(Alignment.TopStart, unbounded = true).offset(left, top).requiredSize(width, height)

@Composable
private fun Layer(asset: String, left: Dp, top: Dp, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    FigmaImage(asset, Modifier.placeAt(left, top, width, height).then(modifier), contentScale = ContentScale.FillBounds)
}

/**
 * A car image whose node centre sits at [rest] when the pose equals it. [imageOffsetX]/[imageOffsetY]
 * are the image's top-left relative to that centre for a north-facing car.
 */
@Composable
private fun Car(
    geo: SceneGeometry,
    asset: String,
    rest: Pose,
    imageWidth: Float,
    imageHeight: Float,
    pose: () -> Pose,
    alpha: () -> Float,
) {
    // Top-left of the north-facing image so that CAR_PIVOT lands on the node centre.
    val left = rest.x - imageWidth * CAR_PIVOT.pivotFractionX
    val top = rest.y - imageHeight * CAR_PIVOT.pivotFractionY
    Layer(asset, geo.x(left), geo.y(top), geo.s(imageWidth), geo.s(imageHeight), Modifier.graphicsLayer {
        val p = pose()
        transformOrigin = CAR_PIVOT
        rotationZ = p.heading
        translationX = ((p.x - rest.x) * geo.scale).dp.toPx()
        translationY = ((p.y - rest.y) * geo.scale).dp.toPx()
        this.alpha = alpha()
    })
}

@Composable
private fun CrosswalkScene(geo: SceneGeometry, colors: SceneColors, t: () -> Float) {
    val s = SceneScripts.Crosswalk
    val ox = -356f
    val oy = -80.8359f
    Layer("road_crosswalk", geo.x(0f), geo.y(0f), geo.s(SCENE_W), geo.s(585f))
    Box(
        Modifier.placeAt(geo.x(ox + 737.51f), geo.y(oy + 496.64f), geo.s(134.092f), geo.s(14.899f))
            .graphicsLayer { alpha = s.stopLineAlpha(t()) }
            .background(colors.red, RoundedCornerShape(geo.s(7.45f))),
    )
    Car(geo, "scene_car_crosswalk", s.rest, 122.031f, 192.979f, { s.car(t()) }, { s.carAlpha(t()) })
}

@Composable
private fun RightTurnScene(geo: SceneGeometry, colors: SceneColors, t: () -> Float) {
    val s = SceneScripts.RightTurn
    val ox = -420f
    val oy = 59.1641f
    Layer("road_right_turn", geo.x(0f), geo.y(oy), geo.s(SCENE_W), geo.s(618f))
    Pedestrian(geo, colors, s.PEDESTRIAN_X, s.PEDESTRIAN_REST_Y, { s.pedestrianY(t()) }, { s.pedestrianAlpha(t()) })
    Car(geo, "scene_car_right_turn", s.rest, 118.545f, 187.466f, { s.car(t()) }, { s.carAlpha(t()) })
    Box(Modifier.placeAt(geo.x(ox + 841.5f), geo.y(oy + 290.7f), geo.s(11.9f), geo.s(112.2f)).background(colors.red))
    Layer("walk_signal", geo.x(ox + 931.6f), geo.y(oy + 30.6f), geo.s(71.4f), geo.s(125.8f))
}

@Composable
private fun HighwayScene(geo: SceneGeometry, colors: SceneColors, t: () -> Float) {
    val s = SceneScripts.Highway
    // `Scene · 고속도로 갓길 정차` (1849:27547): rectangles drawn from the Figma layer list.
    Box(Modifier.placeAt(geo.x(100f), geo.y(-36.83f), geo.s(520f), geo.s(787.667f)).background(colors.road))
    for (i in 0..6) {
        Box(Modifier.placeAt(geo.x(296f), geo.y(-76.83f + 130f * i), geo.s(8f), geo.s(70f)).background(colors.marking))
    }
    Box(Modifier.placeAt(geo.x(496f), geo.y(-36.83f), geo.s(6f), geo.s(787.667f)).background(colors.marking))
    Car(geo, "scene_car_highway", s.rest, 153.411f, 242.603f, { s.car(t()) }, { 1f })
    Layer("highway_parking", geo.x(640f), geo.y(277.17f), geo.s(80f), geo.s(80f), Modifier.graphicsLayer {
        val time = t()
        scaleX = s.parkingScale(time)
        scaleY = s.parkingScale(time)
        alpha = s.parkingAlpha(time)
    })
    Box(Modifier.placeAt(geo.x(690f), geo.y(73.17f), geo.s(10f), geo.s(220f)).background(colors.tertiary))
    Box(
        Modifier.placeAt(geo.x(650f), geo.y(33.17f), geo.s(84f), geo.s(52f))
            .background(colors.secondary, RoundedCornerShape(geo.s(10f))),
    )
    Layer("highway_lens", geo.x(664f), geo.y(47.17f), geo.s(24f), geo.s(24f))
    // Flash glow around the lens (app motion; not a Figma layer).
    val glow = 40f
    Box(
        Modifier.placeAt(geo.x(676f - glow), geo.y(59.17f - glow), geo.s(glow * 2), geo.s(glow * 2))
            .drawBehind {
                val a = s.flashAlpha(t())
                if (a > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * a), Color.Transparent)),
                        radius = size.minDimension / 2f,
                    )
                }
            },
    )
}

@Composable
private fun DestinationScene(geo: SceneGeometry, colors: SceneColors, t: () -> Float) {
    val s = SceneScripts.Destination
    FigmaImage(CardScene.SchoolZoneDestination.asset, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val time = t()
            val a = s.rippleAlpha(time)
            if (a <= 0f) return@drawBehind
            val center = Offset(
                (s.CENTER_X * geo.scale).dp.toPx(),
                (s.CENTER_Y * geo.scale).dp.toPx(),
            )
            val radius = (s.rippleRadius(time) * geo.scale).dp.toPx()
            drawCircle(colors.zone.copy(alpha = a * 0.35f), radius = radius, center = center)
            drawCircle(colors.zone.copy(alpha = a), radius = radius, center = center, style = Stroke(width = 3.5f * geo.scale.dp.toPx()))
        },
    )
}

/** I-1 `Pedestrian` (1849:29364): pink disc, system-colour ring, warm glow, user glyph. */
@Composable
private fun Pedestrian(
    geo: SceneGeometry,
    colors: SceneColors,
    centerX: Float,
    restY: Float,
    y: () -> Float,
    alpha: () -> Float,
) {
    val size = 89.598f
    val glow = Color(0x73FFB23D) // rgba(255,178,61,0.45)
    Box(
        Modifier.placeAt(geo.x(centerX - size / 2f), geo.y(restY - size / 2f), geo.s(size), geo.s(size))
            .graphicsLayer {
                translationY = ((y() - restY) * geo.scale).dp.toPx()
                this.alpha = alpha()
            }
            .drawBehind {
                // CSS shadow 0 0 31px 3.4px, approximated with a radial fade.
                val radius = this.size.minDimension / 2f
                val outer = radius + (3.446f + 31.015f) * geo.scale * density
                drawCircle(
                    Brush.radialGradient(
                        0f to glow, (radius / outer) to glow, 1f to Color.Transparent,
                        center = Offset(this.size.width / 2f, this.size.height / 2f), radius = outer,
                    ),
                    radius = outer,
                )
            }
            .background(colors.pink, CircleShape)
            .border((5.169f * geo.scale).dp, colors.system, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FigmaImage("walker", Modifier.requiredSize(geo.s(55.137f)), contentScale = ContentScale.FillBounds)
    }
}
