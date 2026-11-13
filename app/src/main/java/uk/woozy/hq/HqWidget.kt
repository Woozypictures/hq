package uk.woozy.hq

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.text.TextPaint
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Compact home-screen widget in the Frosted Glass KWGT style: translucent white gradient card,
 * gradient rim, ProductSans, txt / txt2 / txt3 whites and the 30-square progress bar.
 * Drawn to a bitmap so it can use the real font and bar.
 */
class HqWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        Cfg.get(c)
        ids.forEach { render(c, m, it) }
        RefreshWorker.ensure(c)
    }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) = render(c, m, id)

    companion object {
        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, HqWidget::class.java)).forEach { render(c, m, it) }
        }

        private fun render(c: Context, m: AppWidgetManager, id: Int) {
            val o = m.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300).coerceAtLeast(120)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 100).coerceAtLeast(56)
            val bmp = draw(c, Store.load(c), wDp, hDp)
            val rv = RemoteViews(c.packageName, R.layout.widget)
            rv.setImageViewBitmap(R.id.img, bmp)
            val pi = PendingIntent.getActivity(c, 0,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("refresh", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.root, pi)
            m.updateAppWidget(id, rv)
        }

        const val TXT = 0xFFFFFFFF.toInt()
        const val TXT2 = 0xCCFFFFFF.toInt()
        const val TXT3 = 0x99FFFFFF.toInt()
        const val CAP = 0x66303030
        const val BAR = 0x40FFFFFF

        /** Removes emoji and other symbols the widget font can't draw. */
        private fun clean(t: String) = t.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,:;!?'\"()&/+-%·@#£$°" }.replace(Regex("\\s+"), " ").trim()

        fun draw(c: Context, s: Snapshot, wDp: Int, hDp: Int): Bitmap {
            val d = c.resources.displayMetrics.density.coerceAtMost(3f)
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val font: Typeface = try { c.resources.getFont(R.font.productsans) } catch (e: Exception) { Typeface.DEFAULT }

            // glass card: faint dark underlay so white text stays readable on light wallpapers, then the frosted gradient and rim
            val inset = 1.2f * d
            val rect = RectF(inset, inset, w - inset, h - inset)
            val radius = minOf(28f * d, h / 2.4f)
            cv.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26000000 })
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x14FFFFFF, 0x3DFFFFFF, Shader.TileMode.CLAMP)
            }
            cv.drawRoundRect(rect, radius, radius, fill)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.1f * d
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x4DFFFFFF, 0x73FFFFFF, Shader.TileMode.CLAMP)
            }
            cv.drawRoundRect(rect, radius, radius, rim)

            fun tp(size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font; textSize = size * d; this.color = color
            }
            fun text(t: String, x: Float, baseline: Float, p: TextPaint, maxW: Float, right: Boolean = false) {
                val s2 = TextUtils.ellipsize(t, p, maxW, TextUtils.TruncateAt.END).toString()
                val xx = if (right) x - p.measureText(s2) else x
                cv.drawText(s2, xx, baseline, p)
            }

            val padX = maxOf(radius * 0.7f, 18f * d)
            val left = padX; val right = w - padX; val inner = right - left
            val compact = hDp < 100

            val label = tp(11f, TXT2)
            val big = tp(if (compact) 16f else 18f, TXT)
            val small = tp(11.5f, TXT3)

            // Date, weather, countdowns, steps and calories already sit in other widgets, so this card shows what they don't:
            // what to lock in and replies due (left), today's workout from Woozy Fit (right), the top task, the next event and last night's sleep.
            val lock = s.tasks?.count { it.lane == "Do now" } ?: 0
            val mail = s.feed?.optJSONArray("emails")?.length() ?: 0
            val lockTxt = when { s.tasks == null -> ""; lock == 0 -> "Nothing to lock in"; lock == 1 -> "1 to lock in"; else -> "$lock to lock in" }
            val mailTxt = if (mail > 0) (if (mail == 1) "1 reply due" else "$mail replies due") else ""
            val leftTxt = listOf(lockTxt, mailTxt).filter { it.isNotEmpty() }.joinToString(" · ")
            val waitTxt = s.fit?.workoutToday?.takeIf { it.isNotBlank() }?.let { clean(it) } ?: ""
            val sleepTxt = s.health?.sleepMin?.takeIf { it > 0 }?.let { "Slept ${Fmt.duration(it)}" } ?: ""

            val gap1 = (if (compact) 5f else 7f) * d
            val gap2 = 5f * d
            val lineH = label.textSize
            var total = lineH + gap1 + big.textSize
            if (!compact) total += gap2 + small.textSize
            var y = ((h - total) / 2f).coerceAtLeast(inset + 6 * d) + lineH

            // line 1: to lock in | replies due and waiting on others
            val wW = if (waitTxt.isEmpty()) 0f else minOf(label.measureText(waitTxt), inner * 0.5f)
            text(leftTxt, left, y, label, inner - wW - 10 * d)
            if (waitTxt.isNotEmpty()) text(waitTxt, right, y, label, wW, right = true)

            // line 2: top task
            y += gap1 + big.textSize
            val top = s.tasks?.firstOrNull()?.title?.let { clean(it) }?.takeIf { it.isNotEmpty() }
                ?: if (Store.token(c).isEmpty()) "Open HQ to set up" else "Nothing in Do now"
            text(top, left, y, big, inner)

            // line 3: next event | sleep
            if (!compact) {
                y += gap2 + small.textSize
                val ev = nextEvent(s)?.let { clean(it) } ?: ""
                val sW = small.measureText(sleepTxt)
                text(ev, left, y, small, inner - sW - 12 * d)
                if (sleepTxt.isNotEmpty()) text(sleepTxt, right, y, small, sW + 1, right = true)
            }
            return bmp
        }

        private fun nextEvent(s: Snapshot): String? {
            val now = System.currentTimeMillis()
            val e = s.events?.firstOrNull { it.end > now && !it.allDay } ?: s.events?.firstOrNull() ?: return null
            if (e.allDay) return e.title
            val z = ZoneId.systemDefault()
            val t = Instant.ofEpochMilli(e.start).atZone(z)
            val today = LocalDate.now(z)
            val day = when (t.toLocalDate()) {
                today -> ""
                today.plusDays(1) -> "Tmrw "
                else -> t.format(DateTimeFormatter.ofPattern("EEE ", Locale.UK))
            }
            return "Next $day${Fmt.time(t)}  ${e.title}"
        }
    }
}
