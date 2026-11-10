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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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

private val UK = Locale.UK

/** App theme. "hq" follows the Lore Club HQ look; "frosted" matches the Frosted Glass widget pack. */
class Th(
    val dark: Boolean, val glass: Boolean,
    val bg: Color, val panel: Color, val fg: Color, val muted: Color, val hair: Color,
    val font: FontFamily, val ok: Color, val warn: Color, val amber: Color,
    val tab: List<Color>, val onTab: Color,
)

private val HqFont = FontFamily(
    Font(R.font.hanken_regular, FontWeight.Normal),
    Font(R.font.hanken_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_extrabold, FontWeight.ExtraBold),
)
private val GlassFont = FontFamily(Font(R.font.productsans))

private val HQ = Th(
    dark = false, glass = false,
    bg = Color(0xFFFBF8F2), panel = Color(0xFFFFFFFF), fg = Color(0xFF1D1D1F), muted = Color(0xFF625E57),
    hair = Color(0x1A1D1D1F), font = HqFont, ok = Color(0xFF3E7A4E), warn = Color(0xFFB3401A), amber = Color(0xFFA26A12),
    // Today, Life, Health, Career, Create (HQ palette: home, life, health, work, content)
    tab = listOf(Color(0xFF3F4250), Color(0xFFA63D6B), Color(0xFF2F5F8A), Color(0xFF4B4FA8), Color(0xFFA0522D)),
    onTab = Color.White,
)
private val FROSTED = Th(
    dark = true, glass = true,
    bg = Color(0xFF2E2E2E), panel = Color(0x1FFFFFFF), fg = Color.White, muted = Color(0x8CFFFFFF), hair = Color(0x3AFFFFFF),
    font = GlassFont, ok = Color(0xFFB8E6C4), warn = Color(0xFFFFB4A0), amber = Color(0xFFFFD98A),
    tab = List(5) { Color.White }, onTab = Color(0xFF2E2E2E),
)
private val LocalTh = staticCompositionLocalOf { HQ }
private val Faint = Color(0x66FFFFFF)

private val TAB_NAMES = listOf("Today", "Life", "Health", "Career", "Create")

class MainActivity : ComponentActivity() {
    private var snap by mutableStateOf(Snapshot())
    private var loading by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var themeKey by mutableStateOf("hq")

    private val calPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { askHealth() }
    private val healthPerm = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        snap = Store.load(this)
        themeKey = Store.theme(this)
        RefreshWorker.schedule(this)
        setContent {
            val th = if (themeKey == "frosted") FROSTED else HQ
            val view = LocalView.current
            SideEffect {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !th.dark
                c.isAppearanceLightNavigationBars = !th.dark
            }
            val scheme = if (th.dark) darkColorScheme(background = th.bg, surface = Color(0xFF383838))
            else lightColorScheme(background = th.bg, surface = Color.White)
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalTh provides th) {
                    Screen()
                    if (showSettings) SettingsDialog()
                }
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

    // ---------- screen ----------

