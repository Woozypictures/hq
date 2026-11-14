package uk.woozy.hq

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlinx.coroutines.launch
import androidx.compose.ui.node.currentValueOf

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
    val outline: Color,
)

fun glassStyle(dark: Boolean, ink: Color, muted: Color): GlassStyle = if (dark) {
    GlassStyle(
        tint = Color(0xFF1C1D1F).copy(alpha = 0.52f), tintNoBlur = Color(0xFF202124).copy(alpha = 0.92f),
        rim = Color.White.copy(alpha = 0.14f), capsule = Color.White.copy(alpha = 0.14f),
        ink = ink, muted = muted, shadow = Color.Black.copy(alpha = 0.50f), outline = Color.White.copy(alpha = 0.22f),
    )
} else {
    GlassStyle(
        tint = Color(0xFFEEF0F3).copy(alpha = 0.52f), tintNoBlur = Color.White.copy(alpha = 0.94f),
        rim = Color.White.copy(alpha = 0.70f), capsule = Color(0xFF1D1D1F).copy(alpha = 0.075f),
        ink = ink, muted = muted, shadow = Color.Black.copy(alpha = 0.26f), outline = Color.Black.copy(alpha = 0.07f),
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

/** True when the phone's "Remove animations" is on: glass drops its glow and springs and uses a thicker tint. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** Fine film grain, made once and tiled: the matte, misty feel of frosted glass (also hides blur banding on OLED). */
private val grainShader: androidx.compose.ui.graphics.ShaderBrush by lazy {
    val n = 128
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) { val v = rnd.nextInt(256); android.graphics.Color.argb(255, v, v, v) }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * A frosted glass surface in [shape], built in layers: blurred and colour-boosted backdrop, translucent tint,
 * fine grain, a soft specular edge (bright on top, fading down the sides), and a two-layer shadow. [blur] is in dp.
 */
@Composable
fun Modifier.glass(backdrop: GlassBackdrop, style: GlassStyle, shape: Shape, blur: Float = 22f): Modifier {
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= 31
    val reduce = rememberReduceMotion()
    val dark = style.tint.red < 0.5f
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .shadow(24.dp, shape, ambientColor = style.shadow.copy(alpha = style.shadow.alpha * 0.45f), spotColor = style.shadow.copy(alpha = style.shadow.alpha * 0.4f))
        .shadow(6.dp, shape, ambientColor = style.shadow.copy(alpha = style.shadow.alpha * 0.55f), spotColor = style.shadow.copy(alpha = style.shadow.alpha * 0.6f))
        .clip(shape)
        .drawBehind {
            if (canBlur && size.width > 0f && size.height > 0f) drawBlurredBackdrop(blurLayer, backdrop, pos, (if (dark) blur + 4f else blur).dp.toPx(), saturate = true)
            val tint = if (!canBlur) style.tintNoBlur else if (reduce) style.tintNoBlur.copy(alpha = 0.8f) else style.tint
            drawRect(tint)
            drawRect(grainShader, alpha = if (dark) 0.04f else 0.03f, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
            // Specular edge: light catches the top rim and fades down the sides; a faint darker rim at the bottom.
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.32f else 0.75f), 0.45f to Color.White.copy(alpha = if (dark) 0.06f else 0.12f),
                    1f to Color.Black.copy(alpha = if (dark) 0.25f else 0.06f),
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
        .drawWithContent {
            drawContent()
            // Hairline that only shows along the lower half, so the top keeps its bright specular edge.
            drawOutline(
                shape.createOutline(size, layoutDirection, this),
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to style.outline),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
}

/** Solid version of the glass for surfaces that sit on other glass (menus, sheets): crisp, with the same rim and shadow. */
fun Modifier.solidGlass(style: GlassStyle, shape: Shape): Modifier = this
    .shadow(20.dp, shape, ambientColor = style.shadow.copy(alpha = style.shadow.alpha * 0.5f), spotColor = style.shadow.copy(alpha = style.shadow.alpha * 0.5f))
    .clip(shape)
    .background(style.tintNoBlur.copy(alpha = 0.97f))
    .drawBehind {
        val dark = style.tint.red < 0.5f
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.25f else 0.8f), 1f to Color.Black.copy(alpha = if (dark) 0.2f else 0.06f)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )
    }

/**
 * Bottom scroll edge: content dissolves into the page colour as it slides under the nav pill, so cards never look
 * clipped by the gesture bar. Place it just before the pill in the screen's Box.
 */
@Composable
fun GlassBottomFade(bg: Color, modifier: Modifier = Modifier) {
    val nb = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier.fillMaxWidth().height(nb + GlassNavHeight + GlassNavLift + 24.dp).background(
            androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.45f to bg.copy(alpha = 0.55f), 1f to bg.copy(alpha = 0.9f)),
        ),
    )
}

