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
 * 3x1 home-screen widget in the Frosted Glass KWGT style: translucent white gradient card,
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
        const val TXT2 = 0x8CFFFFFF.toInt()
        const val TXT3 = 0x66FFFFFF
        const val CAP = 0x66303030
        const val BAR = 0x40FFFFFF

        fun draw(c: Context, s: Snapshot, wDp: Int, hDp: Int): Bitmap {
            val d = c.resources.displayMetrics.density.coerceAtMost(3f)
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp)
            val font: Typeface = try { c.resources.getFont(R.font.productsans) } catch (e: Exception) { Typeface.DEFAULT }

            // glass card
            val inset = 1.2f * d
            val rect = RectF(inset, inset, w - inset, h - inset)
            val radius = minOf(30f * d, h / 2.6f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x0EFFFFFF, 0x37FFFFFF, Shader.TileMode.CLAMP)
            }
            cv.drawRoundRect(rect, radius, radius, fill)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.1f * d
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0x3AFFFFFF, 0x60FFFFFF, Shader.TileMode.CLAMP)
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

            val padX = maxOf(radius * 0.62f, 16f * d)
            val left = padX; val right = w - padX; val inner = right - left
            val compact = hDp < 96

            val label = tp(11f, TXT2)
            val big = tp(if (compact) 15f else 17f, TXT)
            val small = tp(11f, TXT3)

            // line 1: date + weather | next countdown
            val date = LocalDate.now(Cfg.zone).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK))
            val wx = s.weather?.let { " · ${Fmt.temp(it.temp)} ${WeatherSrc.label(it.code)}" } ?: ""
            val cd = s.countdowns?.firstOrNull()?.let { "${it.name} ${it.days}d" } ?: ""
            val cdW = if (cd.isEmpty()) 0f else minOf(label.measureText(cd.uppercase()), inner * 0.45f)
            var y = (if (compact) 12f else 16f) * d + label.textSize
            text((date + wx).uppercase(), left, y, label, inner - cdW - 8 * d)
            if (cd.isNotEmpty()) text(cd.uppercase(), right, y, label, cdW, right = true)

            // line 2: top task
            y += (if (compact) 5f else 8f) * d + big.textSize
            val top = s.tasks?.firstOrNull()?.title?.uppercase()
                ?: if (Store.token(c).isEmpty()) "OPEN HQ TO SET UP" else "NOTHING IN DO NOW"
            text(top, left, y, big, inner)

            // line 3: next event | steps
            val goal = Store.stepGoal(c)
            val steps = s.health?.steps
            val stepsTxt = steps?.let { "${NumberFormat.getIntegerInstance(Locale.UK).format(it)} / $goal STEPS" } ?: ""
            if (!compact) {
                y += 6f * d + small.textSize
                val ev = nextEvent(s)?.uppercase() ?: ""
                val sW = small.measureText(stepsTxt)
                text(ev, left, y, small, inner - sW - 10 * d)
                text(stepsTxt, right, y, small, sW + 1, right = true)
            }

            // bar: 30 squares, first is the dark cap, filled by steps / goal
            val barH = (if (compact) 7f else 9f) * d
            val barTop = minOf(y + 9f * d, h - inset - barH - (if (compact) 10f else 14f) * d)
            val n = 30
            val slot = inner / n
            val seg = slot * 0.5f
            val pct = ((steps ?: 0L).toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
            val filled = (pct * (n - 1)).toInt()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until n) {
                p.color = when {
                    i == 0 -> CAP
                    i <= filled -> TXT
                    else -> BAR
                }
                val x = left + i * slot
                cv.drawRect(x, barTop, x + seg, barTop + barH, p)
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
            return "Next: $day${Fmt.time(t)} ${e.title}"
        }
    }
}
