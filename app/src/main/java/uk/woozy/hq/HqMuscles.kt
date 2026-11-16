package uk.woozy.hq

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One muscle region from Woozy Fit: working sets over 7 and 30 days, and when it was last worked. */
data class MuscleRow(val key: String, val name: String, val week: Double, val month: Double, val lastHit: Long?)

/** Reads Woozy Fit's muscle map (content://uk.woozy.fit.summary/muscles). Null when Woozy Fit is missing or too old. */
object FitMuscles {
    private val URI = android.net.Uri.parse("content://uk.woozy.fit.summary/muscles")
    val MAIN = listOf("chest", "shoulders", "biceps", "triceps", "abs", "lats", "upperback", "glutes", "quads", "hamstrings", "calves")

    fun read(c: Context): List<MuscleRow>? {
        if (!FitSrc.installed(c)) return null
        return runCatching {
            c.contentResolver.query(URI, null, null, null, null)?.use { q ->
                val k = q.getColumnIndex("region"); val n = q.getColumnIndex("name"); val w = q.getColumnIndex("week_sets")
                val m = q.getColumnIndex("month_sets"); val l = q.getColumnIndex("last_hit_ms")
                if (k < 0 || w < 0) return@use null
                val out = ArrayList<MuscleRow>()
                while (q.moveToNext()) out += MuscleRow(q.getString(k), q.getString(n), q.getDouble(w), q.getDouble(m), if (q.isNull(l)) null else q.getLong(l))
                out
            }
        }.getOrNull()
    }
}

