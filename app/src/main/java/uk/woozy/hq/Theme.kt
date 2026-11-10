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


/** Colour schemes ported from the HQ artifact looks. Same font and layout, colours only. "hq" is the default (the artifact's Natural look). */
internal val EXTRA_THEMES: Map<String, Th> = mapOf(
    "colourful" to Th(dark = false, glass = false, bg = Color(0xFFFFF7EC), panel = Color(0xFFFFFFFF), fg = Color(0xFF1D1D1F), muted = Color(0xFF625E57), hair = Color(0x1F000000),
        ok = Color(0xFF1F8A57), warn = Color(0xFFC92A2A), amber = Color(0xFFB7791F),
        tab = listOf(Color(0xFF3F4250), Color(0xFFD6336C), Color(0xFF1C7ED6), Color(0xFF5C5FEF), Color(0xFFD9480F)), onTab = Color(0xFFFFFFFF)),
    "standard" to Th(dark = false, glass = false, bg = Color(0xFFF5F5F7), panel = Color(0xFFFFFFFF), fg = Color(0xFF1C1D22), muted = Color(0xFF5E616B), hair = Color(0x1F000000),
        ok = Color(0xFF2E7D4F), warn = Color(0xFFB42318), amber = Color(0xFF9A6A14),
        tab = listOf(Color(0xFF3F4250), Color(0xFF8A5470), Color(0xFF4A6A8A), Color(0xFF5A5E8C), Color(0xFF8E5C4B)), onTab = Color(0xFFFFFFFF)),
    "calm" to Th(dark = false, glass = false, bg = Color(0xFFF5F4F8), panel = Color(0xFFFFFFFF), fg = Color(0xFF2A2933), muted = Color(0xFF6B6978), hair = Color(0x1F000000),
        ok = Color(0xFF4F7A6A), warn = Color(0xFF9A4E4E), amber = Color(0xFF8A6F3A),
        tab = listOf(Color(0xFF5D5A7A), Color(0xFF8A6A80), Color(0xFF5F7790), Color(0xFF6A6894), Color(0xFF8A7070)), onTab = Color(0xFFFFFFFF)),
    "seventies" to Th(dark = false, glass = false, bg = Color(0xFFF2E6CC), panel = Color(0xFFFBF2DF), fg = Color(0xFF3A2718), muted = Color(0xFF6E5642), hair = Color(0x1F000000),
        ok = Color(0xFF5E6E2E), warn = Color(0xFFB4431F), amber = Color(0xFFA8741A),
        tab = listOf(Color(0xFF3A2718), Color(0xFFB23F35), Color(0xFF2F6B66), Color(0xFFB4511F), Color(0xFFD07A1F)), onTab = Color(0xFFFFFFFF)),
    "japandi" to Th(dark = false, glass = false, bg = Color(0xFFEEE7DA), panel = Color(0xFFF7F2E8), fg = Color(0xFF2A2521), muted = Color(0xFF6E655B), hair = Color(0x1F000000),
        ok = Color(0xFF6F7F5F), warn = Color(0xFFA8392F), amber = Color(0xFF8C6A3E),
        tab = listOf(Color(0xFF2A2521), Color(0xFFA0685E), Color(0xFF5E7280), Color(0xFF6F6A5E), Color(0xFFA9714F)), onTab = Color(0xFFFFFFFF)),
    "riviera" to Th(dark = false, glass = false, bg = Color(0xFFF3EDE1), panel = Color(0xFFFBF8F1), fg = Color(0xFF14233F), muted = Color(0xFF5A6273), hair = Color(0x1F000000),
        ok = Color(0xFF1F4A3A), warn = Color(0xFF8E2A2A), amber = Color(0xFF8A6A2E),
        tab = listOf(Color(0xFF14233F), Color(0xFF6E2E3E), Color(0xFF24476E), Color(0xFF14233F), Color(0xFF7A4A2A)), onTab = Color(0xFFFFFFFF)),
    "editorial" to Th(dark = false, glass = false, bg = Color(0xFFF3EFE6), panel = Color(0xFFFBF9F4), fg = Color(0xFF111111), muted = Color(0xFF57534C), hair = Color(0x1F000000),
        ok = Color(0xFF2F6B45), warn = Color(0xFFC8102E), amber = Color(0xFF9A6B12),
        tab = listOf(Color(0xFF111111), Color(0xFF111111), Color(0xFF111111), Color(0xFF111111), Color(0xFFC8102E)), onTab = Color(0xFFFFFFFF)),
    "notebook" to Th(dark = false, glass = false, bg = Color(0xFFF6F0E1), panel = Color(0xFFFFFDF6), fg = Color(0xFF2D3142), muted = Color(0xFF5F6274), hair = Color(0x1F000000),
        ok = Color(0xFF2E8A62), warn = Color(0xFFC2452E), amber = Color(0xFF8A6A12),
        tab = listOf(Color(0xFF2D3142), Color(0xFFC2457A), Color(0xFF2E7FB0), Color(0xFF3F63B0), Color(0xFFD0602A)), onTab = Color(0xFFFFFFFF)),
    "riso" to Th(dark = false, glass = false, bg = Color(0xFFF3EEE2), panel = Color(0xFFFFFBF2), fg = Color(0xFF1E1B3A), muted = Color(0xFF55506E), hair = Color(0x1F000000),
        ok = Color(0xFF00837F), warn = Color(0xFFD61F7A), amber = Color(0xFF8A6400),
        tab = listOf(Color(0xFF1E1B3A), Color(0xFFE0338E), Color(0xFF2D6FD6), Color(0xFF00837F), Color(0xFFE0338E)), onTab = Color(0xFFFFFFFF)),
    "solarpunk" to Th(dark = false, glass = false, bg = Color(0xFFEAF6E4), panel = Color(0xFFFFFFFF), fg = Color(0xFF1F3A24), muted = Color(0xFF557159), hair = Color(0x1F000000),
        ok = Color(0xFF2F8A3E), warn = Color(0xFFC2502E), amber = Color(0xFF9A7300),
        tab = listOf(Color(0xFF2F6B3A), Color(0xFFD06A7A), Color(0xFF2E86C1), Color(0xFF2F7A8A), Color(0xFFD98A1F)), onTab = Color(0xFFFFFFFF)),
    "bauhaus" to Th(dark = false, glass = false, bg = Color(0xFFF1EDE2), panel = Color(0xFFFBF9F3), fg = Color(0xFF111111), muted = Color(0xFF4A4740), hair = Color(0x1F000000),
        ok = Color(0xFF1F4FC1), warn = Color(0xFFD42A20), amber = Color(0xFF9A7400),
        tab = listOf(Color(0xFF111111), Color(0xFFD42A20), Color(0xFF1F4FC1), Color(0xFF1F4FC1), Color(0xFFD42A20)), onTab = Color(0xFFFFFFFF)),
    "chrome" to Th(dark = false, glass = false, bg = Color(0xFFE9E4FB), panel = Color(0xB3FFFFFF), fg = Color(0xFF262046), muted = Color(0xFF5E5884), hair = Color(0x1F000000),
        ok = Color(0xFF2E9C7A), warn = Color(0xFFC2366B), amber = Color(0xFF8A6A12),
        tab = listOf(Color(0xFF6A4CF5), Color(0xFFE0559A), Color(0xFF3A86E8), Color(0xFF6A4CF5), Color(0xFFE0559A)), onTab = Color(0xFFFFFFFF)),
    "digital" to Th(dark = true, glass = false, bg = Color(0xFF0F1320), panel = Color(0xFF171C2E), fg = Color(0xFFEEF1F8), muted = Color(0xFFA3ACC2), hair = Color(0x1FFFFFFF),
        ok = Color(0xFF5BE0A3), warn = Color(0xFFFF8A65), amber = Color(0xFFFFD166),
        tab = listOf(Color(0xFFC9D1E6), Color(0xFFFF8FBF), Color(0xFF6FB2FF), Color(0xFFA5A8FF), Color(0xFFFF9F6B)), onTab = Color(0xFF0F1320)),
    "noir" to Th(dark = true, glass = false, bg = Color(0xFF0C0B0A), panel = Color(0xFF151412), fg = Color(0xFFF2ECDF), muted = Color(0xFFA69E8F), hair = Color(0x1FFFFFFF),
        ok = Color(0xFFA9BE8F), warn = Color(0xFFE0927A), amber = Color(0xFFD4B06A),
        tab = listOf(Color(0xFFD4B06A), Color(0xFFD9A3A0), Color(0xFFA9BBC8), Color(0xFFD4B06A), Color(0xFFD9A77A)), onTab = Color(0xFF0C0B0A)),
    "athletic" to Th(dark = true, glass = false, bg = Color(0xFF141414), panel = Color(0xFF1E1E1E), fg = Color(0xFFF2F2EE), muted = Color(0xFFA6A6A0), hair = Color(0x1FFFFFFF),
        ok = Color(0xFFC9DB6A), warn = Color(0xFFF07A5A), amber = Color(0xFFE8C26A),
        tab = listOf(Color(0xFFC9DB6A), Color(0xFFE8A4A4), Color(0xFF9FC9E0), Color(0xFFC9DB6A), Color(0xFFE8A46A)), onTab = Color(0xFF141414)),
    "arcade" to Th(dark = true, glass = false, bg = Color(0xFF120A26), panel = Color(0xFF1C1238), fg = Color(0xFFF1ECFF), muted = Color(0xFFB4A8D8), hair = Color(0x1FFFFFFF),
        ok = Color(0xFFA8FF3E), warn = Color(0xFFFF5C8A), amber = Color(0xFFFFD23E),
        tab = listOf(Color(0xFF3EE6FF), Color(0xFFFF3EC8), Color(0xFF3EE6FF), Color(0xFF3EE6FF), Color(0xFFFF3EC8)), onTab = Color(0xFF120A26)),
)
internal val THEME_NAMES: List<Pair<String, String>> = listOf("hq" to "Natural (HQ)", "frosted" to "Frosted glass") + listOf(
    "colourful" to "Colourful", "standard" to "Standard", "calm" to "Calm", "seventies" to "Seventies", "japandi" to "Japandi", "riviera" to "Riviera", "editorial" to "Editorial", "notebook" to "Notebook", "riso" to "Riso", "solarpunk" to "Solarpunk", "bauhaus" to "Bauhaus", "chrome" to "Chrome", "digital" to "Digital", "noir" to "Noir", "athletic" to "Athletic", "arcade" to "Arcade"
)
internal fun themeFor(id: String): Th = when (id) { "hq" -> HQ; "frosted" -> FROSTED; else -> EXTRA_THEMES[id] ?: HQ }
