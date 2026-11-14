package uk.woozy.hq

/**
 * HQ's own recovery estimate, 0 to 100, from what Health Connect already holds: HRV against its 7 day average,
 * resting heart rate against the recent baseline, and last night's sleep against the target.
 * It is not Samsung's number (Samsung does not share its energy score through Health Connect).
 */
internal class RecoveryEstimate(val score: Int, val parts: List<Pair<String, Int>>) {
    val label: String get() = when { score >= 67 -> "Good"; score >= 34 -> "Okay"; else -> "Low" }
}

internal fun estimateRecovery(s: Snapshot, sleepTargetMin: Int): RecoveryEstimate? {
    val parts = ArrayList<Triple<String, Double, Double>>()
    val hrv = s.sport?.hrvLatest
    val avg = s.sport?.hrvAvg7
    if (hrv != null && avg != null && avg > 0) parts += Triple("HRV", 100 * ((hrv / avg - 0.7) / 0.5).coerceIn(0.0, 1.0), 0.4)
    val rhr = s.health?.restingHr
    val base = s.history?.days?.mapNotNull { it.rhr }?.takeIf { it.size >= 3 }?.average()
    if (rhr != null && base != null) parts += Triple("Resting HR", 100 * (1 - ((rhr - base) + 2) / 10).coerceIn(0.0, 1.0), 0.3)
    val sleep = s.health?.sleepMin
    if (sleep != null && sleep > 0 && sleepTargetMin > 0) parts += Triple("Sleep", 100 * (sleep.toDouble() / sleepTargetMin).coerceIn(0.0, 1.0), 0.3)
    if (parts.size < 2) return null
    val total = parts.sumOf { it.third }
    val score = parts.sumOf { it.second * it.third } / total
    return RecoveryEstimate(score.toInt(), parts.map { it.first to it.second.toInt() })
}