/**
 * Draws the backdrop behind this surface, blurred (and with colour lifted when [saturate], so it glows through
 * instead of going grey). The copy is taken with a margin round the surface so text half under an edge is
 * blurred exactly like text in the middle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurredBackdrop(
    blurLayer: GraphicsLayer, backdrop: GlassBackdrop, pos: Offset, radiusPx: Float, saturate: Boolean = false,
) {
    val m = (radiusPx * 2f).coerceAtLeast(1f)
    blurLayer.renderEffect = if (saturate && Build.VERSION.SDK_INT >= 31) {
        val cm = android.graphics.ColorMatrix().apply { setSaturation(1.35f) }
        android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(cm)),
            android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.DECAL),
        ).asComposeRenderEffect()
    } else BlurEffect(radiusPx, radiusPx, TileMode.Decal)
    blurLayer.record(size = IntSize((size.width + m * 2).toInt(), (size.height + m * 2).toInt())) {
        translate(backdrop.origin.x - pos.x + m, backdrop.origin.y - pos.y + m) { drawLayer(backdrop.layer) }
    }
    translate(-m, -m) { drawLayer(blurLayer) }
}

/**
 * The wash that sits behind the + menu. Pair it with [frostedContent] on the screen content, which does the actual
 * blur (blurring the content itself is reliable at full-screen size, unlike copying it into a layer).
 */
@Composable
fun Modifier.frostScreen(backdrop: GlassBackdrop, style: GlassStyle): Modifier {
    val dark = style.tint.red < 0.5f
    val canBlur = Build.VERSION.SDK_INT >= 31
    return this.drawBehind {
        drawRect(if (dark) Color.Black.copy(alpha = if (canBlur) 0.30f else 0.6f) else Color.White.copy(alpha = if (canBlur) 0.30f else 0.7f))
    }
}

/** Blurs the whole screen content while [on] (eases in and out), for the frost behind menus. */
@Composable
fun Modifier.frostedContent(on: Boolean): Modifier {
    if (Build.VERSION.SDK_INT < 31) return this
    val r by androidx.compose.animation.core.animateFloatAsState(if (on) 22f else 0f, tween(200), label = "frost")
    val px = with(LocalDensity.current) { r.dp.toPx() }
    return this.graphicsLayer { renderEffect = if (px > 0.5f) BlurEffect(px, px, TileMode.Decal) else null }
}

/**
 * Frosted band under the status bar, made of the same glass as the nav pill: fully frosted behind the clock and icons,
 * then easing away over a short fade so content melts out instead of being cut off.
 */
@Composable
fun GlassTopFade(backdrop: GlassBackdrop, bg: Color, modifier: Modifier = Modifier) {
    val sb = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fade = 20.dp
    val h = sb + fade
    val solid = (sb / h).coerceIn(0f, 1f)
    val canBlur = Build.VERSION.SDK_INT >= 31
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.fillMaxWidth().height(h)
            .onGloballyPositioned { pos = it.positionInRoot() }
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            }
            .drawBehind {
                if (canBlur && size.width > 0f && size.height > 0f) drawBlurredBackdrop(blurLayer, backdrop, pos, 20.dp.toPx())
                drawRect(bg.copy(alpha = if (canBlur) 0.92f else 0.97f))
            },
    )
}

/** Small frosted pop-up that appears briefly (for example "Last refreshed 18:27") and fades away. */
@Composable
fun GlassToast(text: String, visible: Boolean, backdrop: GlassBackdrop, style: GlassStyle, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.scaleIn(tween(180), initialScale = 0.92f),
        exit = androidx.compose.animation.fadeOut(tween(260)),
    ) {
        // A solid ink toast (as Samsung's "Last synced"), so it never reads as glass stacked on the glass pill.
        val dark = style.tint.red < 0.5f
        Box(
            Modifier.shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(if (dark) Color(0xFFF2F2F0) else Color(0xFF1C1D1F)).padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (dark) Color(0xFF1C1D1F) else Color.White))
        }
    }
}

data class GlassMenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Bubble menu that pops out of the nav's action button (like an iOS long-press menu). Cover the whole screen with it:
 * a tap outside closes it. [bottomInset] is the space to leave for the nav so the bubble sits just above the button.
 */
