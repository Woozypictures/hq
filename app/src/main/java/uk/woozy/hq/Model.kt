package uk.woozy.hq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Task(val title: String, val area: String, val lane: String, val score: Double, val due: String, val url: String)
data class Countdown(val name: String, val date: String, val days: Long, val url: String)
data class Event(val id: Long, val title: String, val start: Long, val end: Long, val allDay: Boolean)
data class Health(
    val steps: Long?, val sleepMin: Long?, val restingHr: Long?,
    val activeKcal: Double?, val totalKcal: Double?, val eatenKcal: Double?, val weightKg: Double?,
)
data class Weather(val temp: Double, val code: Int, val hi: Double, val lo: Double, val rain: Int)

/** Everything the app and widget show. Any section can be null if its source failed and has never worked. */
data class Snapshot(
    val updated: Long = 0,
    val tasks: List<Task>? = null,
    val countdowns: List<Countdown>? = null,
    val events: List<Event>? = null,
    val health: Health? = null,
    val weather: Weather? = null,
    val feed: JSONObject? = null,
    val errors: List<String> = emptyList(),
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("hq", Context.MODE_PRIVATE)

    fun token(c: Context): String = prefs(c).getString("token", "") ?: ""
    fun setToken(c: Context, t: String) = prefs(c).edit().putString("token", t.trim()).apply()
    fun dob(c: Context): String = prefs(c).getString("dob", "") ?: ""
    fun setDob(c: Context, d: String) = prefs(c).edit().putString("dob", d.trim()).apply()
    fun stepGoal(c: Context): Int = prefs(c).getInt("stepGoal", 8500)
    fun setStepGoal(c: Context, g: Int) = prefs(c).edit().putInt("stepGoal", g).apply()
    fun asked(c: Context): Boolean = prefs(c).getBoolean("asked", false)
    fun setAsked(c: Context) = prefs(c).edit().putBoolean("asked", true).apply()

    fun load(c: Context): Snapshot {
        val raw = prefs(c).getString("snap", null) ?: return Snapshot()
        return try { fromJson(JSONObject(raw)) } catch (e: Exception) { Snapshot() }
    }

    fun save(c: Context, s: Snapshot) = prefs(c).edit().putString("snap", toJson(s).toString()).apply()

    private fun JSONObject.dOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.lOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null

    private fun toJson(s: Snapshot): JSONObject = JSONObject().apply {
        put("updated", s.updated)
        s.tasks?.let { l -> put("tasks", JSONArray(l.map {
            JSONObject().put("title", it.title).put("area", it.area).put("lane", it.lane)
                .put("score", it.score).put("due", it.due).put("url", it.url)
        })) }
        s.countdowns?.let { l -> put("countdowns", JSONArray(l.map {
            JSONObject().put("name", it.name).put("date", it.date).put("days", it.days).put("url", it.url)
        })) }
        s.events?.let { l -> put("events", JSONArray(l.map {
            JSONObject().put("id", it.id).put("title", it.title).put("start", it.start)
                .put("end", it.end).put("allDay", it.allDay)
        })) }
        s.health?.let { h -> put("health", JSONObject().apply {
            put("steps", h.steps ?: JSONObject.NULL); put("sleepMin", h.sleepMin ?: JSONObject.NULL)
            put("restingHr", h.restingHr ?: JSONObject.NULL); put("activeKcal", h.activeKcal ?: JSONObject.NULL)
            put("totalKcal", h.totalKcal ?: JSONObject.NULL); put("eatenKcal", h.eatenKcal ?: JSONObject.NULL)
            put("weightKg", h.weightKg ?: JSONObject.NULL)
        }) }
        s.weather?.let { w -> put("weather", JSONObject().put("temp", w.temp).put("code", w.code)
            .put("hi", w.hi).put("lo", w.lo).put("rain", w.rain)) }
        s.feed?.let { put("feed", it) }
        put("errors", JSONArray(s.errors))
    }

    private fun <T> JSONObject.list(k: String, f: (JSONObject) -> T): List<T>? {
        val a = optJSONArray(k) ?: return null
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    private fun fromJson(o: JSONObject) = Snapshot(
        updated = o.optLong("updated"),
        tasks = o.list("tasks") { Task(it.optString("title"), it.optString("area"), it.optString("lane"),
            it.optDouble("score"), it.optString("due"), it.optString("url")) },
        countdowns = o.list("countdowns") { Countdown(it.optString("name"), it.optString("date"),
            it.optLong("days"), it.optString("url")) },
        events = o.list("events") { Event(it.optLong("id"), it.optString("title"), it.optLong("start"),
            it.optLong("end"), it.optBoolean("allDay")) },
        health = o.optJSONObject("health")?.let { Health(it.lOrNull("steps"), it.lOrNull("sleepMin"),
            it.lOrNull("restingHr"), it.dOrNull("activeKcal"), it.dOrNull("totalKcal"),
            it.dOrNull("eatenKcal"), it.dOrNull("weightKg")) },
        weather = o.optJSONObject("weather")?.let { Weather(it.optDouble("temp"), it.optInt("code"),
            it.optDouble("hi"), it.optDouble("lo"), it.optInt("rain")) },
        feed = o.optJSONObject("feed"),
        errors = o.optJSONArray("errors")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
    )
}
