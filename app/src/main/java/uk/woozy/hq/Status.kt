package uk.woozy.hq

import android.Manifest
import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

val SOURCES = listOf("Notion tasks", "Notion countdowns", "HQ Feed", "Weather", "Calendar", "Health", "Health details", "Health history")

data class SourceState(
    val name: String, val lastAttempt: Long = 0, val lastSuccess: Long = 0,
    val ok: Boolean = true, val lastError: String = "", val cached: Boolean = false,
)
data class ErrEntry(val t: Long, val source: String, val msg: String)

/** Per-source status and a short error log. Holds messages only, never tokens. */
object Diag {
    private fun sp(c: Context) = c.getSharedPreferences("hq", Context.MODE_PRIVATE)

    fun states(c: Context): Map<String, SourceState> = try {
        val o = JSONObject(sp(c).getString("diag", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k ->
            o.getJSONObject(k).let {
                SourceState(k, it.optLong("a"), it.optLong("s"), it.optBoolean("ok", true), it.optString("e"), it.optBoolean("c"))
            }
        }
    } catch (_: Exception) { emptyMap() }

    fun log(c: Context): List<ErrEntry> = try {
        val a = JSONArray(sp(c).getString("errlog", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { e -> ErrEntry(e.optLong("t"), e.optString("s"), e.optString("m")) } }
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun record(c: Context, name: String, error: String?, cached: Boolean) {
        val now = System.currentTimeMillis()
        val all = states(c).toMutableMap()
        val old = all[name]
        all[name] = if (error == null) SourceState(name, now, now, true, "", false)
        else SourceState(name, now, old?.lastSuccess ?: 0, false, error, cached)
        val o = JSONObject()
        all.forEach { (k, v) -> o.put(k, JSONObject().put("a", v.lastAttempt).put("s", v.lastSuccess).put("ok", v.ok).put("e", v.lastError).put("c", v.cached)) }
        val e = sp(c).edit().putString("diag", o.toString())
        if (error != null) {
            val l = (listOf(ErrEntry(now, name, error)) + log(c)).take(40)
            e.putString("errlog", JSONArray(l.map { JSONObject().put("t", it.t).put("s", it.source).put("m", it.msg) }).toString())
        }
        e.apply()
    }

    fun clear(c: Context) {
        val e = sp(c).edit().remove("diag").remove("errlog").remove("snap")
        sp(c).all.keys.filter { it.startsWith("n_") }.forEach { e.remove(it) }
        e.apply()
    }
}

/** Optional alerts, off by default. Each category is its own setting and each alert fires at most once a day. */
object Notifier {
    private const val CH = "hq_alerts"

    fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun once(c: Context, key: String): Boolean {
        val sp = c.getSharedPreferences("hq", Context.MODE_PRIVATE)
        if (sp.getBoolean("n_$key", false)) return false
        sp.edit().putBoolean("n_$key", true).apply()
        return true
    }

    private fun post(c: Context, id: Int, title: String, text: String) {
        if (!allowed(c)) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH, "HQ alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build())
    }

    fun afterRefresh(c: Context, s: Snapshot, failedCount: Int, total: Int) {
        val cfg = Cfg.get(c)
        val today = LocalDate.now(Cfg.zone)
        if (cfg.nOverdue) {
            val n = s.tasks?.count { t -> t.due.length >= 10 && runCatching { LocalDate.parse(t.due.take(10)).isBefore(today) }.getOrDefault(false) } ?: 0
            if (n > 0 && once(c, "overdue_$today")) post(c, 1, "Overdue tasks", "$n task" + (if (n == 1) "" else "s") + " past their due date")
        }
        if (cfg.nCountdowns) s.countdowns?.filter { it.days in 0..3 }?.forEach {
            if (once(c, "cd_${it.name}_$today")) post(c, 2 + (it.name.hashCode() and 0xff), it.name, if (it.days == 0L) "Today" else "In ${it.days} day" + (if (it.days == 1L) "" else "s"))
        }
        if (cfg.nFailed && failedCount >= total - 1 && total > 0 && once(c, "failed_$today"))
            post(c, 300, "HQ couldn't refresh", "Most sources failed. Open HQ > Settings > Advanced for details.")
        if (cfg.nSources) {
            val bad = Diag.states(c).values.filter { !it.ok && (it.lastError.contains("Notion") || it.lastError.contains("not granted") || it.lastError.contains("token")) }
            if (bad.isNotEmpty() && once(c, "src_$today")) post(c, 301, "HQ connection problem", bad.first().name + ": " + bad.first().lastError.take(90))
        }
    }
}
