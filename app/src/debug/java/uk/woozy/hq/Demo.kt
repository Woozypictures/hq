package uk.woozy.hq

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug builds only (never in the release APK). Launched with the extra demo=true, HQ starts with sample tasks,
 * events, health and feed data so CI can take screenshots on an emulator at phone and tablet sizes. HQ Fit's
 * summary is still read for real when HQ Fit is installed.
 */
object Demo {
    fun apply(c: Context, i: Intent?) {
        if (i?.getBooleanExtra("demo", false) != true) return
        Store.setAsked(c)
        Cfg.set(c, Cfg.get(c).copy(showErrors = false))
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        val n = "https://www.notion.so/00000000000000000000000000000000"
        val tasks = listOf(
            Task("Book the CIMA E2 exam", "CIMA", "Do now", 26.0, today.plusDays(2).toString(), n),
            Task("Send meter readings", "Flat", "Do now", 22.0, today.toString(), n),
            Task("Edit the Lisbon video", "Content", "Schedule", 14.0, "", n),
            Task("Renew car insurance", "Money", "Schedule", 12.0, today.plusDays(9).toString(), n),
            Task("Order gym chalk", "Health", "Park", 4.0, "", n),
        )
        val cds = listOf(
            Countdown("CIMA E2 exam", today.plusDays(23).toString(), 23, n),
            Countdown("Lisbon trip", today.plusDays(41).toString(), 41, n),
        )
        val events = listOf(
            Event(1, "Comm fin team meeting", at(today, 10), at(today, 11), false),
            Event(2, "Gym: Full body B", at(today, 18), at(today, 19), false),
            Event(3, "Flash meeting", at(today.plusDays(1), 9), at(today.plusDays(1), 10), false),
        )
        val history = HealthHistory(now, (30 downTo 0).map { b ->
            val d = today.minusDays(b.toLong())
            DayStat(d.toString(), 6000L + (b * 937L) % 6000L, 380L + (b * 17L) % 120L, 56L + b % 5, 2000.0 + (b * 53) % 400, 120.0 + (b * 7) % 40)
        }, (0..8).map { WeightPt(now - it * 3L * 86_400_000L, 86.2 + it * 0.12) })
        val feed = JSONObject().put("updated", today.toString() + "T06:43")
            .put("youtube", JSONObject().put("channel", "WoozyPictures").put("handle", "@WoozyPictures").put("subs", 1240).put("views", 98200).put("videos", 64).put("views30", 3100).put("subs30", 22))
            .put("instagram", JSONObject().put("handle", "woozypictures").put("followers", 738).put("posts", 6).put("reach30", 494))
            .put("emails", JSONArray().put(JSONObject().put("from", "Astranti").put("subject", "Your E2 mock results").put("url", "https://mail.google.com/")))
            .put("notes", JSONArray().put("Bin day tomorrow"))
        Store.save(c, Snapshot(
            updated = now, tasks = tasks, countdowns = cds, events = events,
            health = Health(7420, 452, 58, 410.0, 2480.0, 1210.0, 86.2),
            weather = Weather(9.0, 3, 11.0, 5.0, 20), feed = feed,
            healthX = HealthExtra(72.0, 14.0, 140.0, 38.0, now - 600_000, now - 9 * 3_600_000L, now - 3_600_000, now - 86_400_000L, now - 1_200_000, 2),
            history = history,
        ))
    }
}
