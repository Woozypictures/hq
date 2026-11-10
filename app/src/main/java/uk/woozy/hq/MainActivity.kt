package uk.woozy.hq

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Txt = Color(0xFFFFFFFF)
private val Txt2 = Color(0x8CFFFFFF)
private val Txt3 = Color(0x66FFFFFF)
private val Cap = Color(0x66303030)
private val BarC = Color(0x40FFFFFF)
private val Bg = Color(0xFF2E2E2E)
private val Sans = FontFamily(Font(R.font.productsans))
private val UK = Locale.UK

class MainActivity : ComponentActivity() {
    private var snap by mutableStateOf(Snapshot())
    private var loading by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)

    private val calPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { askHealth() }
    private val healthPerm = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        snap = Store.load(this)
        RefreshWorker.schedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
                Screen()
                if (showSettings) SettingsDialog()
            }
        }
        if (Store.token(this).isEmpty()) showSettings = true
        else if (!Store.asked(this)) askPermissions()
        else refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("refresh", false)) refresh()
    }

    override fun onResume() {
        super.onResume()
        if (Store.token(this).isNotEmpty() && System.currentTimeMillis() - snap.updated > 5 * 60_000) refresh()
    }

    private fun refresh() {
        if (loading) return
        loading = true
        lifecycleScope.launch {
            try { snap = Repo.refresh(this@MainActivity) } catch (_: Exception) { }
            loading = false
        }
    }

    private fun askPermissions() {
        Store.setAsked(this)
        if (!CalendarSrc.granted(this)) calPerm.launch(Manifest.permission.READ_CALENDAR) else askHealth()
    }

    private fun askHealth() {
        if (HealthSrc.available(this)) {
            try { healthPerm.launch(HealthSrc.PERMS + HealthSrc.BACKGROUND) } catch (_: Exception) { refresh() }
        } else refresh()
    }

    private fun open(url: String) {
        if (url.isBlank()) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
    }

    private fun openEvent(id: Long) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)))
        } catch (_: Exception) { }
    }

    private fun openCalendar() {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
    }

    // ---------- UI ----------

    @Composable
    private fun Screen() {
        val s = snap
        Column(
            Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(s)
            s.countdowns?.takeIf { it.isNotEmpty() }?.let { Countdowns(it) }
            Card("Today", onHeader = ::openCalendar) {
                val ev = s.events
                when {
                    ev == null -> Muted("Calendar not connected yet")
                    ev.isEmpty() -> Muted("Nothing in the next 3 days")
                    else -> ev.take(6).forEach { EventRow(it) }
                }
            }
            Card("Do now", onHeader = { open(Links.TASKS) }) {
                val t = s.tasks
                when {
                    t == null -> Muted("Add your Notion token in settings")
                    t.isEmpty() -> Muted("Nothing to do. Nice.")
                    else -> t.take(6).forEach { TaskRow(it) }
                }
            }
            Card("Health", onHeader = { open(Links.HEALTH) }) { HealthBlock(s.health) }
            Card("Progress", onHeader = { open(Links.CAREER) }) { LifeBlock() }
            s.feed?.let { f ->
                Card("Content", onHeader = { open(Links.CREATE) }) { ContentBlock(f) }
                val mail = f.optJSONArray("emails")
                if (mail != null && mail.length() > 0) Card("Inbox", onHeader = { open("https://mail.google.com/") }) {
                    for (i in 0 until minOf(mail.length(), 5)) {
                        val m = mail.getJSONObject(i)
                        Column(Modifier.fillMaxWidth().clickable { open(m.optString("url", "https://mail.google.com/")) }.padding(vertical = 5.dp)) {
                            T(m.optString("subject"), 15.sp, Txt, maxLines = 1)
                            T(m.optString("from"), 12.sp, Txt3, maxLines = 1)
                        }
                    }
                }
            }
            Hubs()
            if (s.errors.isNotEmpty()) T(s.errors.joinToString("\n"), 11.sp, Txt3, upper = false)
            Spacer(Modifier.height(8.dp))
        }
    }

    @Composable
    private fun Header(s: Snapshot) {
        val now = LocalDate.now(LONDON)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(now.format(DateTimeFormatter.ofPattern("EEEE", UK)), 13.sp, Txt2)
                T(now.format(DateTimeFormatter.ofPattern("d MMMM", UK)), 34.sp, Txt, upper = false)
                val w = s.weather
                val line = buildString {
                    if (w != null) append("${w.temp.toInt()}° ${WeatherSrc.label(w.code)} · H ${w.hi.toInt()}° L ${w.lo.toInt()}° · Rain ${w.rain}%")
                    if (s.updated > 0) {
                        if (isNotEmpty()) append("\n")
                        append("Updated " + Instant.ofEpochMilli(s.updated).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }
                T(line, 12.sp, Txt3)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (loading) "Refreshing" else "Refresh") { refresh() }
                Pill("Settings") { showSettings = true }
            }
        }
    }

    @Composable
    private fun Pill(label: String, onClick: () -> Unit) {
        Box(Modifier.glass(18.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T(label, 12.sp, Txt2)
        }
    }

    @Composable
    private fun Countdowns(list: List<Countdown>) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            list.take(3).forEach { c ->
                Column(
                    Modifier.weight(1f).aspectRatio(1f).glass(24.dp).clickable { open(c.url) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    T(c.days.toString(), 30.sp, Txt)
                    T(if (c.days == 1L) "day" else "days", 11.sp, Txt3)
                    Spacer(Modifier.height(4.dp))
                    T(c.name, 11.sp, Txt2, maxLines = 2, center = true)
                }
            }
            repeat(3 - minOf(3, list.size)) { Spacer(Modifier.weight(1f)) }
        }
    }

    @Composable
    private fun Card(title: String, onHeader: () -> Unit, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().glass(26.dp).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onHeader).padding(bottom = 6.dp)) {
                T(title, 13.sp, Txt2, modifier = Modifier.weight(1f))
                T("Open", 11.sp, Txt3)
            }
            content()
        }
    }

    @Composable
    private fun Muted(t: String) = T(t, 13.sp, Txt3, upper = false)

    @Composable
    private fun EventRow(e: Event) {
        val z = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(e.start).atZone(z)
        val today = LocalDate.now(z)
        val day = when (start.toLocalDate()) {
            today -> ""
            today.plusDays(1) -> "Tmrw "
            else -> start.format(DateTimeFormatter.ofPattern("EEE ", UK))
        }
        val time = if (e.allDay) day.ifEmpty { "Today " } + "all day" else day + start.format(DateTimeFormatter.ofPattern("HH:mm"))
        Row(Modifier.fillMaxWidth().clickable { openEvent(e.id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            T(time.trim(), 12.sp, Txt3, modifier = Modifier.width(92.dp), maxLines = 1)
            T(e.title, 15.sp, Txt, upper = false, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }

    @Composable
    private fun TaskRow(t: Task) {
        Column(Modifier.fillMaxWidth().clickable { open(t.url) }.padding(vertical = 5.dp)) {
            T(t.title, 15.sp, Txt, upper = false, maxLines = 2)
            val meta = listOf(t.area, t.lane, t.due.take(10).takeIf { it.isNotEmpty() }?.let { "due " + fmtDate(it) })
                .filter { !it.isNullOrBlank() }.joinToString(" · ")
            T(meta, 11.sp, Txt3)
        }
    }

    private fun fmtDate(iso: String): String = try {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", UK))
    } catch (_: Exception) { iso }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun HealthBlock(h: Health?) {
        if (h == null) { Muted("Allow Health Connect in settings"); return }
        val nf = NumberFormat.getIntegerInstance(UK)
        val goal = Store.stepGoal(this)
        Row(verticalAlignment = Alignment.Bottom) {
            T(h.steps?.let { nf.format(it) } ?: "-", 26.sp, Txt)
            T(" / $goal steps", 14.sp, Txt3, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
        Segments(((h.steps ?: 0L).toFloat() / goal).coerceIn(0f, 1f))
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
            Stat("Sleep", h.sleepMin?.let { "${it / 60}h ${it % 60}m" })
            Stat("Resting HR", h.restingHr?.let { "$it bpm" })
            Stat("Weight", h.weightKg?.let { String.format(UK, "%.1f kg", it) })
            Stat("Eaten", h.eatenKcal?.let { nf.format(it.toLong()) + " kcal" })
            Stat("Burned", (h.totalKcal ?: h.activeKcal)?.let { nf.format(it.toLong()) + " kcal" })
            Stat("Active", h.activeKcal?.let { nf.format(it.toLong()) + " kcal" })
        }
    }

    @Composable
    private fun Stat(label: String, value: String?) {
        Column(Modifier.width(96.dp)) {
            T(value ?: "-", 16.sp, Txt, upper = false)
            T(label, 11.sp, Txt3)
        }
    }

    @Composable
    private fun Segments(pct: Float, height: Dp = 12.dp) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = 30
            val slot = size.width / n
            val filled = (pct * (n - 1)).toInt()
            for (i in 0 until n) {
                val c = when { i == 0 -> Cap; i <= filled -> Txt; else -> BarC }
                drawRect(c, Offset(i * slot, 0f), Size(slot * 0.5f, size.height))
            }
        }
    }

    @Composable
    private fun LifeBlock() {
        val now = LocalDateTime.now(LONDON)
        val d = now.toLocalDate()
        fun frac(a: LocalDateTime, b: LocalDateTime) =
            (ChronoUnit.SECONDS.between(a, now).toFloat() / ChronoUnit.SECONDS.between(a, b)).coerceIn(0f, 1f)
        val y0 = d.withDayOfYear(1).atStartOfDay()
        val q0 = d.withMonth(((d.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1).atStartOfDay()
        val m0 = d.withDayOfMonth(1).atStartOfDay()
        val w0 = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
        val rows = mutableListOf<Pair<String, Float>>()
        Store.dob(this).takeIf { it.length == 10 }?.let {
            try {
                val dob = LocalDate.parse(it).atStartOfDay()
                rows += "Life" to frac(dob, dob.plusYears(80))
            } catch (_: Exception) { }
        }
        rows += "Year" to frac(y0, y0.plusYears(1))
        rows += "Quarter" to frac(q0, q0.plusMonths(3))
        rows += "Month" to frac(m0, m0.plusMonths(1))
        rows += "Week" to frac(w0, w0.plusWeeks(1))
        rows += "Day" to frac(d.atStartOfDay(), d.plusDays(1).atStartOfDay())
        rows.forEach { (name, p) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T(name, 11.sp, Txt2, modifier = Modifier.width(64.dp))
                Box(Modifier.weight(1f)) { Segments(p, 10.dp) }
                T("${(p * 100).toInt()}%", 11.sp, Txt3, modifier = Modifier.width(40.dp), center = true)
            }
        }
    }

    @Composable
    private fun ContentBlock(f: JSONObject) {
        val nf = NumberFormat.getIntegerInstance(UK)
        f.optJSONObject("youtube")?.let { y ->
            T("YouTube · " + y.optString("channel"), 11.sp, Txt3)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Stat("Subs", nf.format(y.optLong("subs")))
                Stat("Views", nf.format(y.optLong("views")))
                Stat("30 days", "+" + nf.format(y.optLong("views30")))
            }
        }
        f.optJSONObject("instagram")?.let { ig ->
            T("Instagram · " + ig.optString("handle"), 11.sp, Txt3)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Stat("Followers", nf.format(ig.optLong("followers")))
                Stat("Reach 30d", if (ig.has("reach30")) nf.format(ig.optLong("reach30")) else "-")
                Stat("Posts", if (ig.has("posts")) nf.format(ig.optLong("posts")) else "-")
            }
        }
        f.optJSONArray("notes")?.let { a ->
            for (i in 0 until a.length()) T("· " + a.getString(i), 12.sp, Txt2, upper = false)
        }
        T("Feed updated " + f.optString("updated").replace("T", " "), 10.sp, Txt3, upper = false)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Hubs() {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to Links.TODAY, "Life" to Links.LIFE, "Health" to Links.HEALTH,
                "Career" to Links.CAREER, "Create" to Links.CREATE, "Dashboard" to Links.DASHBOARD)
                .forEach { (n, u) -> Pill(n) { open(u) } }
        }
    }

    @Composable
    private fun SettingsDialog() {
        var token by remember { mutableStateOf(Store.token(this)) }
        var dob by remember { mutableStateOf(Store.dob(this)) }
        var goal by remember { mutableStateOf(Store.stepGoal(this).toString()) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Color(0xFF383838),
            title = { T("Settings", 16.sp, Txt) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(token, { token = it }, label = { Text("Notion token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(dob, { dob = it }, label = { Text("Birthday (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(goal, { goal = it }, label = { Text("Daily step goal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    TextButton(onClick = { showSettings = false; askPermissions() }) {
                        Text("Allow calendar + Health Connect")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.setToken(this, token); Store.setDob(this, dob)
                    goal.toIntOrNull()?.let { Store.setStepGoal(this, it) }
                    showSettings = false
                    if (!Store.asked(this)) askPermissions() else refresh()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun T(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier,
    upper: Boolean = true, maxLines: Int = Int.MAX_VALUE, center: Boolean = false,
) = Text(
    if (upper) text.uppercase() else text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    style = TextStyle(fontFamily = Sans, fontSize = size, color = color, textAlign = if (center) TextAlign.Center else TextAlign.Start),
)

/** Frosted Glass card: 5% to 22% white fill, 23% to 38% white rim, left to right. */
private fun Modifier.glass(r: Dp): Modifier {
    val shape = RoundedCornerShape(r)
    return this.clip(shape)
        .background(Brush.horizontalGradient(listOf(Color(0x0EFFFFFF), Color(0x37FFFFFF))))
        .border(1.dp, Brush.horizontalGradient(listOf(Color(0x3AFFFFFF), Color(0x60FFFFFF))), shape)
}
