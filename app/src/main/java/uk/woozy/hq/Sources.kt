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


/** Notion page links. Opening these URLs hands off to the Notion app. */
object Links {
    fun n(id: String) = "https://app.notion.com/p/" + id.replace("-", "")
    val DASHBOARD = n("ea4d45cd57594ee7987f9120609c8293")
    val TODAY = n("4134afc9-3b00-814e-b6ca-eb472646712a")
    val LIFE = n("4134afc9-3b00-81d7-8080-e75c4a5c171b")
    val HEALTH = n("4134afc9-3b00-813e-865f-cc8dd311d02c")
    val CAREER = n("4134afc9-3b00-8186-b7a4-e51c931d8447")
    val CREATE = n("4134afc9-3b00-81b6-849a-e0d8bf7d4f6c")
    val TASKS = n("89e4f6a36ea9414ca8b4684774ec4284")

    val LIFE_PAGES = listOf("Apartment" to n("31d4afc93b0080d09181e30de2acf638"), "Finances" to n("a2c45a544a724dd69cdafdf8eb48697d"),
        "Travel" to n("99e37ebcb21a4a55a229d5e2e7e74bfc"), "Personal care" to n("3bd6e2d57eb545d3ab6d821b2d5d1bbd"),
        "Portfolios" to n("926465794aad4a5197de71eb21e5f8f8"), "Archive" to n("595c786542d74f239295aebce0022e22"))
    val HEALTH_PAGES = listOf("Fitness" to n("835ca4fa122e41ef9956d58451935cb1"), "Habits" to n("7ce78dd4e707420c846c9be153232c34"))
    val CAREER_PAGES = listOf("CIMA" to n("697d8747766a419c83a05a500c5d2dbf"), "Goals" to n("8e5b030f904e4b8ab1cda6d8974719cf"), "Be Greater" to n("d06bec76307649c287d8db606ea4f487"))
    val CREATE_PAGES = listOf("Content" to n("e5fc16e75b32433d995b3c9524d84c3c"), "Music" to n("7fecbd7641b04a1493871bfd467b73a4"),
        "Moodboard" to n("a715bad2ea2349fda7b72b7368f66726"), "Reading" to n("abb746c59e624c53adc15daeea7ebefe"))
}

fun friendly(m: String): String = when {
    "HTTP 401" in m -> "Notion rejected the token. Re-copy it from your HQ connection and paste it in Settings."
    "HTTP 404" in m || "object_not_found" in m -> "Notion can't see that page. On T O D A Y (and each hub) open the ... menu > Connections > add HQ."
    "HTTP 403" in m -> "HQ's Notion connection doesn't have permission for that page."
    "UnknownHost" in m || "Unable to resolve" in m -> "No internet."
    else -> m
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

data class Block(val kind: String, val text: String, val url: String, val checked: Boolean)

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

    /** Contents of a Notion page as simple blocks, for the sub-page views. Nested blocks are not expanded. */
    fun page(token: String, id: String): List<Block> {
        val r = call(token, "GET", "blocks/${id.replace("-", "")}/children?page_size=100")
        val res = r.getJSONArray("results")
        val out = mutableListOf<Block>()
        for (i in 0 until res.length()) {
            val b = res.getJSONObject(i)
            val t = b.optString("type")
            val o = b.optJSONObject(t)
            when (t) {
                "child_page" -> out += Block("page", o?.optString("title") ?: "", Links.n(b.optString("id")), false)
                "child_database" -> out += Block("page", o?.optString("title") ?: "", Links.n(b.optString("id")), false)
                "heading_1", "heading_2", "heading_3" -> joinText(o?.optJSONArray("rich_text")).takeIf { it.isNotBlank() }?.let { out += Block("h", it, "", false) }
                "to_do" -> joinText(o?.optJSONArray("rich_text")).takeIf { it.isNotBlank() }?.let { out += Block("todo", it, "", o?.optBoolean("checked") == true) }
                "paragraph", "callout", "quote", "bulleted_list_item", "numbered_list_item" ->
                    joinText(o?.optJSONArray("rich_text")).takeIf { it.isNotBlank() }?.let { out += Block(if (t.endsWith("list_item")) "li" else "p", it, "", false) }
                "link_to_page" -> {
                    val pid = o?.optString("page_id") ?: ""
                    if (pid.isNotEmpty()) out += Block("page", "Linked page", Links.n(pid), false)
                }
            }
        }
        return out
    }

    fun tasks(token: String): List<Task> {
        val r = call(token, "POST", "data_sources/$TASKS_DS/query", JSONObject().put("page_size", 100))
        val res = r.getJSONArray("results")
        return (0 until res.length()).map { res.getJSONObject(it) }.mapNotNull { p ->
            val lane = prop(p, "Lane")
            val status = prop(p, "Status")
            if (status == "Done" || lane == "Done" || lane == "Park") return@mapNotNull null
            Task(prop(p, "Task"), prop(p, "Area"), lane, prop(p, "Score").toDoubleOrNull() ?: 0.0,
                prop(p, "Due"), p.optString("url"))
        }.sortedWith(compareBy<Task> { if (it.lane == "Do now") 0 else 1 }.thenByDescending { it.score })
    }

    fun countdowns(token: String): List<Countdown> {
        val r = call(token, "POST", "data_sources/$COUNTDOWNS_DS/query", JSONObject()
            .put("filter", JSONObject().put("property", "Show on Today").put("checkbox", JSONObject().put("equals", true)))
            .put("sorts", JSONArray().put(JSONObject().put("property", "Date").put("direction", "ascending"))))
        val res = r.getJSONArray("results")
        val today = LocalDate.now(Cfg.zone)
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
        val end = start + Duration.ofDays(36).toMillis()
        val timedLimit = start + Duration.ofDays(3).toMillis()
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
                if (!allDay && b >= timedLimit) continue
                out += Event(cur.getLong(0), cur.getString(1) ?: "(no title)", b, e, allDay)
            }
        }
        return out.take(80)
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

