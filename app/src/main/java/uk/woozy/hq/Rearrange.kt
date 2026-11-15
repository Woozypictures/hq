package uk.woozy.hq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch

/**
 * iOS-style rearranging. When [enabled], a card that moves in the column (because another card was dragged past
 * it) glides to its new place on a spring instead of jumping. Off, it follows layout with no animation.
 */
fun Modifier.animatePlacement(enabled: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    val anim = remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val t = target ?: return@offset IntOffset.Zero
            val a = anim.value ?: Animatable(t, IntOffset.VectorConverter).also { anim.value = it }
            if (a.targetValue != t) scope.launch {
                if (enabled) a.animateTo(t, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) else a.snapTo(t)
            }
            a.value - t
        }
}

/**
 * The picked-up card: lifts smoothly (scale 1.02, soft shadow, slight tilt) and follows the finger by [offsetY].
 * Everything eases on springs, so pick-up and drop both settle rather than snap.
 */
fun Modifier.liftWhileDragging(dragging: Boolean, offsetY: () -> Float): Modifier = composed {
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "lift")
    this.graphicsLayer {
        translationY = offsetY()
        val s = 1f + 0.02f * lift
        scaleX = s; scaleY = s
        rotationZ = -1.2f * lift
        shadowElevation = if (lift > 0.02f) 24f else 0f // not animated: shadows are the expensive part
        shape = RoundedCornerShape(22.dp)
        clip = false
    }
}

/**
 * Floating frosted "Done" pill for rearranging: sits at the bottom of the screen, above the gesture area and the
 * floating nav ([aboveNav]), with a 1px specular rim and a spring press. Drawn in a popup so it floats over any page.
 */
@Composable
fun FloatingDonePill(visible: Boolean, dark: Boolean, aboveNav: Dp, label: String = "Done", onDone: () -> Unit) {
    if (!visible) return
    val nb = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press")
    val shape = RoundedCornerShape(50)
    val tint = if (dark) Color(0xFF1C1D1F).copy(alpha = 0.86f) else Color(0xFFF8F9FB).copy(alpha = 0.90f)
    val ink = if (dark) Color(0xFFF2F1EC) else Color(0xFF141414)
    Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Box(
            Modifier
                .padding(bottom = nb + aboveNav + 14.dp)
                .graphicsLayer { scaleX = press; scaleY = press }
                .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape)
                .background(tint)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.45f), Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.9f), Color.White.copy(alpha = if (dark) 0.06f else 0.25f))), shape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onDone)
                .widthIn(min = 160.dp)
                .padding(horizontal = 32.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink))
        }
    }
}

/**
 * Tap target with an iOS-style feel: scales to 0.97 on a spring while pressed (GPU layer only, no layout change),
 * no ripple flash, and a crisp tick the moment the press registers (inside a scroll this waits for the short tap
 * timeout, so scrolling past a card never ticks).
 */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val view = androidx.compose.ui.platform.LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh), label = "press")
    androidx.compose.runtime.LaunchedEffect(source) {
        source.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Press) crispTick(view) }
    }
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
}

/** Shortest, sharpest haptic the phone has (a keyboard-style tick), not a buzz. */
fun crispTick(v: android.view.View) {
    v.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.KEYBOARD_PRESS else android.view.HapticFeedbackConstants.KEYBOARD_TAP)
}
