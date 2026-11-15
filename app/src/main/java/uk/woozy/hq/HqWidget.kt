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
 * Compact home-screen widget matching the Music Player Glass KWGT: translucent white gradient card,
 * gradient rim, ProductSans. Left: top priority task. Right: next event and today's training.
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
            HqTabletWidget.updateAll(c)
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
        internal fun clean(t: String) = t.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,:;!?'\"()&/+-%·@#£$°" }.replace(Regex("\\s+"), " ").trim()

        fun draw(c: Context, s: Snapshot, wDp: Int, hDp: Int): Bitmap {
            val d = c.resources.displayMetrics.density.coerceAtMost(3f)
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val font: Typeface = try { c.resources.getFont(R.font.productsans) } catch (e: Exception) { Typeface.DEFAULT }
            val bold = Typeface.create(font, Typeface.BOLD)

            // Glass card copied from the Music Player Glass KWGT: 965 x 296 card, corner 74.97 (a quarter of the height),
            // fill #0EFFFFFF -> #37FFFFFF left to right, rim #3AFFFFFF -> #60FFFFFF at 2.66 / 296 of the height. No underlay.
            val stroke = (h * 2.66f / 296f).coerceAtLeast(1f * d)
            val inset = stroke / 2f
            val rect = RectF(inset, inset, w - inset, h - inset)
            val radius = h * 74.97f / 296f
            cv.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x0EFFFFFF, 0x37FFFFFF, Shader.TileMode.CLAMP)
            })
            cv.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = stroke
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x3AFFFFFF, 0x60FFFFFF, Shader.TileMode.CLAMP)
            })

            fun tp(size: Float, color: Int, face: Typeface = font, tracking: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face; textSize = size * d; this.color = color; letterSpacing = tracking
                fontFeatureSettings = "tnum"
            }
            fun text(t: String, x: Float, baseline: Float, p: TextPaint, maxW: Float, right: Boolean = false) {
                if (t.isEmpty() || maxW <= 0f) return
                val s2 = TextUtils.ellipsize(t, p, maxW, TextUtils.TruncateAt.END).toString()
                cv.drawText(s2, if (right) x - p.measureText(s2) else x, baseline, p)
            }

            // Same hierarchy as the player: label 17 bold secondary (#8C), title 25 white, detail 17 muted (#66).
            val scale = if (hDp < 70) 0.88f else 1f
            val label = tp(11f * scale, 0x8CFFFFFF.toInt(), bold, 0.08f)
            val title = tp(16.5f * scale, TXT)
            val detail = tp(12f * scale, 0x8CFFFFFF.toInt())
            val muted = tp(12f * scale, 0x66FFFFFF)

            val padX = h * 50f / 296f + radius * 0.35f
            val left = padX; val right = w - padX
            val split = left + (right - left) * 0.65f
            val gutter = 12f * d

            // Left zone: micro-label and the top Notion task on one line.
            val mail = s.feed?.optJSONArray("emails")?.length() ?: 0
            val lbl = when {
                mail == 1 -> "TOP PRIORITY · 1 REPLY DUE"
                mail > 1 -> "TOP PRIORITY · $mail REPLIES DUE"
                else -> "TOP PRIORITY"
            }
            val top = s.tasks?.firstOrNull()?.title?.let { clean(it) }?.takeIf { it.isNotEmpty() }
                ?: if (Store.token(c).isEmpty()) "Open HQ to set up" else "Nothing in Do now"
            val gapL = 6f * d
            val blockL = label.textSize + gapL + title.textSize
            var y = (h - blockL) / 2f + label.textSize * 0.82f
            text(lbl, left, y, label, split - gutter - left)
            y += gapL + title.textSize
            text(top, left, y, title, split - gutter - left)

            // Hairline divider, the rim colour at its right-hand strength.
            val divTop = h * 0.26f; val divBot = h * 0.74f
            cv.drawLine(split, divTop, split, divBot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3AFFFFFF; strokeWidth = 1f * d })

            // Right zone: next event over today's training, right-aligned.
            val ev = nextEvent(s)?.let { clean(it) } ?: "No more events"
            val today = LocalDate.now().toString()
            val f = s.fit
            val plan = when {
                f == null -> ""
                f.date == today && !f.workoutToday.isNullOrBlank() -> clean(f.workoutToday) + if (f.workoutDone) " · Done" else ""
                f.date == today -> "Rest Day"
                else -> ""
            }
            val gapR = 7f * d
            val blockR = detail.textSize + (if (plan.isEmpty()) 0f else gapR + muted.textSize)
            var yr = (h - blockR) / 2f + detail.textSize * 0.82f
            val rw = right - split - gutter
            text(ev, right, yr, detail, rw, right = true)
            if (plan.isNotEmpty()) { yr += gapR + muted.textSize; text(plan, right, yr, muted, rw, right = true) }
            return bmp
        }

        /** The next [n] timed events (all-day ones only if nothing else is left), formatted like nextEvent. */
        internal fun nextEvents(s: Snapshot, n: Int): List<String> {
            val now = System.currentTimeMillis()
            val all = s.events.orEmpty()
            val timed = all.filter { it.end > now && !it.allDay }
            return (timed.ifEmpty { all.filter { it.allDay } }).take(n).map { fmt(it) }
        }

        private fun fmt(e: Event): String {
            if (e.allDay) return e.title
            val z = ZoneId.systemDefault()
            val t = Instant.ofEpochMilli(e.start).atZone(z)
            val today = LocalDate.now(z)
            val day = when (t.toLocalDate()) {
                today -> ""
                today.plusDays(1) -> "Tmrw "
                else -> t.format(DateTimeFormatter.ofPattern("EEE ", Locale.UK))
            }
            return "$day${Fmt.time(t)} · ${e.title}"
        }

        internal fun nextEvent(s: Snapshot): String? {
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
            return "$day${Fmt.time(t)} · ${e.title}"
        }
    }
}
