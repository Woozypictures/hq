package uk.woozy.hq

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Woozy glass: the shared floating frosted-glass navigation, one spec used by both HQ and Woozy Fit.
 * This file is kept identical in both repos apart from the package line. Change both together.
 *
 * Real backdrop blur: the screen content is recorded into a GraphicsLayer ([GlassBackdrop]). Each glass
 * surface draws a copy of that layer, shifted so it lines up with the screen, through a blur render effect,
 * then lays a translucent tint and a hairline rim on top. Needs Android 12 (API 31) for the blur; older
 * phones get the same tint with no blur (a little more opaque so text stays readable).
 */

/** Space the floating nav occupies at the bottom of a screen, so scrolling content can leave room for it. */
val LocalGlassInset = staticCompositionLocalOf { 0.dp }

val GlassNavHeight = 64.dp
val GlassNavLift = 12.dp

@Immutable
class GlassStyle(
    val tint: Color,
    val tintNoBlur: Color,
    val rim: Color,
    val capsule: Color,
    val ink: Color,
    val muted: Color,
    val shadow: Color,
)

fun glassStyle(dark: Boolean, ink: Color, muted: Color): GlassStyle = if (dark) {
    GlassStyle(
        tint = Color(0xFF202124).copy(alpha = 0.58f), tintNoBlur = Color(0xFF202124).copy(alpha = 0.92f),
        rim = Color.White.copy(alpha = 0.14f), capsule = Color.White.copy(alpha = 0.14f),
        ink = ink, muted = muted, shadow = Color.Black.copy(alpha = 0.40f),
    )
} else {
    GlassStyle(
        tint = Color.White.copy(alpha = 0.66f), tintNoBlur = Color.White.copy(alpha = 0.94f),
        rim = Color.White.copy(alpha = 0.70f), capsule = Color(0xFF1D1D1F).copy(alpha = 0.075f),
        ink = ink, muted = muted, shadow = Color.Black.copy(alpha = 0.16f),
    )
}

/** What the glass looks at. Wrap the screen content in [glassSource]; surfaces then blur what is behind them. */
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Mark the content that should show through the glass. Draws normally as well as recording itself. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        val self = this
        backdrop.layer.record { self.drawContent() }
        drawLayer(backdrop.layer)
    }

/** A frosted glass surface in [shape]: blurred backdrop, translucent tint, hairline rim, soft shadow. */
@Composable
fun Modifier.glass(backdrop: GlassBackdrop, style: GlassStyle, shape: Shape, blur: Float = 26f): Modifier {
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= 31
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .shadow(10.dp, shape, ambientColor = style.shadow, spotColor = style.shadow)
        .clip(shape)
        .drawBehind {
            if (canBlur && size.width > 0f && size.height > 0f) {
                blurLayer.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                blurLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                    translate(backdrop.origin.x - pos.x, backdrop.origin.y - pos.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurLayer)
            }
            drawRect(if (canBlur) style.tint else style.tintNoBlur)
        }
        .border(0.75.dp, style.rim, shape)
}

data class GlassItem(val label: String, val icon: ImageVector)

/**
 * The floating pill. Sits [GlassNavLift] above the gesture area, centred, with a separate inset capsule that
 * slides behind the selected item. Call it as an overlay at the bottom of a Box that holds the screen.
 * [trailing] is an optional extra glass control placed to the right of the pill (Fit's quick add).
 */
@Composable
fun GlassPillNav(
    items: List<GlassItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: GlassBackdrop,
    style: GlassStyle,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = GlassNavLift),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        val pill = RoundedCornerShape(32.dp)
        BoxWithConstraints(Modifier.weight(1f, fill = true).height(GlassNavHeight).glass(backdrop, style, pill)) {
            val pad = 6.dp
            val itemW = (maxWidth - pad * 2) / items.size
            val x by animateDpAsState(pad + itemW * selected, tween(280, easing = FastOutSlowInEasing), label = "capsule")
            Box(
                Modifier.offset(x = x, y = pad).width(itemW).height(GlassNavHeight - pad * 2)
                    .clip(RoundedCornerShape(26.dp)).background(style.capsule),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = pad)) {
                items.forEachIndexed { i, item ->
                    val on = i == selected
                    Column(
                        Modifier.weight(1f).fillMaxHeight().selectable(selected = on, role = Role.Tab, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            if (!on) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = if (on) style.ink else style.muted, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.label, maxLines = 1, overflow = TextOverflow.Clip,
                            style = TextStyle(
                                fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (on) style.ink else style.muted, textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
        if (trailing != null) trailing()
    }
}

/** Round glass control (quick add and the like), same material as the pill. */
@Composable
fun GlassRoundButton(backdrop: GlassBackdrop, style: GlassStyle, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(GlassNavHeight).glass(backdrop, style, CircleShape)
            .selectable(selected = false, role = Role.Button, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** No press ripple or grey flash anywhere: touches are answered by haptics and the sliding capsule instead. */
object NoIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
            override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() { drawContent() }
        }
    override fun equals(other: Any?) = other === this
    override fun hashCode() = -1
}
