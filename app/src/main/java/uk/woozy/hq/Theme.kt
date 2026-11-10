package uk.woozy.hq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Typography for the whole app: Google Sans (SIL Open Font License 1.1), bundled in res/font so it renders the same on any device.
 * Licence text: assets/licenses/GoogleSans-OFL.txt. Both themes use this one family.
 */
internal val GSans = FontFamily(
    Font(R.font.googlesans_regular, FontWeight.Normal),
    Font(R.font.googlesans_medium, FontWeight.Medium),
    Font(R.font.googlesans_semibold, FontWeight.SemiBold),
    Font(R.font.googlesans_bold, FontWeight.Bold),
)

/** App theme. "hq" follows the Lore Club HQ look; "frosted" matches the Frosted Glass widget pack. */
internal class Th(
    val dark: Boolean, val glass: Boolean,
    val bg: Color, val panel: Color, val fg: Color, val muted: Color, val hair: Color,
    val ok: Color, val warn: Color, val amber: Color,
    val tab: List<Color>, val onTab: Color,
)

internal val HQ = Th(
    dark = false, glass = false,
    bg = Color(0xFFFBF8F2), panel = Color(0xFFFFFFFF), fg = Color(0xFF1D1D1F), muted = Color(0xFF625E57),
    hair = Color(0x1A1D1D1F), ok = Color(0xFF3E7A4E), warn = Color(0xFFB3401A), amber = Color(0xFFA26A12),
    tab = listOf(Color(0xFF3F4250), Color(0xFFA63D6B), Color(0xFF2F5F8A), Color(0xFF4B4FA8), Color(0xFFA0522D)),
    onTab = Color.White,
)
internal val FROSTED = Th(
    dark = true, glass = true,
    bg = Color(0xFF2E2E2E), panel = Color(0x1FFFFFFF), fg = Color.White, muted = Color(0x8CFFFFFF), hair = Color(0x3AFFFFFF),
    ok = Color(0xFFB8E6C4), warn = Color(0xFFFFB4A0), amber = Color(0xFFFFD98A),
    tab = List(5) { Color.White }, onTab = Color(0xFF2E2E2E),
)
internal val LocalTh = staticCompositionLocalOf { HQ }

@Composable
internal fun T(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal, maxLines: Int = Int.MAX_VALUE, center: Boolean = false, caps: Boolean = false,
) {
    val th = LocalTh.current
    Text(
        if (caps && th.glass) text.uppercase() else text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = GSans, fontSize = size, color = color, fontWeight = weight,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            fontFeatureSettings = "tnum", // tabular numerals so figures and times line up
        ),
    )
}

/** Card surface: Frosted = glass gradient + rim; HQ = white panel with a hairline, or a soft tint of the tab colour. */
@Composable
internal fun Modifier.card(r: Dp, tint: Color? = null): Modifier {
    val th = LocalTh.current
    val shape = RoundedCornerShape(r)
    return if (th.glass) {
        this.clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x0EFFFFFF), Color(0x37FFFFFF))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color(0x3AFFFFFF), Color(0x60FFFFFF))), shape)
    } else {
        val m = this.clip(shape).background(if (tint != null) lerp(th.panel, tint, 0.12f) else th.panel)
        if (tint == null) m.border(1.dp, th.hair, shape) else m
    }
}
