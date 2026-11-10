package uk.woozy.hq

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

val LONDON: ZoneId = ZoneId.of("Europe/London")

/** Notion page links. Opening these URLs hands off to the Notion app. */
object Links {
    private fun n(id: String) = "https://www.notion.so/" + id.replace("-", "")
    val DASHBOARD = n("ea4d45cd57594ee7987f9120609c8293")
    val TODAY = n("4134afc9-3b00-814e-b6ca-eb472646712a")
    val LIFE = n("4134afc9-3b00-81d7-8080-e75c4a5c171b")
    val HEALTH = n("4134afc9-3b00-813e-865f-cc8dd311d02c")
    val CAREER = n("4134afc9-3b00-8186-b7a4-e51c931d8447")
    val CREATE = n("4134afc9-3b00-81b6-849a-e0d8bf7d4f6c")
    val TASKS = n("89e4f6a36ea9414ca8b4684774ec4284")
}

private fun http(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.requestMethod = method
    c.connectTimeout = 15000
    c.readTimeout = 20000
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    if (body != null) {
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
    }
    val code = c.responseCode
    val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
    if (code !in 200..299) throw IOException("HTTP $code ${txt.take(160)}")
    return txt
}

object NotionSrc {
    private const val TASKS_DS = "b9b6a15d-50d6-46bd-901b-0b332aba7157"
    private const val COUNTDOWNS_DS = "7ec5d191-6cfa-4977-953f-9889beef130a"
    private const val FEED_PAGE = "4144afc9-3b00-8116-889c-f5fc44bf53df"

    private fun call(token: String, method: String, path: String, body: JSONObject? = null): JSONObject =
        JSONObject(http("https://api.notion.com/v1/$path", method, mapOf(
            "Authorization" to "Bearer $token",
            "Notion-Version" to "2025-09-03",
            "Content-Type" to "application/json",
        ), body?.toString()))

    private fun joinText(a: JSONArray?): String =
        if (a == null) "" else (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("plain_text") }

    /** Reads any property as display text, whatever its type. */
    private fun prop(page: JSONObject, name: String): String {
        val p = page.optJSONObject("properties")?.optJSONObject(name) ?: return ""
        return when (val t = p.optString("type")) {
            "title", "rich_text" -> joinText(p.optJSONArray(t))
            "select", "status" -> p.optJSONObject(t)?.optString("name") ?: ""
            "multi_select" -> p.optJSONArray(t)?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } } ?: ""
            "number" -> if (p.isNull("number")) "" else p.optDouble("number").toString()
            "date" -> p.optJSONObject("date")?.optString("start") ?: ""
            "checkbox" -> p.optBoolean("checkbox").toString()
            "formula" -> p.optJSONObject("formula")?.let { f ->
                when (f.optString("type")) {
                    "string" -> f.optString("string")
                    "number" -> if (f.isNull("number")) "" else f.optDouble("number").toString()
                    "boolean" -> f.optBoolean("boolean").toString()
                    "date" -> f.optJSONObject("date")?.optString("start") ?: ""
                    else -> ""
                }
            } ?: ""
            else -> ""
        }
    }

    fun tasks(token: String): List<Task> {
        val r = call(token, "POST", "data_sources/$TASKS_DS/query", JSONObject()
            .put("page_size", 100)
            .put("sorts", JSONArray().put(JSONObject().put("property", "Score").put("direction", "descending"))))
        val res = r.getJSONArray("results")
        return (0 until res.length()).map { res.getJSONObject(it) }.mapNotNull { p ->
            val lane = prop(p, "Lane")
            val status = prop(p, "Status")
            if (status == "Done" || lane == "Done" || lane == "Park") return@mapNotNull null
            Task(prop(p, "Task"), prop(p, "Area"), lane, prop(p, "Score").toDoubleOrNull() ?: 0.0,
                prop(p, "Due"), p.optString("url"))
        }.sortedWith(compareBy<Task> { if (it.lane == "Do now") 0 else 1 }.thenByDescending { it.score }).take(8)
    }

    fun countdowns(token: String): List<Countdown> {
        val r = call(token, "POST", "data_sources/$COUNTDOWNS_DS/query", JSONObject()
            .put("filter", JSONObject().put("property", "Show on Today").put("checkbox", JSONObject().put("equals", true)))
            .put("sorts", JSONArray().put(JSONObject().put("property", "Date").put("direction", "ascending"))))
        val res = r.getJSONArray("results")
        val today = LocalDate.now(LONDON)
        return (0 until res.length()).map { res.getJSONObject(it) }.mapNotNull { p ->
            val d = prop(p, "Date").take(10)
            if (d.length < 10) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, LocalDate.parse(d))
            if (days < 0) null else Countdown(prop(p, "Event"), d, days, p.optString("url"))
        }
    }

    /** The morning Claude task writes a JSON code block into the HQ Feed page. */
    fun feed(token: String): JSONObject? {
        val r = call(token, "GET", "blocks/$FEED_PAGE/children?page_size=50")
        val res = r.getJSONArray("results")
        for (i in 0 until res.length()) {
            val b = res.getJSONObject(i)
            if (b.optString("type") == "code") {
                val txt = joinText(b.getJSONObject("code").optJSONArray("rich_text"))
                return JSONObject(txt)
            }
        }
        return null
    }
}