/** Reads HQ Fit's signature-protected summary. Returns null when HQ Fit is not installed (not an error). */
object FitSrc {
    private const val PKG = "uk.woozy.fit"
    private val URI = android.net.Uri.parse("content://uk.woozy.fit.summary/today")
    private const val SUPPORTED_VERSION = 1

    fun installed(c: Context): Boolean = try { c.packageManager.getPackageInfo(PKG, 0); true } catch (_: Exception) { false }

    fun read(c: Context): FitSummary? {
        if (!installed(c)) return null
        val cur = try { c.contentResolver.query(URI, null, null, null, null) }
        catch (e: SecurityException) { throw SecurityException("HQ Fit permission not granted. Reinstall HQ after HQ Fit") }
            ?: throw IllegalStateException("HQ Fit did not answer")
        cur.use { q ->
            if (!q.moveToFirst()) throw IllegalStateException("HQ Fit data not available. Open HQ Fit once")
            fun idx(n: String) = q.getColumnIndex(n).takeIf { it >= 0 && !q.isNull(it) }
            fun str(n: String) = idx(n)?.let { q.getString(it) }?.takeIf { it.isNotBlank() }
            fun dbl(n: String) = idx(n)?.let { i -> try { q.getDouble(i) } catch (_: Exception) { q.getString(i)?.toDoubleOrNull() } }
            fun lng(n: String) = idx(n)?.let { i -> try { q.getLong(i) } catch (_: Exception) { q.getString(i)?.toLongOrNull() } }
            val version = lng("version")?.toInt() ?: 0
            if (version != SUPPORTED_VERSION) throw IllegalStateException("HQ Fit summary version $version is newer than HQ understands")
            val workout = str("workout_today")
            return FitSummary(
                date = str("date") ?: "", kcal = dbl("kcal"), proteinG = dbl("protein_g"), netCarbsG = dbl("net_carbs_g"),
                fatG = dbl("fat_g"), waterMl = dbl("water_ml"),
                kcalTarget = dbl("kcal_target"), proteinTarget = dbl("protein_target_g"),
                netCarbsTarget = dbl("net_carbs_target_g"), fatTarget = dbl("fat_target_g"),
                weightKg = dbl("weight_kg"), weightAt = lng("weight_at_ms"),
                workoutToday = workout?.takeIf { it != "0" && !it.equals("false", true) },
                workoutDone = (lng("workout_today_done") ?: 0L) != 0L || str("workout_today_done").equals("true", true),
                doneWeek = lng("workouts_done_week")?.toInt(), plannedWeek = lng("workouts_planned_week")?.toInt(),
                checkinDate = str("checkin_date"), checkinMood = str("checkin_mood"),
                checkinMetrics = str("checkin_metrics"), checkinPhoto = str("checkin_photo"),
                readAt = System.currentTimeMillis(),
            )
        }
    }