@Composable
fun GlassPopupMenu(
    visible: Boolean, items: List<GlassMenuItem>, onDismiss: () -> Unit,
    backdrop: GlassBackdrop, style: GlassStyle, bottomInset: androidx.compose.ui.unit.Dp,
) {
    val haptic = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    androidx.compose.animation.AnimatedVisibility(visible = visible, enter = androidx.compose.animation.fadeIn(tween(160)), exit = androidx.compose.animation.fadeOut(tween(140))) {
        Box(
            Modifier.fillMaxSize()
                .frostScreen(backdrop, style)
                .selectable(selected = false, role = Role.Button, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            // The bubble grows out of the + in the bottom-right corner, on a spring.
            val grow = remember { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0.6f) }
            androidx.compose.runtime.LaunchedEffect(Unit) { grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 520f)) }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = bottomInset + 6.dp)
                    .graphicsLayer { scaleX = grow.value; scaleY = grow.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f) }
                    .width(236.dp)
                    .solidGlass(style, RoundedCornerShape(26.dp))
                    .selectable(selected = false, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(vertical = 6.dp),
            ) {
                items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(style.outline))
                    Row(
                        Modifier.fillMaxWidth().height(50.dp)
                            .selectable(selected = false, role = Role.Button, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); item.onClick()
                            }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, modifier = Modifier.weight(1f), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = style.ink))
                        Icon(item.icon, contentDescription = null, tint = style.ink, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** Switch with a light tick when it flips. */
@Composable
fun HapticSwitch(
    checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier,
    colors: androidx.compose.material3.SwitchColors = androidx.compose.material3.SwitchDefaults.colors(),
) {
    val haptic = LocalHapticFeedback.current
    androidx.compose.material3.Switch(checked = checked, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) }, modifier = modifier, colors = colors)
}

/**
 * Pull to refresh with a calmer feel: the spinner stays up for a moment after the data lands instead of
 * snapping away, it sits under the status bar, and there is a tick when the pull passes the point of no return.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SmoothRefreshBox(
    isRefreshing: Boolean, onRefresh: () -> Unit, container: Color, accent: Color,
    modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var held by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isRefreshing) {
        if (isRefreshing) held = true else if (held) { kotlinx.coroutines.delay(800); held = false }
    }
    androidx.compose.runtime.LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.distanceFraction >= 1f }.collect { if (it) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    val top = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = held, onRefresh = onRefresh, state = state, modifier = modifier,
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = held, containerColor = container, color = accent,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top)
                    .graphicsLayer { alpha = if (held || state.distanceFraction > 0f) 1f else 0f },
            )
        },
        content = content,
    )
}

/** Loading placeholder in the shape of the content: soft bars that pulse, instead of the word "Loading". */
@Composable
fun SkeletonBlock(color: Color, modifier: Modifier = Modifier, lines: Int = 3) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.35f, 0.75f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(lines) { i ->
            Box(Modifier.fillMaxWidth(if (i == lines - 1) 0.55f else if (i % 2 == 0) 0.9f else 0.75f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = color.alpha * a)))
        }
    }
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
    actionIcon: ImageVector? = null,
    actionLabel: String = "",
    onAction: () -> Unit = {},
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
            val itemW = (maxWidth - pad * 2) / (items.size + (if (actionIcon != null) 1 else 0))
            val reduce = rememberReduceMotion()
            val x by animateDpAsState(
                pad + itemW * selected,
                if (reduce) tween(0) else androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f), label = "capsule",
            )
            // Press glow: a soft light that blooms from the touched slot across the glass.
            var glowAt by remember { mutableStateOf(-1) }
            var glowTick by remember { mutableStateOf(0) }
            val glow = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(glowTick) {
                if (glowAt >= 0 && !reduce) { glow.snapTo(0.9f); glow.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
            }
            val glowCol = style.ink
            Box(Modifier.fillMaxSize().drawBehind {
                if (glow.value > 0f && glowAt >= 0) {
                    val cx = (pad + itemW * (glowAt + 0.5f)).toPx()
                    val r = size.height * (1.2f + (1f - glow.value) * 1.4f)
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f * glow.value), Color.Transparent), center = Offset(cx, size.height / 2), radius = r),
                        radius = r, center = Offset(cx, size.height / 2),
                    )
                }
            })
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
                            glowAt = i; glowTick++
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
                if (actionIcon != null) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().selectable(selected = false, role = Role.Button, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); glowAt = items.size; glowTick++; onAction()
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(actionIcon, contentDescription = null, tint = style.muted, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(actionLabel, maxLines = 1, overflow = TextOverflow.Clip, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = style.muted, textAlign = TextAlign.Center))
                        }
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

/** No press ripple or grey flash anywhere: a touch gets a light haptic tick and a small spring press (scales to 98% and back). */
object NoIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : Modifier.Node(), androidx.compose.ui.node.DrawModifierNode, androidx.compose.ui.node.CompositionLocalConsumerModifierNode {
            val press = androidx.compose.animation.core.Animatable(1f)
            override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
                val k = press.value
                if (k == 1f) drawContent() else scale(k, k) { this@draw.drawContent() }
            }
            override fun onAttach() {
                coroutineScope.launch {
                    interactionSource.interactions.collect {
                        when (it) {
                            is androidx.compose.foundation.interaction.PressInteraction.Press -> {
                                currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                launch { press.animateTo(0.98f, androidx.compose.animation.core.spring(stiffness = 900f)) }
                            }
                            is androidx.compose.foundation.interaction.PressInteraction.Release,
                            is androidx.compose.foundation.interaction.PressInteraction.Cancel ->
                                launch { press.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f)) }
                        }
                    }
                }
            }
        }
    override fun equals(other: Any?) = other === this
    override fun hashCode() = -1
}