object WeatherSrc {
    fun read(): Weather {
        val o = JSONObject(http("https://api.open-meteo.com/v1/forecast?latitude=51.51&longitude=-0.59" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=Europe%2FLondon&forecast_days=1"))
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        return Weather(cur.getDouble("temperature_2m"), cur.getInt("weather_code"),
            d.getJSONArray("temperature_2m_max").getDouble(0), d.getJSONArray("temperature_2m_min").getDouble(0),
            d.getJSONArray("precipitation_probability_max").optInt(0))
    }

    fun label(code: Int) = when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Cloudy"
        45, 48 -> "Fog"; in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"
        in 80..82 -> "Showers"; in 85..86 -> "Snow showers"; in 95..99 -> "Thunder"; else -> "-"
    }
}

object CalendarSrc {
    fun granted(c: Context) = c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun read(c: Context): List<Event> {
        if (!granted(c)) throw SecurityException("Calendar access not granted")
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = start + Duration.ofDays(3).toMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start); ContentUris.appendId(it, end)
        }.build()
        val now = System.currentTimeMillis()
        val out = mutableListOf<Event>()
        c.contentResolver.query(uri, arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.VISIBLE,
        ), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cur ->
            while (cur.moveToNext()) {
                if (cur.getInt(5) == 0) continue
                val allDay = cur.getInt(4) == 1
                var b = cur.getLong(2); var e = cur.getLong(3)
                if (allDay) { // all-day instances are stored in UTC midnight; shift to local midnight
                    val off = zone.rules.getOffset(Instant.ofEpochMilli(b)).totalSeconds * 1000L
                    b -= off; e -= off
                }
                if (e <= now) continue
                out += Event(cur.getLong(0), cur.getString(1) ?: "(no title)", b, e, allDay)
            }
        }
        return out.take(10)
    }
}

object HealthSrc {
    val PERMS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    fun available(c: Context) = HealthConnectClient.getSdkStatus(c) == HealthConnectClient.SDK_AVAILABLE

    suspend fun read(c: Context): Health {
        if (!available(c)) throw IllegalStateException("Health Connect not available")
        val hc = HealthConnectClient.getOrCreate(c)
        val granted = hc.permissionController.getGrantedPermissions()
        if (granted.none { it in PERMS }) throw SecurityException("Health access not granted")
        fun has(k: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) =
            HealthPermission.getReadPermission(k) in granted

        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val today = TimeRangeFilter.between(dayStart, now)

        val metrics = buildSet<AggregateMetric<*>> {
            if (has(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (has(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (has(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (has(NutritionRecord::class)) add(NutritionRecord.ENERGY_TOTAL)
        }
        val agg = if (metrics.isEmpty()) null else hc.aggregate(AggregateRequest(metrics, today))

        val sleep = if (has(SleepSessionRecord::class)) {
            // last night: sessions overlapping 6pm yesterday to now
            hc.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                TimeRangeFilter.between(dayStart.minus(Duration.ofHours(6)), now)))[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()
        } else null

        val rhr = if (has(RestingHeartRateRecord::class)) hc.readRecords(ReadRecordsRequest(
            RestingHeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(3)), now),
            ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.beatsPerMinute else null

        val weight = if (has(WeightRecord::class)) hc.readRecords(ReadRecordsRequest(
            WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now),
            ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.weight?.inKilograms else null

        return Health(
            steps = agg?.get(StepsRecord.COUNT_TOTAL),
            sleepMin = sleep,
            restingHr = rhr,
            activeKcal = agg?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            totalKcal = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            eatenKcal = agg?.get(NutritionRecord.ENERGY_TOTAL)?.inKilocalories,
            weightKg = weight,
        )
    }
}

object Repo {
    /** Pulls every source in parallel. A failed source keeps its last good value and adds a short error. */
    suspend fun refresh(c: Context): Snapshot = withContext(Dispatchers.IO) {
        val old = Store.load(c)
        val token = Store.token(c)
        val errors = mutableListOf<String>()
        fun <T> ok(name: String, r: Result<T>, prev: T?): T? =
            r.getOrElse { errors += "$name: ${it.message ?: it.javaClass.simpleName}".take(140); prev }

        coroutineScope {
            val tasks = async { runCatching { if (token.isEmpty()) error("add Notion token in settings") else NotionSrc.tasks(token) } }
            val cds = async { runCatching { if (token.isEmpty()) error("no token") else NotionSrc.countdowns(token) } }
            val feed = async { runCatching { if (token.isEmpty()) error("no token") else NotionSrc.feed(token) } }
            val weather = async { runCatching { WeatherSrc.read() } }
            val events = async { runCatching { CalendarSrc.read(c) } }
            val health = async { runCatching { HealthSrc.read(c) } }

            val s = Snapshot(
                updated = System.currentTimeMillis(),
                tasks = ok("Tasks", tasks.await(), old.tasks),
                countdowns = ok("Countdowns", cds.await(), old.countdowns),
                feed = ok("Feed", feed.await(), old.feed),
                weather = ok("Weather", weather.await(), old.weather),
                events = ok("Calendar", events.await(), old.events),
                health = ok("Health", health.await(), old.health),
                errors = errors.distinctBy { it.substringBefore(":") }
                    .filterNot { it.startsWith("Countdowns: no token") || it.startsWith("Feed: no token") },
            )
            Store.save(c, s)
            HqWidget.updateAll(c)
            s
        }
    }
}
