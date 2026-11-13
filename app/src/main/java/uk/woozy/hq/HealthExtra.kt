package uk.woozy.hq

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** When each measurement was actually recorded, plus protein and fibre. Read-only extras: the original Health read is unchanged. */
data class HealthExtra(
    val proteinG: Double?, val fibreG: Double?, val carbsG: Double? = null, val fatG: Double? = null,
    val stepsAt: Long?, val sleepEnd: Long?, val rhrAt: Long?, val weightAt: Long?, val nutritionAt: Long?,
    val weighIns7: Int,
) {
    fun toJson() = JSONObject().apply {
        put("p", proteinG ?: JSONObject.NULL); put("f", fibreG ?: JSONObject.NULL); put("c", carbsG ?: JSONObject.NULL); put("fa", fatG ?: JSONObject.NULL)
        put("st", stepsAt ?: JSONObject.NULL); put("sl", sleepEnd ?: JSONObject.NULL); put("r", rhrAt ?: JSONObject.NULL)
        put("w", weightAt ?: JSONObject.NULL); put("n", nutritionAt ?: JSONObject.NULL); put("wi", weighIns7)
    }
    companion object {
        private fun JSONObject.d(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
        private fun JSONObject.l(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
        fun from(o: JSONObject) = HealthExtra(o.d("p"), o.d("f"), o.d("c"), o.d("fa"), o.l("st"), o.l("sl"), o.l("r"), o.l("w"), o.l("n"), o.optInt("wi"))
    }
}

data class DayStat(val date: String, val steps: Long?, val sleepMin: Long?, val rhr: Long?, val kcal: Double?, val protein: Double?)
data class WeightPt(val t: Long, val kg: Double)

/** Per-day values for the last 30 days. A missing value is null, never zero. */
data class HealthHistory(val at: Long, val days: List<DayStat>, val weights: List<WeightPt>) {
    fun toJson() = JSONObject().apply {
        put("at", at)
        put("days", JSONArray(days.map {
            JSONObject().put("d", it.date).put("s", it.steps ?: JSONObject.NULL).put("sl", it.sleepMin ?: JSONObject.NULL)
                .put("r", it.rhr ?: JSONObject.NULL).put("k", it.kcal ?: JSONObject.NULL).put("p", it.protein ?: JSONObject.NULL)
        }))
        put("weights", JSONArray(weights.map { JSONObject().put("t", it.t).put("kg", it.kg) }))
    }
    companion object {
        private fun JSONObject.d(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
        private fun JSONObject.l(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
        fun from(o: JSONObject): HealthHistory {
            val d = o.optJSONArray("days") ?: JSONArray()
            val w = o.optJSONArray("weights") ?: JSONArray()
            return HealthHistory(o.optLong("at"),
                (0 until d.length()).map { d.getJSONObject(it).let { x -> DayStat(x.optString("d"), x.l("s"), x.l("sl"), x.l("r"), x.d("k"), x.d("p")) } },
                (0 until w.length()).map { w.getJSONObject(it).let { x -> WeightPt(x.optLong("t"), x.optDouble("kg")) } })
        }
    }
}

object HealthExtraSrc {
    private suspend fun client(c: Context): Pair<HealthConnectClient, Set<String>> {
        if (!HealthSrc.available(c)) throw IllegalStateException("Health Connect not available")
        val hc = HealthConnectClient.getOrCreate(c)
        val g = hc.permissionController.getGrantedPermissions()
        if (g.none { it in HealthSrc.PERMS }) throw SecurityException("Health access not granted")
        return hc to g
    }

    private fun has(g: Set<String>, k: KClass<out Record>) = HealthPermission.getReadPermission(k) in g

    suspend fun readExtra(c: Context): HealthExtra {
        val (hc, g) = client(c)
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        var protein: Double? = null
        var fibre: Double? = null
        var carbs: Double? = null
        var fat: Double? = null
        if (has(g, NutritionRecord::class)) {
            val a = hc.aggregate(AggregateRequest(setOf(NutritionRecord.PROTEIN_TOTAL, NutritionRecord.DIETARY_FIBER_TOTAL, NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL, NutritionRecord.TOTAL_FAT_TOTAL), TimeRangeFilter.between(dayStart, now)))
            protein = a[NutritionRecord.PROTEIN_TOTAL]?.inGrams
            fibre = a[NutritionRecord.DIETARY_FIBER_TOTAL]?.inGrams
            carbs = a[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL]?.inGrams
            fat = a[NutritionRecord.TOTAL_FAT_TOTAL]?.inGrams
        }
        suspend fun <T : Record> latest(k: KClass<T>, days: Long): T? =
            if (!has(g, k)) null else hc.readRecords(ReadRecordsRequest(k, TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now),
                ascendingOrder = false, pageSize = 1)).records.firstOrNull()
        val weighIns = if (has(g, WeightRecord::class)) hc.readRecords(ReadRecordsRequest(WeightRecord::class,
            TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now), pageSize = 200)).records.size else 0
        return HealthExtra(
            protein, fibre, carbs, fat,
            latest(StepsRecord::class, 3)?.endTime?.toEpochMilli(),
            latest(SleepSessionRecord::class, 3)?.endTime?.toEpochMilli(),
            latest(RestingHeartRateRecord::class, 7)?.time?.toEpochMilli(),
            latest(WeightRecord::class, 120)?.time?.toEpochMilli(),
            latest(NutritionRecord::class, 3)?.endTime?.toEpochMilli(),
            weighIns,
        )
    }

    /** Workouts (last 7 days) and heart rate variability. Null when neither permission is granted yet. */
    suspend fun readSport(c: Context): SportData? {
        val (hc, g) = client(c)
        val now = Instant.now()
        val week = TimeRangeFilter.between(now.minus(Duration.ofDays(7)), now)
        val ex = androidx.health.connect.client.records.ExerciseSessionRecord::class
        val hv = androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord::class
        if (!has(g, ex) && !has(g, hv)) return null
        val sessions = if (has(g, ex)) hc.readRecords(ReadRecordsRequest(ex, week, ascendingOrder = false, pageSize = 100)).records.map {
            val name = androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[it.exerciseType] ?: "workout"
            Session(it.startTime.toEpochMilli(), name.replace('_', ' ').replaceFirstChar { ch -> ch.uppercase() }, Duration.between(it.startTime, it.endTime).toMinutes())
        } else emptyList()
        var latest: Double? = null
        var avg: Double? = null
        if (has(g, hv)) {
            val recs = hc.readRecords(ReadRecordsRequest(hv, week, ascendingOrder = false, pageSize = 200)).records
            latest = recs.firstOrNull()?.heartRateVariabilityMillis
            avg = recs.map { it.heartRateVariabilityMillis }.takeIf { it.isNotEmpty() }?.average()
        }
        return SportData(System.currentTimeMillis(), sessions, latest, avg)
    }

    /** Sleep uses 6pm-to-6pm windows (so a night is one day, overlaps are de-duplicated by Health Connect). */
    suspend fun readHistory(c: Context, days: Int = 30): HealthHistory {
        val (hc, g) = client(c)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(days.toLong())
        val now = Instant.now()

        val dayMetrics = buildSet<AggregateMetric<*>> {
            if (has(g, StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (has(g, RestingHeartRateRecord::class)) add(RestingHeartRateRecord.BPM_AVG)
            if (has(g, NutritionRecord::class)) { add(NutritionRecord.ENERGY_TOTAL); add(NutritionRecord.PROTEIN_TOTAL) }
        }
        val perDay = if (dayMetrics.isEmpty()) emptyList() else hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
            dayMetrics, TimeRangeFilter.between(start.atStartOfDay(), today.atStartOfDay()), Period.ofDays(1)))
        val byDate = perDay.associateBy { it.startTime.toLocalDate() }

        val sleepByDate = HashMap<LocalDate, Long>()
        if (has(g, SleepSessionRecord::class)) {
            val slices = hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
                setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                TimeRangeFilter.between(start.minusDays(1).atTime(18, 0), today.atTime(18, 0)), Period.ofDays(1)))
            slices.forEach { sl ->
                val mins = sl.result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()
                if (mins != null && mins > 0) sleepByDate[sl.startTime.toLocalDate().plusDays(1)] = mins
            }
        }

        val stats = (0..days).map { i ->
            val d = start.plusDays(i.toLong())
            val r = byDate[d]?.result
            val kcal = r?.get(NutritionRecord.ENERGY_TOTAL)?.inKilocalories?.takeIf { it > 0 }
            DayStat(d.toString(),
                r?.get(StepsRecord.COUNT_TOTAL), sleepByDate[d],
                r?.get(RestingHeartRateRecord.BPM_AVG), kcal,
                r?.get(NutritionRecord.PROTEIN_TOTAL)?.inGrams?.takeIf { kcal != null && it > 0 })
        }
        val weights = if (has(g, WeightRecord::class)) hc.readRecords(ReadRecordsRequest(WeightRecord::class,
            TimeRangeFilter.between(now.minus(Duration.ofDays(days.toLong())), now), ascendingOrder = true, pageSize = 200))
            .records.map { WeightPt(it.time.toEpochMilli(), it.weight.inKilograms) } else emptyList()
        return HealthHistory(System.currentTimeMillis(), stats, weights)
    }
}