    @Composable
    private fun Screen() {
        val th = LocalTh.current
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val scroll = rememberScrollState()
        LaunchedEffect(tab) { scroll.scrollTo(0) }
        val s = snap
        Column(Modifier.fillMaxSize().background(th.bg).statusBarsPadding()) {
            Header(s)
            Tabs(tab) { tab = it }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (tab) {
                    0 -> TodayTab(s) { tab = it }
                    1 -> LifeTab(s)
                    2 -> HealthTab(s)
                    3 -> CareerTab(s)
                    else -> CreateTab(s)
                }
            }
        }
    }

    @Composable
    private fun Header(s: Snapshot) {
        val th = LocalTh.current
        val now = LocalDate.now(LONDON)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(now.format(DateTimeFormatter.ofPattern("EEEE", UK)), 13.sp, th.muted, weight = FontWeight.SemiBold, caps = true)
                T(now.format(DateTimeFormatter.ofPattern("d MMMM", UK)), 32.sp, th.fg, weight = FontWeight.ExtraBold, caps = true)
                val w = s.weather
                val line = buildString {
                    if (w != null) append("${w.temp.toInt()}° ${WeatherSrc.label(w.code)} · H ${w.hi.toInt()}° L ${w.lo.toInt()}° · rain ${w.rain}%")
                    if (s.updated > 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("updated " + Instant.ofEpochMilli(s.updated).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }
                T(line, 11.sp, th.muted, caps = true)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (loading) "Refreshing" else "Refresh") { refresh() }
                Pill("Settings") { showSettings = true }
            }
        }
    }

    @Composable
    private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
        val th = LocalTh.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TAB_NAMES.forEachIndexed { i, name ->
                val tc = th.tab[i]
                val on = i == selected
                val bg = when {
                    on -> tc
                    th.glass -> Color(0x14FFFFFF)
                    else -> lerp(th.panel, tc, 0.13f)
                }
                val fg = when {
                    on -> th.onTab
                    th.glass -> th.muted
                    else -> lerp(th.fg, tc, 0.75f)
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(bg)
                        .clickable { onSelect(i) }.padding(horizontal = 4.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { T(name, 13.sp, fg, weight = FontWeight.ExtraBold, maxLines = 1, center = true, caps = true) }
            }
        }
    }

    // ---------- tabs ----------

    private fun tasksIn(s: Snapshot, vararg areas: String): List<Task> =
        s.tasks?.filter { it.area in areas } ?: emptyList()

    @Composable
    private fun TodayTab(s: Snapshot, go: (Int) -> Unit) {
        val th = LocalTh.current
        val tc = th.tab[0]
        val notes = s.feed?.optJSONArray("notes")
        if (notes != null && notes.length() > 0) Section("Heads up") {
            for (i in 0 until notes.length()) Flag(notes.getString(i))
        }
        s.countdowns?.takeIf { it.isNotEmpty() }?.let { cds ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cds.take(3).forEach { c ->
                    Tile(c.name, c.days.toString(), if (c.days == 1L) "day" else "days", tc, Modifier.weight(1f)) { open(c.url) }
                }
                repeat(3 - minOf(3, cds.size)) { Spacer(Modifier.weight(1f)) }
            }
        }
        val h = s.health
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val nf = NumberFormat.getIntegerInstance(UK)
            Tile("Steps", h?.steps?.let { nf.format(it) } ?: "-", "of ${Store.stepGoal(this@MainActivity)}", th.tab[2], Modifier.weight(1f)) { go(2) }
            Tile("Sleep", h?.sleepMin?.let { "${it / 60}h ${it % 60}m" } ?: "-", "last night", th.tab[2], Modifier.weight(1f)) { go(2) }
            Tile("Eaten", h?.eatenKcal?.let { nf.format(it.toLong()) } ?: "-", "kcal", th.tab[2], Modifier.weight(1f)) { go(2) }
        }
        Section("Calendar", "Open", { openCalendar() }) {
            val ev = s.events
            Panel {
                when {
                    ev == null -> Muted(if (CalendarSrc.granted(this@MainActivity)) "Calendar hasn't loaded yet" else "Allow calendar access in Settings")
                    ev.isEmpty() -> Muted("Nothing in the next 3 days")
                    else -> ev.take(6).forEach { EventRow(it) }
                }
            }
        }
        Section("Do now", "All tasks", { open(Links.TASKS) }) {
            TasksPanel(s, s.tasks?.take(6))
        }
        val mail = s.feed?.optJSONArray("emails")
        if (mail != null && mail.length() > 0) Section("Needs a reply", "Gmail", { open("https://mail.google.com/") }) {
            Panel {
                for (i in 0 until minOf(mail.length(), 5)) {
                    val m = mail.getJSONObject(i)
                    Column(Modifier.fillMaxWidth().clickable { open(m.optString("url", "https://mail.google.com/")) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        T(m.optString("subject"), 15.sp, th.fg, weight = FontWeight.SemiBold, maxLines = 1)
                        T(m.optString("from"), 12.sp, th.muted, maxLines = 1)
                    }
                }
            }
        }
        if (s.errors.isNotEmpty()) Section("Needs attention") {
            Panel { s.errors.forEach { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { T(it, 13.sp, th.warn) } } }
        }
        NotionLinks("Open in Notion", listOf("Dashboard" to Links.DASHBOARD, "Today" to Links.TODAY, "Life" to Links.LIFE,
            "Health" to Links.HEALTH, "Career" to Links.CAREER, "Create" to Links.CREATE), tc)
    }

    @Composable
    private fun LifeTab(s: Snapshot) {
        val tc = LocalTh.current.tab[1]
        Section("Progress") { Panel { LifeBars(tc) } }
        Section("Life admin", "Open", { open(Links.LIFE) }) {
            TasksPanel(s, s.tasks?.let { tasksIn(s, "Flat", "Money", "Life admin") }, "Nothing on for flat, money or admin")
        }
        NotionLinks("Open in Notion", Links.LIFE_PAGES + ("L I F E hub" to Links.LIFE), tc)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun HealthTab(s: Snapshot) {
        val th = LocalTh.current
        val tc = th.tab[2]
        val nf = NumberFormat.getIntegerInstance(UK)
        val h = s.health
        Section("Today") {
            if (h == null) {
                Panel { Muted("Allow Health Connect in Settings") }
            } else {
                val goal = Store.stepGoal(this@MainActivity)
                Column(Modifier.fillMaxWidth().card(18.dp).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(h.steps?.let { nf.format(it) } ?: "-", 30.sp, th.fg, weight = FontWeight.ExtraBold)
                        T(" / $goal steps", 14.sp, th.muted, modifier = Modifier.padding(bottom = 4.dp), caps = true)
                    }
                    Spacer(Modifier.height(10.dp))
                    Segments(((h.steps ?: 0L).toFloat() / goal).coerceIn(0f, 1f), tc, 14.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Sleep", h.sleepMin?.let { "${it / 60}h ${it % 60}m" } ?: "-", null, tc, Modifier.weight(1f))
                    Tile("Resting HR", h.restingHr?.let { "$it" } ?: "-", "bpm", tc, Modifier.weight(1f))
                    Tile("Weight", h.weightKg?.let { String.format(UK, "%.1f", it) } ?: "-", "kg", tc, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Eaten", h.eatenKcal?.let { nf.format(it.toLong()) } ?: "-", "kcal", tc, Modifier.weight(1f))
                    Tile("Burned", (h.totalKcal ?: h.activeKcal)?.let { nf.format(it.toLong()) } ?: "-", "kcal", tc, Modifier.weight(1f))
                    Tile("Active", h.activeKcal?.let { nf.format(it.toLong()) } ?: "-", "kcal", tc, Modifier.weight(1f))
                }
            }
        }
        Section("Health tasks") { TasksPanel(s, tasksIn(s, "Health"), "No health tasks") }
        NotionLinks("Open in Notion", Links.HEALTH_PAGES + ("H E A L T H hub" to Links.HEALTH), tc)
    }

    @Composable
    private fun CareerTab(s: Snapshot) {
        val th = LocalTh.current
        val tc = th.tab[3]
        Section("CIMA") {
            val cima = s.countdowns?.filter { it.name.contains("CIMA", true) || it.name.contains("E2", true) }
            if (cima.isNullOrEmpty()) Panel { Muted("No CIMA date in Countdowns yet") }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cima.take(2).forEach { c -> Tile(c.name, c.days.toString(), "days to go", tc, Modifier.weight(1f)) { open(Links.CAREER_PAGES[0].second) } }
                if (cima.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Section("Study and work") { TasksPanel(s, tasksIn(s, "CIMA", "Work"), "No CIMA or work tasks") }
        NotionLinks("Open in Notion", Links.CAREER_PAGES + ("C A R E E R hub" to Links.CAREER), tc)
    }

    @Composable
    private fun CreateTab(s: Snapshot) {
        val th = LocalTh.current
        val tc = th.tab[4]
        val nf = NumberFormat.getIntegerInstance(UK)
        val f = s.feed
        val yt = f?.optJSONObject("youtube")
        val ig = f?.optJSONObject("instagram")
        Section("YouTube" + (yt?.optString("channel")?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")) {
            if (yt == null) Panel { Muted("Stats arrive with the morning refresh") }
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Subscribers", nf.format(yt.optLong("subs")), if (yt.optLong("subs30") != 0L) "${yt.optLong("subs30")} in 30d" else null, tc, Modifier.weight(1f))
                    Tile("Views", nf.format(yt.optLong("views")), "+${nf.format(yt.optLong("views30"))} in 30d", tc, Modifier.weight(1f))
                    Tile("Videos", nf.format(yt.optLong("videos")), null, tc, Modifier.weight(1f))
                }
            }
        }
        Section("Instagram" + (ig?.optString("handle")?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")) {
            if (ig == null) Panel { Muted("Instagram isn't connected to vidIQ yet") }
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Followers", nf.format(ig.optLong("followers")), null, tc, Modifier.weight(1f))
                Tile("Reach", if (ig.has("reach30")) nf.format(ig.optLong("reach30")) else "-", "30 days", tc, Modifier.weight(1f))
                Tile("Posts", if (ig.has("posts")) nf.format(ig.optLong("posts")) else "-", null, tc, Modifier.weight(1f))
            }
        }
        Section("Content and music") { TasksPanel(s, tasksIn(s, "Content", "Music"), "No content tasks") }
        NotionLinks("Open in Notion", Links.CREATE_PAGES + ("C R E A T E hub" to Links.CREATE), tc)
    }

    // ---------- building blocks ----------

    @Composable
    private fun Section(title: String, link: String? = null, onLink: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
        val th = LocalTh.current
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(title, if (th.glass) 13.sp else 18.sp, if (th.glass) th.muted else th.fg, Modifier.weight(1f),
                    weight = FontWeight.ExtraBold, caps = true)
                if (link != null && onLink != null)
                    T(link, 12.sp, th.muted, Modifier.clickable { onLink() }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), caps = true)
            }
            content()
        }
    }

    @Composable
    private fun Panel(content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().card(18.dp).padding(vertical = 4.dp), content = content)
    }

    @Composable
    private fun Muted(t: String) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { T(t, 14.sp, LocalTh.current.muted) }
    }

    @Composable
    private fun Flag(text: String) {
        val th = LocalTh.current
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (th.glass) Color(0x14FFFFFF) else lerp(th.panel, th.amber, 0.14f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            T(text, 14.sp, th.fg)
        }
    }

    @Composable
    private fun Tile(label: String, value: String, sub: String?, tc: Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
        val th = LocalTh.current
        Column(
            modifier.heightIn(min = 92.dp).card(16.dp, tc).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            T(label, 12.sp, if (th.glass) th.muted else lerp(th.fg, tc, 0.75f), weight = FontWeight.ExtraBold, maxLines = 2, caps = true)
            Column {
                T(value, 26.sp, th.fg, weight = FontWeight.ExtraBold, maxLines = 1)
                if (sub != null) T(sub, 11.sp, th.muted, maxLines = 1, caps = true)
            }
        }
    }

    @Composable
    private fun Pill(label: String, onClick: () -> Unit) {
        val th = LocalTh.current
        Box(Modifier.clip(RoundedCornerShape(50)).background(if (th.glass) Color(0x1FFFFFFF) else lerp(th.panel, th.fg, 0.07f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T(label, 12.sp, if (th.glass) th.muted else th.fg, weight = FontWeight.ExtraBold, caps = true)
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun NotionLinks(title: String, pages: List<Pair<String, String>>, tc: Color) {
        val th = LocalTh.current
        Section(title) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.forEach { (n, u) ->
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if (th.glass) Color(0x1FFFFFFF) else lerp(th.panel, tc, 0.12f))
                        .clickable { open(u) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        T(n, 14.sp, if (th.glass) th.fg else lerp(th.fg, tc, 0.75f), weight = FontWeight.ExtraBold, caps = true)
                    }
                }
            }
        }
    }

    @Composable
    private fun TasksPanel(s: Snapshot, list: List<Task>?, empty: String = "Nothing to do. Nice.") {
        val th = LocalTh.current
        Panel {
            when {
                list == null -> {
                    val err = s.errors.firstOrNull { it.startsWith("Tasks") }
                    Muted(when {
                        Store.token(this@MainActivity).isEmpty() -> "Add your Notion token in Settings"
                        err != null -> err
                        else -> "Tasks haven't loaded yet. Tap Refresh."
                    })
                }
                list.isEmpty() -> Muted(empty)
                else -> list.take(8).forEach { t ->
                    Column(Modifier.fillMaxWidth().clickable { open(t.url) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        T(t.title, 15.sp, th.fg, weight = FontWeight.SemiBold, maxLines = 2)
                        val meta = listOf(t.area, t.lane, t.due.take(10).takeIf { it.isNotEmpty() }?.let { "due " + fmtDate(it) })
                            .filter { !it.isNullOrBlank() }.joinToString(" · ")
                        T(meta, 11.sp, th.muted, caps = true)
                    }
                }
            }
        }
    }

    @Composable
    private fun EventRow(e: Event) {
        val th = LocalTh.current
        val z = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(e.start).atZone(z)
        val today = LocalDate.now(z)
        val day = when (start.toLocalDate()) {
            today -> ""
            today.plusDays(1) -> "Tmrw "
            else -> start.format(DateTimeFormatter.ofPattern("EEE ", UK))
        }
        val time = if (e.allDay) day.ifEmpty { "Today " } + "all day" else day + start.format(DateTimeFormatter.ofPattern("HH:mm"))
        Row(Modifier.fillMaxWidth().clickable { openEvent(e.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(time.trim(), 12.sp, th.muted, modifier = Modifier.width(96.dp), maxLines = 1, weight = FontWeight.SemiBold, caps = true)
            T(e.title, 15.sp, th.fg, modifier = Modifier.weight(1f), maxLines = 1, weight = FontWeight.SemiBold)
        }
    }

    private fun fmtDate(iso: String): String = try {
        LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", UK))
    } catch (_: Exception) { iso }

    @Composable
    private fun Segments(pct: Float, tc: Color, height: Dp = 12.dp) {
        val th = LocalTh.current
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = 30
            val slot = size.width / n
            val filled = (pct * (n - 1)).toInt()
            val on = if (th.glass) Color.White else tc
            val off = if (th.glass) Color(0x40FFFFFF) else tc.copy(alpha = 0.16f)
            val cap = if (th.glass) Color(0x66303030) else tc.copy(alpha = 0.40f)
            for (i in 0 until n) {
                val c = when { i == 0 -> cap; i <= filled -> on; else -> off }
                drawRect(c, Offset(i * slot, 0f), Size(slot * 0.5f, size.height))
            }
        }
    }

    @Composable
    private fun LifeBars(tc: Color) {
        val th = LocalTh.current
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                T(name, 12.sp, th.muted, modifier = Modifier.width(64.dp), weight = FontWeight.SemiBold, caps = true)
                Box(Modifier.weight(1f)) { Segments(p, tc, 11.dp) }
                T("${(p * 100).toInt()}%", 12.sp, th.muted, modifier = Modifier.width(44.dp), center = true, weight = FontWeight.SemiBold)
            }
        }
    }

    @Composable
    private fun SettingsDialog() {
        val th = LocalTh.current
        var token by remember { mutableStateOf(Store.token(this)) }
        var dob by remember { mutableStateOf(Store.dob(this)) }
        var goal by remember { mutableStateOf(Store.stepGoal(this).toString()) }
        var theme by remember { mutableStateOf(Store.theme(this)) }
        fun save() {
            Store.setToken(this, token); Store.setDob(this, dob)
            goal.toIntOrNull()?.let { Store.setStepGoal(this, it) }
            Store.setTheme(this, theme); themeKey = theme
        }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = if (th.dark) Color(0xFF383838) else Color.White,
            title = { T("Settings", 18.sp, th.fg, weight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(if (Store.token(this@MainActivity).isEmpty()) "Notion token: not saved yet" else "Notion token: saved", 12.sp, th.muted)
                    OutlinedTextField(token, { token = it }, label = { Text("Notion token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(dob, { dob = it }, label = { Text("Birthday (YYYY-MM-DD)") }, singleLine = true)
                    OutlinedTextField(goal, { goal = it }, label = { Text("Daily step goal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    T("Theme", 12.sp, th.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("hq" to "HQ", "frosted" to "Frosted").forEach { (k, label) ->
                            val on = theme == k
                            Box(Modifier.clip(RoundedCornerShape(50))
                                .background(if (on) (if (th.dark) Color.White else Color(0xFF1D1D1F)) else Color(0x22888888))
                                .clickable { theme = k; themeKey = k }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                T(label, 13.sp, if (on) (if (th.dark) Color(0xFF2E2E2E) else Color.White) else th.fg, weight = FontWeight.ExtraBold, caps = true)
                            }
                        }
                    }
                    TextButton(onClick = { save(); showSettings = false; askPermissions() }) {
                        Text("Save and allow calendar + Health Connect")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    save()
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
    weight: FontWeight = FontWeight.Normal, maxLines: Int = Int.MAX_VALUE, center: Boolean = false, caps: Boolean = false,
) {
    val th = LocalTh.current
    Text(
        if (caps && th.glass) text.uppercase() else text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = th.font, fontSize = size, color = color,
            fontWeight = if (th.glass) FontWeight.Normal else weight,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
    )
}

/** Card surface: Frosted = glass gradient + rim; HQ = white panel with a hairline, or a soft tint of the tab colour. */
@Composable
private fun Modifier.card(r: Dp, tint: Color? = null): Modifier {
    val th = LocalTh.current
    val shape = RoundedCornerShape(r)
    return if (th.glass) {
        this.clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x0EFFFFFF), Color(0x37FFFFFF))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color(0x3AFFFFFF), Color(0x60FFFFFF))), shape)
    } else {
        val m = this.clip(shape).background(if (tint != null) lerp(th.panel, tint, 0.12f) else th.panel)
        if (tint == null) m.border(1.dp, th.hair, shape) else m
    }
}
