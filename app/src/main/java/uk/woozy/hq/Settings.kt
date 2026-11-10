package uk.woozy.hq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import kotlin.math.roundToInt

val HOME_CARDS = listOf("tasks", "steps", "sleep", "rhr", "events", "countdowns", "feed", "weather")
val HOME_NAMES = mapOf(
    "tasks" to "Priority tasks", "steps" to "Steps", "sleep" to "Sleep", "rhr" to "Resting heart rate",
    "events" to "Upcoming events", "countdowns" to "Countdowns", "feed" to "HQ Feed", "weather" to "Weather",
)
val HEALTH_CARDS = listOf("steps", "sleep", "rhr", "weight", "nutrition", "activity", "weekly")
val HEALTH_NAMES = mapOf(
    "steps" to "Steps", "sleep" to "Sleep", "rhr" to "Resting heart rate", "weight" to "Weight",
    "nutrition" to "Nutrition", "activity" to "Calories burned", "weekly" to "Weekly overview",
)
val THEME_IDS = setOf("hq", "frosted", "colourful", "standard", "calm", "seventies", "japandi", "riviera", "editorial", "notebook", "riso", "solarpunk", "bauhaus", "chrome", "digital", "noir", "athletic", "arcade")
val INTERVALS = listOf(15, 30, 60, 120, 240, 360)

/** Every user preference lives here. Targets of 0 mean "not set". */
data class Settings(
    val theme: String = "hq",
    val tempUnit: String = "C",
    val distanceUnit: String = "km",
    val weightUnit: String = "kg",
    val currency: String = "GBP",
    val dateFormat: String = "DD/MM/YYYY",
    val timezone: String = "Europe/London",
    val weekStart: String = "MON",
    val time24: Boolean = true,
    val stepsTarget: Int = 10000,
    val sleepTargetMin: Int = 480,
    val caloriesTarget: Int = 0,
    val proteinTarget: Int = 0,
    val fibreTarget: Int = 0,
    val waterTargetMl: Int = 0,
    val weighInsPerWeek: Int = 0,
    val healthHidden: Set<String> = emptySet(),
    val homeOrder: List<String> = HOME_CARDS,
    val homeHidden: Set<String> = emptySet(),
    val autoRefresh: Boolean = true,
    val intervalMin: Int = 30,
    val refreshOnLaunch: Boolean = true,
    val retainData: Boolean = true,
    val showErrors: Boolean = true,
    val nOverdue: Boolean = false,
    val nCountdowns: Boolean = false,
    val nFailed: Boolean = false,
    val nSources: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme); put("tempUnit", tempUnit); put("distanceUnit", distanceUnit); put("weightUnit", weightUnit)
        put("currency", currency); put("dateFormat", dateFormat); put("timezone", timezone); put("weekStart", weekStart)
        put("time24", time24); put("stepsTarget", stepsTarget); put("sleepTargetMin", sleepTargetMin)
        put("caloriesTarget", caloriesTarget); put("proteinTarget", proteinTarget); put("fibreTarget", fibreTarget)
        put("waterTargetMl", waterTargetMl); put("weighInsPerWeek", weighInsPerWeek)
        put("healthHidden", JSONArray(healthHidden.toList())); put("homeOrder", JSONArray(homeOrder))
        put("homeHidden", JSONArray(homeHidden.toList()))
        put("autoRefresh", autoRefresh); put("intervalMin", intervalMin); put("refreshOnLaunch", refreshOnLaunch)
        put("retainData", retainData); put("showErrors", showErrors)
        put("nOverdue", nOverdue); put("nCountdowns", nCountdowns); put("nFailed", nFailed); put("nSources", nSources)
    }

    companion object {
        /** Reads any subset of keys; missing or invalid values fall back to [b]. */
        fun fromJson(o: JSONObject, b: Settings = Settings()): Settings {
            fun str(k: String, ok: Set<String>, d: String) = if (o.has(k)) o.optString(k, d).takeIf { it in ok } ?: d else d
            fun int(k: String, d: Int, lo: Int, hi: Int) = if (o.has(k)) o.optInt(k, d).coerceIn(lo, hi) else d
            fun bool(k: String, d: Boolean) = if (o.has(k)) o.optBoolean(k, d) else d
            fun list(k: String): List<String>? = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }
            val order = list("homeOrder")?.filter { it in HOME_CARDS }?.distinct() ?: b.homeOrder
            val tz = if (o.has("timezone")) o.optString("timezone") else b.timezone
            return Settings(
                theme = str("theme", THEME_IDS, b.theme),
                tempUnit = str("tempUnit", setOf("C", "F"), b.tempUnit),
                distanceUnit = str("distanceUnit", setOf("km", "mi"), b.distanceUnit),
                weightUnit = str("weightUnit", setOf("kg", "lb", "st"), b.weightUnit),
                currency = str("currency", setOf("GBP", "USD", "EUR"), b.currency),
                dateFormat = str("dateFormat", setOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD", "D MMM YYYY"), b.dateFormat),
                timezone = if (tz == "device" || runCatching { ZoneId.of(tz) }.isSuccess) tz else b.timezone,
                weekStart = str("weekStart", setOf("MON", "SUN"), b.weekStart),
                time24 = bool("time24", b.time24),
                stepsTarget = int("stepsTarget", b.stepsTarget, 0, 100000),
                sleepTargetMin = int("sleepTargetMin", b.sleepTargetMin, 0, 960),
                caloriesTarget = int("caloriesTarget", b.caloriesTarget, 0, 10000),
                proteinTarget = int("proteinTarget", b.proteinTarget, 0, 1000),
                fibreTarget = int("fibreTarget", b.fibreTarget, 0, 500),
                waterTargetMl = int("waterTargetMl", b.waterTargetMl, 0, 20000),
                weighInsPerWeek = int("weighInsPerWeek", b.weighInsPerWeek, 0, 21),
                healthHidden = list("healthHidden")?.filter { it in HEALTH_CARDS }?.toSet() ?: b.healthHidden,
                homeOrder = order + HOME_CARDS.filter { it !in order },
                homeHidden = list("homeHidden")?.filter { it in HOME_CARDS }?.toSet() ?: b.homeHidden,
                autoRefresh = bool("autoRefresh", b.autoRefresh),
                intervalMin = int("intervalMin", b.intervalMin, 15, 1440),
                refreshOnLaunch = bool("refreshOnLaunch", b.refreshOnLaunch),
                retainData = bool("retainData", b.retainData),
                showErrors = bool("showErrors", b.showErrors),
                nOverdue = bool("nOverdue", b.nOverdue), nCountdowns = bool("nCountdowns", b.nCountdowns),
                nFailed = bool("nFailed", b.nFailed), nSources = bool("nSources", b.nSources),
            )
        }
    }
}