    /** HQ Fit owns food, water and weight. Where it answered, its values replace Health Connect's so nothing is counted twice. */
    fun overlay(h: Health?, x: HealthExtra?, f: FitSummary?): Pair<Health?, HealthExtra?> {
        if (f == null || h == null) return h to x
        val nh = h.copy(eatenKcal = f.kcal, weightKg = f.weightKg ?: h.weightKg)
        val base = x ?: HealthExtra(null, null, null, null, null, null, null, null, null, 0)
        val nx = base.copy(
            proteinG = f.proteinG, carbsG = f.netCarbsG, fibreG = null, fatG = f.fatG,
            nutritionAt = if ((f.kcal ?: 0.0) > 0) f.readAt else null,
            weightAt = f.weightAt ?: base.weightAt,
        )
        return nh to nx
    }
}

object Repo {
    /** Pulls every source in parallel. A failed source keeps its last good value (if retaining is on) and is logged. */
    suspend fun refresh(c: Context, manual: Boolean = false): Snapshot = withContext(Dispatchers.IO) {
        val cfg = Cfg.get(c)
        val old = Store.load(c)
        val token = Store.token(c)
        val errors = mutableListOf<String>()
        var failed = 0

        fun <T> track(name: String, r: Result<T>, prev: T?): T? {
            val kept = if (cfg.retainData) prev else null
            return r.fold({ v -> Diag.record(c, name, null, false); v }, { e ->
                val m = friendly(e.message ?: e.javaClass.simpleName).take(200)
                Diag.record(c, name, m, kept != null)
                errors += "$name: $m"; failed++
                kept
            })
        }
        val needsToken = "Add the Notion token in Settings > Connections"

        coroutineScope {
            val tasks = async { runCatching { if (token.isEmpty()) error(needsToken) else NotionSrc.tasks(token) } }
            val cds = async { runCatching { if (token.isEmpty()) error(needsToken) else NotionSrc.countdowns(token) } }
            val feed = async { runCatching { if (token.isEmpty()) error(needsToken) else NotionSrc.feed(token) } }
            val weather = async { runCatching { WeatherSrc.read() } }
            val events = async { runCatching { CalendarSrc.read(c) } }
            val health = async { runCatching { HealthSrc.read(c) } }
            val extra = async { runCatching { HealthExtraSrc.readExtra(c) } }
            val fitR = async { runCatching { FitSrc.read(c) } }
            val stale = manual || System.currentTimeMillis() - (old.history?.at ?: 0) > 3 * 3_600_000L
            val history = async { if (stale) runCatching { HealthExtraSrc.readHistory(c) } else null }

            val hr = health.await()
            val fit = track("HQ Fit", fitR.await(), old.fit)
            val hist = history.await()
            val hVal = track("Health", hr, old.health)
            val xVal = if (hr.isSuccess) track("Health details", extra.await(), old.healthX) else old.healthX
            val (hOut, xOut) = FitSrc.overlay(hVal, xVal, fit)
            val s = Snapshot(
                updated = System.currentTimeMillis(),
                tasks = track("Notion tasks", tasks.await(), old.tasks),
                countdowns = track("Notion countdowns", cds.await(), old.countdowns),
                feed = track("HQ Feed", feed.await(), old.feed),
                weather = track("Weather", weather.await(), old.weather),
                events = track("Calendar", events.await(), old.events),
                health = hOut,
                healthX = xOut,
                fit = fit,
                history = if (hist == null) old.history else track("Health history", hist, old.history),
                errors = errors.toList(),
            )
            Store.save(c, s)
            HqWidget.updateAll(c)
            if (!manual) Notifier.afterRefresh(c, s, failed, 6)
            s
        }
    }
}
