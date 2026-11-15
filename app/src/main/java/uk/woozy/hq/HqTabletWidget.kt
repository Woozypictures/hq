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
import android.net.Uri
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * Tablet home-screen widget. Same glass as the phone widget (Music Player Glass KWGT) with more room:
 * top priority plus the next two tasks on the left, next event and today's training on the right, and a row of
 * three glass tiles copied from the Work Harder / Habit Tracker / IdeaBox Glass KWGTs (same fill, rim, corner ratio
 * and Frosted Glass icon font) that open their Notion pages directly. Tapping the top part opens HQ.
 *
 * The click areas live in res/layout/widget_tablet.xml; [TOP] must match its weights.
 */
class HqTabletWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        Cfg.get(c)
        ids.forEach { render(c, m, it) }
        RefreshWorker.ensure(c)
    }

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) = render(c, m, id)

    companion object {
        /** Share of the height used by the info area; the tile row takes the rest (layout weights 62 / 38). */
        private const val TOP = 0.62f

        private data class Tile(val label: String, val glyph: Char, val url: String)

        private val TILES = listOf(
            Tile("Work Harder", '', Links.TASKS),
            Tile("Habit Tracker", '', Links.n("7ce78dd4e707420c846c9be153232c34")),
            Tile("IdeaBox", '', Links.n("9c01b972e89c402cb5ac6125d7b27241")),
        )

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, HqTabletWidget::class.java)).forEach { render(c, m, it) }
        }

        private fun render(c: Context, m: AppWidgetManager, id: Int) {
            val o = m.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).takeIf { it > 0 }
                ?: o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 460)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 }
                ?: o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200)
            val rv = RemoteViews(c.packageName, R.layout.widget_tablet)
            rv.setImageViewBitmap(R.id.img, draw(c, Store.load(c), wDp.coerceIn(240, 1400), hDp.coerceIn(140, 700)))
            val app = PendingIntent.getActivity(c, 100,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("refresh", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.top, app)
            listOf(R.id.t1, R.id.t2, R.id.t3).forEachIndexed { i, v -> rv.setOnClickPendingIntent(v, notion(c, TILES[i].url, 101 + i)) }
            m.updateAppWidget(id, rv)
        }

        /** Opens a Notion page in the Notion app, or in the browser if Notion isn't installed. */
        private fun notion(c: Context, url: String, code: Int): PendingIntent {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val inApp = Intent(view).setPackage("notion.id")
            val target = if (inApp.resolveActivity(c.packageManager) != null) inApp else view
            return PendingIntent.getActivity(c, code, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun glass(cv: Canvas, r: RectF, radius: Float, stroke: Float) {
            cv.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(r.left, 0f, r.right, 0f, 0x0EFFFFFF, 0x37FFFFFF, Shader.TileMode.CLAMP)
            })
            cv.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = stroke
                shader = LinearGradient(r.left, 0f, r.right, 0f, 0x3AFFFFFF, 0x60FFFFFF, Shader.TileMode.CLAMP)
            })
        }

        fun draw(c: Context, s: Snapshot, wDp: Int, hDp: Int): Bitmap {
            val d = c.resources.displayMetrics.density.coerceAtMost(2.5f)
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val font: Typeface = try { c.resources.getFont(R.font.productsans) } catch (e: Exception) { Typeface.DEFAULT }
            val bold = Typeface.create(font, Typeface.BOLD)
            val icons: Typeface? = try { c.resources.getFont(R.font.frostedglass_icons) } catch (e: Exception) { null }

            fun tp(size: Float, color: Int, face: Typeface = font, tracking: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face; textSize = size * d; this.color = color; letterSpacing = tracking; fontFeatureSettings = "tnum"
            }
            fun text(t: String, x: Float, baseline: Float, p: TextPaint, maxW: Float, right: Boolean = false) {
                if (t.isEmpty() || maxW <= 0f) return
                val e = TextUtils.ellipsize(t, p, maxW, TextUtils.TruncateAt.END).toString()
                cv.drawText(e, if (right) x - p.measureText(e) else x, baseline, p)
            }

            // Outer card: Music Player Glass fill and rim. Corner kept to the tile ratio (24.89 / 112) of the tile row
            // so the card and its tiles share one curve family.
            val stroke = (1f * d).coerceAtLeast(1f)
            val topH = h * TOP
            val rowH = h - topH
            val pad = (16f * d).coerceAtMost(rowH * 0.14f)
            val tileH = rowH - pad
            val tileR = tileH * 24.89f / 112f
            val cardR = tileR + pad * 0.6f
            glass(cv, RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2), cardR, stroke)

            val label = tp(11f, 0x8CFFFFFF.toInt(), bold, 0.08f)
            val title = tp(19f, HqWidget.TXT)
            val detail = tp(13.5f, 0x8CFFFFFF.toInt())
            val muted = tp(13.5f, 0x66FFFFFF)

            val padX = pad + cardR * 0.35f
            val left = padX; val right = w - padX
            val split = left + (right - left) * 0.62f
            val gutter = 14f * d

            // Left: top priority and the next two tasks.
            val tasks = s.tasks.orEmpty().map { HqWidget.clean(it.title) }.filter { it.isNotEmpty() }
            val mail = s.feed?.optJSONArray("emails")?.length() ?: 0
            val lbl = when {
                mail == 1 -> "TOP PRIORITY · 1 REPLY DUE"
                mail > 1 -> "TOP PRIORITY · $mail REPLIES DUE"
                else -> "TOP PRIORITY"
            }
            val top = tasks.firstOrNull() ?: if (Store.token(c).isEmpty()) "Open HQ to set up" else "Nothing in Do now"
            val more = tasks.drop(1).take(if (topH > 120f * d) 2 else 1)
            val lw = split - gutter - left
            val gap = 7f * d
            val leftBlock = label.textSize + gap + title.textSize + more.size * (gap * 0.8f + detail.textSize)
            var y = (topH - leftBlock) / 2f + label.textSize * 0.82f + pad * 0.3f
            text(lbl, left, y, label, lw)
            y += gap + title.textSize
            text(top, left, y, title, lw)
            more.forEach { y += gap * 0.8f + detail.textSize; text(it, left, y, detail, lw) }

            cv.drawLine(split, topH * 0.24f + pad * 0.3f, split, topH * 0.86f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3AFFFFFF; strokeWidth = 1f * d
            })

            // Right: next event over today's training, each under its micro-label.
            val ev = HqWidget.nextEvent(s)?.let { HqWidget.clean(it) } ?: "No more events"
            val f = s.fit
            val today = LocalDate.now().toString()
            val plan = when {
                f == null || f.date != today -> "Open Woozy Fit"
                !f.workoutToday.isNullOrBlank() -> HqWidget.clean(f.workoutToday) + if (f.workoutDone) " · Done" else ""
                else -> "Rest Day"
            }
            val rw = right - split - gutter
            val rightBlock = 2 * (label.textSize + gap * 0.6f + detail.textSize) + gap * 1.6f
            var yr = (topH - rightBlock) / 2f + label.textSize * 0.82f + pad * 0.3f
            text("NEXT UP", right, yr, label, rw, right = true)
            yr += gap * 0.6f + detail.textSize; text(ev, right, yr, detail, rw, right = true)
            yr += gap * 1.6f + label.textSize; text("TRAINING", right, yr, label, rw, right = true)
            yr += gap * 0.6f + detail.textSize; text(plan, right, yr, muted, rw, right = true)

            // Tile row: three glass tiles, KWGT fill/rim, icon from the Frosted Glass font, label beside it.
            val cellW = w / 3f
            val tileLabel = tp(14f, HqWidget.TXT)
            val icon = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = icons ?: font; color = HqWidget.TXT
                textSize = (tileH * 0.44f).coerceAtMost(30f * d)
                shader = LinearGradient(0f, topH, 0f, h.toFloat(), 0xFFFFFFFF.toInt(), 0xFFE6E6E6.toInt(), Shader.TileMode.CLAMP)
            }
            TILES.forEachIndexed { i, t ->
                val l = if (i == 0) pad else cellW * i + pad / 2
                val r = if (i == 2) w - pad else cellW * (i + 1) - pad / 2
                val rect = RectF(l, topH, r, topH + tileH)
                glass(cv, rect, tileR, (stroke * 0.9f).coerceAtLeast(1f))
                val cy = rect.centerY()
                val g = t.glyph.toString()
                val iw = if (icons != null) icon.measureText(g) else 0f
                val lblW = tileLabel.measureText(t.label)
                val space = 9f * d
                val total = (if (iw > 0) iw + space else 0f) + lblW
                val x0 = (rect.centerX() - total / 2f).coerceAtLeast(rect.left + tileR * 0.6f)
                if (icons != null) cv.drawText(g, x0, cy + icon.textSize * 0.36f, icon)
                val tx = x0 + if (iw > 0) iw + space else 0f
                text(t.label, tx, cy + tileLabel.textSize * 0.35f, tileLabel, rect.right - tileR * 0.5f - tx)
            }
            return bmp
        }
    }
}