/** Settings live in their own file, apart from the cached data and the Notion token. */
object SettingsStore {
    private fun sp(c: Context) = c.getSharedPreferences("hq_settings", Context.MODE_PRIVATE)

    fun load(c: Context): Settings {
        val raw = sp(c).getString("json", null)
        if (raw != null) return try { Settings.fromJson(JSONObject(raw)) } catch (_: Exception) { Settings() }
        // First run of this version: copy theme and step goal from the old store. The old values are left untouched.
        val old = c.getSharedPreferences("hq", Context.MODE_PRIVATE)
        var s = Settings()
        if (old.contains("theme")) s = s.copy(theme = old.getString("theme", "hq")?.takeIf { it in THEME_IDS } ?: "hq")
        if (old.contains("stepGoal")) s = s.copy(stepsTarget = old.getInt("stepGoal", 10000).coerceIn(0, 100000))
        save(c, s)
        return s
    }

    fun save(c: Context, s: Settings) { sp(c).edit().putString("json", s.toJson().toString()).apply() }
}

/** In-memory copy of the settings, shared by the app, widget and background worker. */
object Cfg {
    @Volatile private var cur: Settings? = null
    fun get(c: Context): Settings = cur ?: SettingsStore.load(c).also { cur = it }
    fun set(c: Context, s: Settings) { cur = s; SettingsStore.save(c, s) }
    val s: Settings get() = cur ?: Settings()
    val zone: ZoneId get() = if (s.timezone == "device") ZoneId.systemDefault() else runCatching { ZoneId.of(s.timezone) }.getOrDefault(ZoneId.of("Europe/London"))
}

/** Display formatting that follows the unit and format preferences. */
object Fmt {
    private val UK = Locale.UK
    fun temp(c: Double) = if (Cfg.s.tempUnit == "F") "${(c * 9 / 5 + 32).roundToInt()}°F" else "${c.roundToInt()}°C"

    fun weight(kg: Double): String = when (Cfg.s.weightUnit) {
        "lb" -> String.format(UK, "%.1f lb", kg * 2.20462)
        "st" -> { val lb = kg * 2.20462; val st = (lb / 14).toInt(); String.format(UK, "%d st %.0f lb", st, lb - st * 14) }
        else -> String.format(UK, "%.1f kg", kg)
    }

    fun weightDelta(kg: Double): String {
        val sign = if (kg >= 0) "+" else "-"
        val a = kotlin.math.abs(kg)
        return sign + if (Cfg.s.weightUnit == "kg") String.format(UK, "%.1f kg", a) else String.format(UK, "%.1f lb", a * 2.20462)
    }

    private fun datePattern() = when (Cfg.s.dateFormat) {
        "MM/DD/YYYY" -> "MM/dd/yyyy"; "YYYY-MM-DD" -> "yyyy-MM-dd"; "D MMM YYYY" -> "d MMM yyyy"; else -> "dd/MM/yyyy"
    }

    fun date(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(datePattern(), UK))
    fun dayMonth(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern(if (Cfg.s.dateFormat == "MM/DD/YYYY") "MMM d" else "d MMM", UK))
    fun time(t: TemporalAccessor): String = DateTimeFormatter.ofPattern(if (Cfg.s.time24) "HH:mm" else "h:mm a", UK).format(t)

    /** Time if it was today, otherwise the day and time. */
    fun clock(epoch: Long): String {
        val z = Instant.ofEpochMilli(epoch).atZone(Cfg.zone)
        return if (z.toLocalDate() == LocalDate.now(Cfg.zone)) time(z) else dayMonth(z.toLocalDate()) + " " + time(z)
    }

    fun duration(min: Long) = "${min / 60}h ${min % 60}m"
}
