package uk.woozy.hq

import android.Manifest
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val UK = Locale.UK
private val TAB_NAMES = listOf("Home", "Life", "Health", "Career", "Create")
private const val REPO_URL = "https://github.com/Woozypictures/hq"

class MainActivity : ComponentActivity() {
    private var snap by mutableStateOf(Snapshot())
    private var cfg by mutableStateOf(Settings())
    private var diag by mutableStateOf<Map<String, SourceState>>(emptyMap())
    private var errlog by mutableStateOf<List<ErrEntry>>(emptyList())
    private var loading by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var settingsStart by mutableStateOf("")
    private var tokenSaved by mutableStateOf(false)

    private val calPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { askHealth() }
    private val healthPerm = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh(true) }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        cfg = Cfg.get(this)
        snap = Store.load(this)
        loadDiag()
        tokenSaved = Store.token(this).isNotEmpty()
        RefreshWorker.ensure(this)
        setContent {
            val th = if (cfg.theme == "frosted") FROSTED else HQ
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
                    BackHandler(settingsOpen) { settingsOpen = false }
                    if (settingsOpen) SettingsScreen(
                        s = cfg, onChange = ::update, tokenSaved = tokenSaved, dob = Store.dob(this),
                        snap = snap, diag = diag, errlog = errlog, start = settingsStart,
                        versionText = versionText(), actions = actions(),
                    ) else Screen()
                }
            }
        }
        if (!Store.asked(this)) { if (Store.token(this).isEmpty()) openSettings("connections") else askPermissions() }
        else if (cfg.refreshOnLaunch) refresh(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("refresh", false)) refresh(false)
    }

    override fun onResume() {
        super.onResume()
        if (cfg.refreshOnLaunch && Store.token(this).isNotEmpty() && System.currentTimeMillis() - snap.updated > 5 * 60_000) refresh(false)
    }

    private fun openSettings(section: String = "") { settingsStart = section; settingsOpen = true }

    private fun loadDiag() { diag = Diag.states(this); errlog = Diag.log(this) }

    /** Saves settings and applies side effects (schedule, notification permission). */
    private fun update(n: Settings) {
        val old = cfg
        cfg = n
        Cfg.set(this, n)
        if (old.autoRefresh != n.autoRefresh || old.intervalMin != n.intervalMin) RefreshWorker.reschedule(this)
        val wantsNotif = (n.nOverdue && !old.nOverdue) || (n.nCountdowns && !old.nCountdowns) ||
            (n.nFailed && !old.nFailed) || (n.nSources && !old.nSources)
        if (wantsNotif && !Notifier.allowed(this) && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (old.timezone != n.timezone) HqWidget.updateAll(this)
    }

    private fun refresh(manual: Boolean) {
        if (loading) return
        loading = true
        lifecycleScope.launch {
            try { snap = Repo.refresh(this@MainActivity, manual) } catch (_: Exception) { }
            loadDiag()
            loading = false
        }
    }

    private fun askPermissions() {
        Store.setAsked(this)
        if (!CalendarSrc.granted(this)) calPerm.launch(Manifest.permission.READ_CALENDAR) else askHealth()
    }

    private fun askHealth() {
        if (HealthSrc.available(this)) {
            try { healthPerm.launch(HealthSrc.PERMS + HealthSrc.BACKGROUND) } catch (_: Exception) { refresh(true) }
        } else refresh(true)
    }

    private fun versionText(): String = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        "${pi.versionName} (build ${pi.longVersionCode})"
    } catch (_: Exception) { "unknown" }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun actions() = SettingsActions(
        saveToken = { Store.setToken(this, it); tokenSaved = Store.token(this).isNotEmpty() },
        saveDob = { Store.setDob(this, it) },
        allowAccess = { askPermissions() },
        refreshNow = { refresh(true) },
        manageHealth = {
            try { startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this)) }
            catch (_: Exception) { try { startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")) } catch (_: Exception) { toast("Open Health Connect from your phone settings") } }
        },
        exportPrefs = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Cfg.s.toJson().toString(2))
            startActivity(Intent.createChooser(send, "Export HQ preferences"))
        },
        importPrefs = {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val txt = cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            try {
                if (txt.isNullOrBlank()) error("Clipboard is empty")
                update(Settings.fromJson(JSONObject(txt), Settings()))
                toast("Preferences imported")
            } catch (e: Exception) { toast("Couldn't import: copy exported preferences first") }
        },
        clearCache = { Diag.clear(this); snap = Snapshot(); loadDiag(); toast("Cached data cleared. Settings and token kept.") },
        openRepo = { open(REPO_URL) },
        openReleases = { open("$REPO_URL/releases") },
        licenseText = { try { assets.open("licenses/GoogleSans-OFL.txt").bufferedReader().use { it.readText() } } catch (_: Exception) { "Licence file missing" } },
    )

    private fun open(url: String) {
        if (url.isBlank()) return
        if (url.contains("notion.so")) {
            try {
                val deep = Uri.parse("notion://" + url.substringAfter("://").removePrefix("www.").let { "www.$it" })
                startActivity(Intent(Intent.ACTION_VIEW, deep).setPackage("notion.id"))
                return
            } catch (_: Exception) { }
        }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
    }

    private fun openEvent(id: Long) {
        try { startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))) } catch (_: Exception) { }
    }

    private fun openCalendar() {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()
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
                    0 -> HomeTab(s) { tab = it }
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
        val now = LocalDate.now(Cfg.zone)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(now.format(DateTimeFormatter.ofPattern("EEEE", UK)), 13.sp, th.muted, weight = FontWeight.Medium, caps = true)
                T(now.format(DateTimeFormatter.ofPattern("d MMMM", UK)), 32.sp, th.fg, weight = FontWeight.Bold, caps = true)
                T(if (s.updated > 0) "HQ refreshed " + Fmt.clock(s.updated) else "Not refreshed yet", 11.sp, th.muted, caps = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (loading) "Refreshing" else "Refresh") { refresh(true) }
                Pill("Settings") { openSettings() }
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
                val bg = when { on -> tc; th.glass -> Color(0x14FFFFFF); else -> lerp(th.panel, tc, 0.13f) }
                val fg = when { on -> th.onTab; th.glass -> th.muted; else -> lerp(th.fg, tc, 0.75f) }
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(bg)
                        .clickable { onSelect(i) }.padding(horizontal = 4.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { T(name, 13.sp, fg, weight = FontWeight.SemiBold, maxLines = 1, center = true, caps = true) }
            }
        }
    }

    // ---------- tabs ----------

    private fun tasksIn(s: Snapshot, vararg areas: String): List<Task>? = s.tasks?.filter { it.area in areas }

    @Composable
    private fun HomeTab(s: Snapshot, go: (Int) -> Unit) {
        val th = LocalTh.current
        val problems = diag.values.filter { !it.ok }
        if (cfg.showErrors && problems.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (th.glass) Color(0x14FFFFFF) else lerp(th.panel, th.warn, 0.12f))
                .clickable { openSettings("advanced") }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                T(problems.joinToString(", ") { it.name } + (if (problems.size == 1) " has a problem" else " have problems"), 13.sp, th.fg, weight = FontWeight.SemiBold)
                T(problems.first().lastError + if (problems.first().cached) " Showing saved data." else "", 12.sp, th.muted, maxLines = 2)
                T("Tap for details", 11.sp, th.muted, caps = true)
            }
        }
        cfg.homeOrder.filter { it !in cfg.homeHidden }.forEach { key ->
            when (key) {
                "tasks" -> Section("Priority tasks", "All tasks", { open(Links.TASKS) }) { TasksPanel(s, s.tasks?.take(5)) }
                "steps" -> StepsCard(s) { go(2) }
                "sleep" -> SleepCard(s) { go(2) }
                "rhr" -> RhrCard(s) { go(2) }
                "events" -> Section("Upcoming events", "Open", { openCalendar() }) { EventsPanel(s) }
                "countdowns" -> CountdownRow(s)
                "feed" -> FeedSection(s)
                "weather" -> WeatherCard(s)
            }
        }
        NotionLinks("Open in Notion", listOf("Dashboard" to Links.DASHBOARD, "Today" to Links.TODAY, "Life" to Links.LIFE,
            "Health" to Links.HEALTH, "Career" to Links.CAREER, "Create" to Links.CREATE), th.tab[0])
    }

    @Composable
    private fun LifeTab(s: Snapshot) {
        val tc = LocalTh.current.tab[1]
        Section("Progress") { Panel { LifeBars(tc) } }
        Section("Life admin", "Open", { open(Links.LIFE) }) {
            TasksPanel(s, tasksIn(s, "Flat", "Money", "Life admin"), "Nothing on for flat, money or admin")
        }
        NotionLinks("Open in Notion", Links.LIFE_PAGES + ("L I F E hub" to Links.LIFE), tc)
    }

    @Composable
    private fun HealthTab(s: Snapshot) {
        val tc = LocalTh.current.tab[2]
        HEALTH_CARDS.filter { it !in cfg.healthHidden }.forEach { key ->
            when (key) {
                "steps" -> StepsCard(s, null)
                "sleep" -> SleepCard(s, null)
                "rhr" -> RhrCard(s, null)
                "weight" -> WeightCard(s)
                "nutrition" -> NutritionCard(s)
                "activity" -> ActivityTiles(s)
                "weekly" -> WeeklyCard(s)
            }
        }
        Section("Health tasks") { TasksPanel(s, tasksIn(s, "Health"), "No health tasks") }
        NotionLinks("Open in Notion", Links.HEALTH_PAGES + ("H E A L T H hub" to Links.HEALTH), tc)
    }

    @Composable
    private fun CareerTab(s: Snapshot) {
        val tc = LocalTh.current.tab[3]
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
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Subscribers", nf.format(yt.optLong("subs")), if (yt.optLong("subs30") != 0L) "${yt.optLong("subs30")} in 30d" else null, tc, Modifier.weight(1f))
                Tile("Views", nf.format(yt.optLong("views")), "+${nf.format(yt.optLong("views30"))} in 30d", tc, Modifier.weight(1f))
                Tile("Videos", nf.format(yt.optLong("videos")), null, tc, Modifier.weight(1f))
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

    // ---------- home cards ----------

    @Composable
    private fun CountdownRow(s: Snapshot) {
        val tc = LocalTh.current.tab[0]
        val cds = s.countdowns
        if (cds.isNullOrEmpty()) return
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cds.take(3).forEach { c -> Tile(c.name, c.days.toString(), if (c.days == 1L) "day" else "days", tc, Modifier.weight(1f)) { open(c.url) } }
            repeat(3 - minOf(3, cds.size)) { Spacer(Modifier.weight(1f)) }
        }
    }

    @Composable
    private fun EventsPanel(s: Snapshot) {
        val ev = s.events
        Panel {
            when {
                ev == null -> Muted(if (CalendarSrc.granted(this@MainActivity)) "Calendar hasn't loaded yet" else "Allow calendar access in Settings > Connections")
                ev.isEmpty() -> Muted("Nothing in the next 3 days")
                else -> ev.take(6).forEach { EventRow(it) }
            }
        }
    }

    @Composable
    private fun FeedSection(s: Snapshot) {
        val th = LocalTh.current
        val f = s.feed
        Section("HQ Feed", "Gmail", { open("https://mail.google.com/") }) {
            if (f == null) { Panel { Muted("Waiting for the morning refresh") }; return@Section }
            val notes = f.optJSONArray("notes")
            if (notes != null) for (i in 0 until notes.length()) Flag(notes.getString(i))
            val mail = f.optJSONArray("emails")
            Panel {
                val yt = f.optJSONObject("youtube")
                if (yt != null) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val nf = NumberFormat.getIntegerInstance(UK)
                    T("YouTube · ${nf.format(yt.optLong("subs"))} subs · +${nf.format(yt.optLong("views30"))} views in 30d", 14.sp, th.fg, weight = FontWeight.Medium)
                }
                if (mail != null) for (i in 0 until minOf(mail.length(), 4)) {
                    val m = mail.getJSONObject(i)
                    Column(Modifier.fillMaxWidth().clickable { open(m.optString("url", "https://mail.google.com/")) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        T(m.optString("subject"), 14.sp, th.fg, weight = FontWeight.Medium, maxLines = 1)
                        T("Needs a reply · " + m.optString("from"), 11.sp, th.muted, maxLines = 1)
                    }
                }
                val upd = f.optString("updated")
                if (upd.isNotEmpty()) Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { T("Feed written " + feedTime(upd), 11.sp, th.muted) }
            }
        }
    }

    private fun feedTime(iso: String): String = try {
        val d = LocalDateTime.parse(iso)
        (if (d.toLocalDate() == LocalDate.now(Cfg.zone)) "" else Fmt.dayMonth(d.toLocalDate()) + " ") + Fmt.time(d)
    } catch (_: Exception) { iso }

    @Composable
    private fun WeatherCard(s: Snapshot) {
        val th = LocalTh.current
        val w = s.weather
        Section("Weather · Slough") {
            Panel {
                if (w == null) Muted("Weather hasn't loaded yet")
                else Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(Fmt.temp(w.temp), 30.sp, th.fg, weight = FontWeight.Bold)
                        T("  " + WeatherSrc.label(w.code), 15.sp, th.muted, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    T("High ${Fmt.temp(w.hi)} · Low ${Fmt.temp(w.lo)} · Rain ${w.rain}%", 12.sp, th.muted, caps = true)
                }
            }
        }
    }

    // ---------- health cards ----------

    private val nf: NumberFormat get() = NumberFormat.getIntegerInstance(UK)

    @Composable
    private fun HCard(title: String, onClick: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
        val th = LocalTh.current
        Column(Modifier.fillMaxWidth().card(18.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp)) {
            T(title, 12.sp, th.muted, weight = FontWeight.SemiBold, caps = true)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }

    /** Three clocks: when HQ last refreshed (header), when Health Connect was last read, and when the measurement was recorded. */
    @Composable
    private fun Fresh(recorded: Long?) {
        val th = LocalTh.current
        val st = diag["Health"]
        val parts = mutableListOf<String>()
        parts += if (recorded != null) "Recorded " + Fmt.clock(recorded) else "No recorded time"
        if (st != null && st.lastSuccess > 0) parts += "read " + Fmt.clock(st.lastSuccess)
        if (st != null && !st.ok) parts += "showing saved data"
        val old = recorded != null && System.currentTimeMillis() - recorded > 36 * 3_600_000L
        T(parts.joinToString(" · ") + if (old) " · older reading" else "", 11.sp, if (old) th.amber else th.muted)
    }

    @Composable
    private fun StepsCard(s: Snapshot, go: (() -> Unit)?) {
        val th = LocalTh.current
        val h = s.health
        val steps = h?.steps
        val target = cfg.stepsTarget
        HCard("Steps", go) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            Row(verticalAlignment = Alignment.Bottom) {
                T(steps?.let { nf.format(it) } ?: "-", 30.sp, th.fg, weight = FontWeight.Bold)
                if (target > 0) T(" / ${nf.format(target)}", 14.sp, th.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (target > 0 && steps != null) { Spacer(Modifier.height(8.dp)); Segments((steps.toFloat() / target).coerceIn(0f, 1f), th.tab[2], 12.dp) }
            Spacer(Modifier.height(6.dp))
            Fresh(s.healthX?.stepsAt)
        }
    }

    @Composable
    private fun SleepCard(s: Snapshot, go: (() -> Unit)?) {
        val th = LocalTh.current
        val h = s.health
        val min = h?.sleepMin?.takeIf { it > 0 }
        val target = cfg.sleepTargetMin
        HCard("Sleep", go) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            Row(verticalAlignment = Alignment.Bottom) {
                T(min?.let { Fmt.duration(it) } ?: "-", 30.sp, th.fg, weight = FontWeight.Bold)
                if (target > 0) T(" / ${Fmt.duration(target.toLong())}", 14.sp, th.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (target > 0 && min != null) { Spacer(Modifier.height(8.dp)); Segments((min.toFloat() / target).coerceIn(0f, 1f), th.tab[2], 12.dp) }
            Spacer(Modifier.height(6.dp))
            Fresh(s.healthX?.sleepEnd)
        }
    }

    @Composable
    private fun RhrCard(s: Snapshot, go: (() -> Unit)?) {
        val th = LocalTh.current
        val h = s.health
        HCard("Resting heart rate", go) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            Row(verticalAlignment = Alignment.Bottom) {
                T(h.restingHr?.let { "$it" } ?: "-", 30.sp, th.fg, weight = FontWeight.Bold)
                if (h.restingHr != null) T(" bpm", 14.sp, th.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            Fresh(s.healthX?.rhrAt)
        }
    }

    @Composable
    private fun WeightCard(s: Snapshot) {
        val th = LocalTh.current
        val h = s.health
        val x = s.healthX
        HCard("Weight", null) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            T(h.weightKg?.let { Fmt.weight(it) } ?: "-", 30.sp, th.fg, weight = FontWeight.Bold)
            if (cfg.weighInsPerWeek > 0 && x != null) T("Weigh-ins this week: ${x.weighIns7} of ${cfg.weighInsPerWeek}", 12.sp, th.muted)
            Spacer(Modifier.height(6.dp))
            Fresh(x?.weightAt)
        }
    }

    @Composable
    private fun NutriRow(label: String, value: Double?, target: Int, unit: String) {
        val th = LocalTh.current
        val has = value != null && value > 0
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                T(label, 13.sp, th.muted, Modifier.weight(1f))
                T(if (has) nf.format(value!!.toLong()) + (if (unit.isNotEmpty()) " $unit" else "") + (if (target > 0) " / ${nf.format(target)}" else "") else "-", 15.sp, th.fg, weight = FontWeight.Medium)
            }
            if (has && target > 0) { Spacer(Modifier.height(4.dp)); Segments((value!!.toFloat() / target).coerceIn(0f, 1f), th.tab[2], 8.dp) }
        }
    }

    @Composable
    private fun NutritionCard(s: Snapshot) {
        val th = LocalTh.current
        val h = s.health
        val x = s.healthX
        HCard("Nutrition today", null) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            NutriRow("Calories", h.eatenKcal, cfg.caloriesTarget, "kcal")
            NutriRow("Protein", x?.proteinG, cfg.proteinTarget, "g")
            NutriRow("Fibre", x?.fibreG, cfg.fibreTarget, "g")
            if (cfg.waterTargetMl > 0) T("Water target ${nf.format(cfg.waterTargetMl)} ml saved. HQ doesn't read water yet.", 11.sp, th.muted)
            Spacer(Modifier.height(4.dp))
            Fresh(x?.nutritionAt)
        }
    }

    @Composable
    private fun ActivityTiles(s: Snapshot) {
        val tc = LocalTh.current.tab[2]
        val h = s.health ?: return
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Burned", (h.totalKcal ?: h.activeKcal)?.takeIf { it > 0 }?.let { nf.format(it.toLong()) } ?: "-", "kcal", tc, Modifier.weight(1f))
            Tile("Active", h.activeKcal?.takeIf { it > 0 }?.let { nf.format(it.toLong()) } ?: "-", "kcal", tc, Modifier.weight(1f))
        }
    }

    @Composable
    private fun WeeklyCard(s: Snapshot) {
        val th = LocalTh.current
        var span by rememberSaveable { mutableIntStateOf(7) }
        val hist = s.history
        HCard("Weekly overview", null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7 days", 30 to "30 days").forEach { (n, label) ->
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (span == n) th.tab[2].copy(alpha = if (th.glass) 0.35f else 1f) else Color(0x22888888))
                        .clickable { span = n }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        T(label, 12.sp, if (span == n && !th.glass) Color.White else th.fg, weight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (hist == null || hist.days.isEmpty()) { T("History appears after the first successful health refresh.", 13.sp, th.muted); return@HCard }
            val win = hist.days.takeLast(span)
            @Composable fun row(label: String, f: (DayStat) -> Double?, fmt: (Double) -> String) {
                val v = win.mapNotNull(f)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.weight(1f)) {
                        T(label, 13.sp, th.muted)
                        if (v.isNotEmpty() && v.size < span) T("based on ${v.size} of $span days", 10.sp, th.amber)
                    }
                    T(if (v.isEmpty()) "-" else fmt(v.average()), 15.sp, th.fg, weight = FontWeight.Medium)
                }
            }
            row("Average steps", { it.steps?.toDouble() }) { nf.format(it.toLong()) }
            row("Average sleep", { it.sleepMin?.toDouble() }) { Fmt.duration(it.toLong()) }
            row("Average resting HR", { it.rhr?.toDouble() }) { "${it.toInt()} bpm" }
            row("Average calories", { it.kcal }) { nf.format(it.toLong()) + " kcal" }
            row("Average protein", { it.protein }) { nf.format(it.toLong()) + " g" }
            val cutoff = System.currentTimeMillis() - span * 86_400_000L
            val pts = hist.weights.filter { it.t >= cutoff }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.weight(1f)) {
                    T("Weight", 13.sp, th.muted)
                    if (pts.size == 1) T("1 reading, no trend yet", 10.sp, th.amber)
                    if (pts.size >= 2) T("${pts.size} readings", 10.sp, th.muted)
                }
                T(if (pts.isEmpty()) "-" else Fmt.weight(pts.last().kg) + if (pts.size >= 2) "  " + Fmt.weightDelta(pts.last().kg - pts.first().kg) else "", 15.sp, th.fg, weight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            T("Completed days only for steps, calories and heart rate. Missing days are left out, not counted as zero.", 10.sp, th.muted)
        }
    }

    private fun healthMissing() = if (diag["Health"]?.lastError?.isNotEmpty() == true) diag["Health"]!!.lastError else "Allow Health Connect in Settings > Connections"

    // ---------- building blocks ----------

    @Composable
    private fun Section(title: String, link: String? = null, onLink: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
        val th = LocalTh.current
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(title, if (th.glass) 13.sp else 18.sp, if (th.glass) th.muted else th.fg, Modifier.weight(1f), weight = FontWeight.Bold, caps = true)
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
            T(label, 12.sp, if (th.glass) th.muted else lerp(th.fg, tc, 0.75f), weight = FontWeight.SemiBold, maxLines = 2, caps = true)
            Column {
                T(value, 26.sp, th.fg, weight = FontWeight.Bold, maxLines = 1)
                if (sub != null) T(sub, 11.sp, th.muted, maxLines = 1, caps = true)
            }
        }
    }

    @Composable
    private fun Pill(label: String, onClick: () -> Unit) {
        val th = LocalTh.current
        Box(Modifier.clip(RoundedCornerShape(50)).background(if (th.glass) Color(0x1FFFFFFF) else lerp(th.panel, th.fg, 0.07f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T(label, 12.sp, if (th.glass) th.muted else th.fg, weight = FontWeight.SemiBold, caps = true)
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
                        T(n, 14.sp, if (th.glass) th.fg else lerp(th.fg, tc, 0.75f), weight = FontWeight.SemiBold, caps = true)
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
                    val st = diag["Notion tasks"]
                    Muted(when {
                        Store.token(this@MainActivity).isEmpty() -> "Add your Notion token in Settings > Connections"
                        st != null && !st.ok -> st.lastError
                        else -> "Tasks haven't loaded yet. Tap Refresh."
                    })
                }
                list.isEmpty() -> Muted(empty)
                else -> list.take(8).forEach { t ->
                    Column(Modifier.fillMaxWidth().clickable { open(t.url) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        T(t.title, 15.sp, th.fg, weight = FontWeight.Medium, maxLines = 2)
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
        val z = Cfg.zone
        val start = Instant.ofEpochMilli(e.start).atZone(z)
        val today = LocalDate.now(z)
        val day = when (start.toLocalDate()) {
            today -> ""
            today.plusDays(1) -> "Tmrw "
            else -> start.format(DateTimeFormatter.ofPattern("EEE ", UK))
        }
        val time = if (e.allDay) day.ifEmpty { "Today " } + "all day" else day + Fmt.time(start)
        Row(Modifier.fillMaxWidth().clickable { openEvent(e.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(time.trim(), 12.sp, th.muted, modifier = Modifier.width(96.dp), maxLines = 1, weight = FontWeight.Medium, caps = true)
            T(e.title, 15.sp, th.fg, modifier = Modifier.weight(1f), maxLines = 1, weight = FontWeight.Medium)
        }
    }

    private fun fmtDate(iso: String): String = try { Fmt.dayMonth(LocalDate.parse(iso)) } catch (_: Exception) { iso }

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
        val now = LocalDateTime.now(Cfg.zone)
        val d = now.toLocalDate()
        fun frac(a: LocalDateTime, b: LocalDateTime) =
            (ChronoUnit.SECONDS.between(a, now).toFloat() / ChronoUnit.SECONDS.between(a, b)).coerceIn(0f, 1f)
        val y0 = d.withDayOfYear(1).atStartOfDay()
        val q0 = d.withMonth(((d.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1).atStartOfDay()
        val m0 = d.withDayOfMonth(1).atStartOfDay()
        val first = if (cfg.weekStart == "SUN") DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        val w0 = d.with(TemporalAdjusters.previousOrSame(first)).atStartOfDay()
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
                T(name, 12.sp, th.muted, modifier = Modifier.width(64.dp), weight = FontWeight.Medium, caps = true)
                Box(Modifier.weight(1f)) { Segments(p, tc, 11.dp) }
                T("${(p * 100).toInt()}%", 12.sp, th.muted, modifier = Modifier.width(44.dp), center = true, weight = FontWeight.Medium)
            }
        }
    }
}