/** HQ's muscle card body: the same front and back map as Woozy Fit, with Week, 30 days and Recovery. */
@Composable
internal fun MuscleMapContent(rows: List<MuscleRow>?, accent: Color) {
    val th = LocalTh.current
    if (rows == null) { T("Open Woozy Fit once to share your muscle map.", 14.sp, th.muted); return }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(50)).background(lerp(th.panel, th.fg, 0.06f)).padding(3.dp)) {
        listOf("Week", "30 days", "Recovery").forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (tab == i) th.panel else Color.Transparent).clickable { tab = i }.padding(vertical = 7.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { T(label, 13.sp, if (tab == i) th.fg else th.muted, weight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
    val now = System.currentTimeMillis()
    val values: Map<String, Float> = when (tab) {
        0 -> rows.associate { it.key to (it.week / 10.0).toFloat().coerceIn(0f, 1f) }
        1 -> rows.associate { it.key to (it.month / (10.0 * 30 / 7)).toFloat().coerceIn(0f, 1f) }
        else -> rows.associate { r -> r.key to (r.lastHit?.let { 1f - (now - it) / (72f * 3_600_000f) } ?: 0f).coerceIn(0f, 1f) }
    }
    HqBodyMap(values, if (tab == 2) th.warn else accent, Modifier.fillMaxWidth().aspectRatio(1.3f))
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
        T("Front", 12.sp, th.muted); T("Back", 12.sp, th.muted)
    }
    val names = rows.associate { it.key to it.name }
    if (tab == 2) {
        val rec = rows.filter { it.lastHit != null && now - it.lastHit < 48 * 3_600_000L }.sortedByDescending { it.lastHit }.map { it.name.lowercase() }
        T(if (rec.isEmpty()) "Everything is rested." else "Recovering: " + rec.joinToString(", "), 14.sp, th.fg, weight = FontWeight.SemiBold)
    } else {
        val sets = rows.associate { it.key to if (tab == 0) it.week else it.month }
        val weeks = if (tab == 0) 1.0 else 30.0 / 7
        if (sets.values.all { it == 0.0 }) T(if (tab == 0) "No workouts in the last 7 days." else "No workouts in the last 30 days.", 14.sp, th.muted)
        else {
            val behind = FitMuscles.MAIN.map { it to (sets[it] ?: 0.0) }.filter { it.second < 10.0 * weeks / 3 }.sortedBy { it.second }.take(3).map { names[it.first]?.lowercase() ?: it.first }
            if (behind.isEmpty()) T("Every main muscle is getting work.", 14.sp, th.ok, weight = FontWeight.SemiBold)
            else T("Falling behind: " + behind.joinToString(", "), 14.sp, th.warn, weight = FontWeight.SemiBold)
            val top = sets.filter { it.value > 0 }.toList().sortedByDescending { it.second }.take(3)
            T("Most worked: " + top.joinToString(", ") { (k, v) -> "${names[k]?.lowercase() ?: k} ${if (v % 1.0 == 0.0) v.toInt() else String.format(java.util.Locale.UK, "%.1f", v)}" } + " sets", 13.sp, th.muted)
        }
    }
}

/** Front and back figures, the same shapes as Woozy Fit's BodyMap. */
@Composable
internal fun HqBodyMap(values: Map<String, Float>, hot: Color, modifier: Modifier = Modifier) {
    val th = LocalTh.current
    val base = lerp(th.panel, th.fg, if (th.dark) 0.10f else 0.07f)
    val skin = lerp(th.panel, th.fg, if (th.dark) 0.16f else 0.12f)
    Canvas(modifier) {
        val u = minOf(size.width / 220f, size.height / 200f)
        val ox = (size.width - 220f * u) / 2f
        fun col(region: String): Color { val v = values[region] ?: 0f; return if (v <= 0f) base else lerp(base, hot, 0.25f + 0.75f * v) }
        for (side in 0..1) {
            val dx = ox + side * 120f * u
            fun P(x: Float, y: Float) = Offset(dx + x * u, y * u)
            fun oval(region: String?, cx: Float, cy: Float, rx: Float, ry: Float) =
                drawOval(if (region == null) skin else col(region), P(cx - rx, cy - ry), Size(2 * rx * u, 2 * ry * u))
            fun box(region: String?, l: Float, t: Float, r: Float, b: Float, rad: Float) =
                drawRoundRect(if (region == null) skin else col(region), P(l, t), Size((r - l) * u, (b - t) * u), CornerRadius(rad * u))
            fun poly(region: String, vararg pts: Float) {
                val p = Path().apply { moveTo(dx + pts[0] * u, pts[1] * u); var i = 2; while (i < pts.size) { lineTo(dx + pts[i] * u, pts[i + 1] * u); i += 2 }; close() }
                drawPath(p, col(region))
            }
            oval(null, 50f, 14f, 10f, 12f)
            box(null, 45f, 24f, 55f, 33f, 3f)
            oval(null, 18f, 106f, 4f, 6f); oval(null, 82f, 106f, 4f, 6f)
            oval(null, 42f, 194f, 6f, 4f); oval(null, 58f, 194f, 6f, 4f)
            box(null, 37f, 94f, 63f, 104f, 5f)
            if (side == 0) {
                poly("traps", 45f, 30f, 36f, 36f, 45f, 36f); poly("traps", 55f, 30f, 64f, 36f, 55f, 36f)
                oval("shoulders", 29f, 42f, 9f, 8f); oval("shoulders", 71f, 42f, 9f, 8f)
                box("chest", 36f, 36f, 49.3f, 56f, 6f); box("chest", 50.7f, 36f, 64f, 56f, 6f)
                oval("biceps", 24f, 62f, 5.5f, 11f); oval("biceps", 76f, 62f, 5.5f, 11f)
                oval("forearms", 20f, 87f, 4.8f, 13f); oval("forearms", 80f, 87f, 4.8f, 13f)
                box("obliques", 35f, 59f, 40.5f, 92f, 4f); box("obliques", 59.5f, 59f, 65f, 92f, 4f)
                box("abs", 41.5f, 58f, 58.5f, 93f, 5f)
                oval("adductors", 47.6f, 114f, 2.6f, 12f); oval("adductors", 52.4f, 114f, 2.6f, 12f)
                oval("quads", 41.5f, 126f, 7.5f, 22f); oval("quads", 58.5f, 126f, 7.5f, 22f)
                oval(null, 42f, 151f, 4.5f, 3.5f); oval(null, 58f, 151f, 4.5f, 3.5f)
                oval("calves", 42f, 172f, 5.5f, 16f); oval("calves", 58f, 172f, 5.5f, 16f)
            } else {
                box("upperback", 37f, 42f, 63f, 62f, 6f)
                poly("traps", 50f, 27f, 35f, 38f, 50f, 60f, 65f, 38f)
                oval("shoulders", 29f, 42f, 9f, 8f); oval("shoulders", 71f, 42f, 9f, 8f)
                poly("lats", 36f, 50f, 46f, 62f, 45f, 84f, 38f, 78f, 34f, 62f); poly("lats", 64f, 50f, 54f, 62f, 55f, 84f, 62f, 78f, 66f, 62f)
                box("lowerback", 44f, 70f, 56f, 93f, 4f)
                oval("triceps", 24f, 61f, 5.5f, 11f); oval("triceps", 76f, 61f, 5.5f, 11f)
                oval("forearms", 20f, 87f, 4.8f, 13f); oval("forearms", 80f, 87f, 4.8f, 13f)
                oval("glutes", 43f, 104f, 8f, 9f); oval("glutes", 57f, 104f, 8f, 9f)
                oval("hamstrings", 42f, 133f, 7f, 19f); oval("hamstrings", 58f, 133f, 7f, 19f)
                oval("calves", 42f, 170f, 6.5f, 14f); oval("calves", 58f, 170f, 6.5f, 14f)
            }
        }
    }
}
