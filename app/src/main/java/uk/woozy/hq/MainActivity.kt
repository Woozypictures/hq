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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import kotlinx.coroutines.withTimeoutOrNull
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
private fun icon(d: String): ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)).build()
private val TAB_ICONS = listOf(
    icon("M3,13h8L11,3L3,3v10zM3,21h8v-6L3,15v6zM13,21h8L21,11h-8v10zM13,3v6h8L21,3h-8z"),
    icon("M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"),
    icon("M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"),
    icon("M20,6h-4L16,4c0,-1.11 -0.89,-2 -2,-2h-4c-1.11,0 -2,0.89 -2,2v2L4,6c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2L22,8c0,-1.11 -0.89,-2 -2,-2zM10,4h4v2h-4L10,4z"),
    icon("M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z"),
)
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
    private var homeEditing by mutableStateOf(false)

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
            val th = resolveTheme(cfg.theme, cfg.appearance, isSystemInDarkTheme())
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

    /** Opens a link in its own app when installed (Notion, Gmail), otherwise in the browser. */
    private fun open(url: String) {
        if (url.isBlank()) return
        var uri = Uri.parse(url)
        val host = uri.host ?: ""
        // Notion pages: use the canonical share link (page id only), which the app resolves in any workspace
        if (host.endsWith("notion.so") || host.endsWith("notion.com")) {
            Regex("[0-9a-fA-F]{32}").findAll((uri.path ?: "").replace("-", "")).lastOrNull()?.let { uri = Uri.parse("https://app.notion.com/p/" + it.value.lowercase()) }
        }
        val pkg = when {
            host.endsWith("notion.so") || host.endsWith("notion.com") || host.endsWith("notion.site") -> "notion.id"
            host == "mail.google.com" -> "com.google.android.gm"
            else -> null
        }
        fun tryStart(i: Intent): Boolean = try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
        if (pkg != null) {
            // 1. let Android pick the app that owns the link (never the browser)
            if (Build.VERSION.SDK_INT >= 30 &&
                tryStart(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER))) return
            // 2. ask the app directly
            if (tryStart(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))) return
            // 3. Notion's own scheme
            if (pkg == "notion.id" && tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("notion://www.notion.so" + (uri.path ?: "").removePrefix("/p"))).setPackage(pkg))) return
            // 4. at least open the app
            if (host == "mail.google.com") packageManager.getLaunchIntentForPackage(pkg)?.let { if (tryStart(it)) return }
        }
        tryStart(Intent(Intent.ACTION_VIEW, uri))
    }

    /** Opens a link in a specific app when installed, otherwise in the browser. */
    private fun openApp(pkg: String, url: String) {
        fun go(i: Intent): Boolean = try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
        if (!go(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun openWeather() {
        fun go(i: Intent): Boolean = try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
        for (pkg in listOf("com.sec.android.daemonapp", "com.google.android.apps.weather")) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { if (go(it)) return }
        }
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather+Slough")))
    }

    /** Samsung Health tracker pages (steps, sleep, heart rate, weight). Falls back to opening the app. */
    private fun openShealth(kind: String) {
        val pkg = "com.sec.android.app.shealth"
        fun go(i: Intent): Boolean = try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
        if (go(Intent(Intent.ACTION_VIEW, Uri.parse("shealth://tracker.$kind")).setPackage(pkg))) return
        packageManager.getLaunchIntentForPackage(pkg)?.let { if (go(it)) return }
        toast("Samsung Health isn't installed")
    }

    /** Opens the matching Google Calendar event for a countdown, or the calendar on that date. */
    private fun openCountdown(c: Countdown) {
        val day = try { LocalDate.parse(c.date) } catch (_: Exception) { null }
        if (day != null && CalendarSrc.granted(this)) {
            try {
                val z = java.time.ZoneId.systemDefault()
                val b = day.minusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
                val e = day.plusDays(2).atStartOfDay(z).toInstant().toEpochMilli()
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, b); ContentUris.appendId(it, e) }.build()
                val name = c.name.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim().lowercase()
                val words = name.split(" ").filter { it.length > 3 }
                contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE), null, null, null)?.use { cur ->
                    while (cur.moveToNext()) {
                        val t = (cur.getString(1) ?: "").lowercase()
                        if (t.isNotEmpty() && (t.contains(name) || (name.contains(t) && t.length > 3) || words.any { t.contains(it) })) { openEvent(cur.getLong(0)); return }
                    }
                }
            } catch (_: Exception) { }
        }
        val millis = (day ?: LocalDate.now()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, millis) }.build()
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
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
        var sub by rememberSaveable { mutableStateOf("") }
        val scroll = rememberScrollState()
        LaunchedEffect(tab, sub) { scroll.scrollTo(0) }
        val s = snap
        Column(Modifier.fillMaxSize().background(th.bg).statusBarsPadding()) {
            Header(s)
            Tabs(tab) { tab = it; sub = "" }
            val pages = when (tab) { 1 -> Links.LIFE_PAGES; 2 -> Links.HEALTH_PAGES; 3 -> Links.CAREER_PAGES; 4 -> Links.CREATE_PAGES; else -> emptyList() }
            if (pages.isNotEmpty()) Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)) { SubNav(th.tab[tab], pages, sub) { sub = it } }
            @OptIn(ExperimentalMaterial3Api::class)
            PullToRefreshBox(isRefreshing = loading, onRefresh = { refresh(true) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll, enabled = !homeEditing).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (tab) {
                    0 -> HomeTab(s) { tab = it }
                    1 -> LifeTab(s, sub)
                    2 -> HealthTab(s, sub)
                    3 -> CareerTab(s, sub)
                    else -> CreateTab(s, sub)
                }
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
                T(if (loading) "Refreshing..." else if (s.updated > 0) "HQ refreshed " + Fmt.clock(s.updated) else "", 11.sp, th.muted, caps = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (homeEditing) Pill("Done") { homeEditing = false } else Pill("Settings") { openSettings() }
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
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(TAB_ICONS[i], contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                        T(name, 12.sp, fg, weight = FontWeight.SemiBold, maxLines = 1, center = true, caps = true)
                    }
                }
            }
        }
    }

    // ---------- rearranging ----------

    private val dragHeights = mutableStateMapOf<String, Int>()
    private var draggingKey by mutableStateOf<String?>(null)
    private var dragOffset by mutableStateOf(0f)

    /** Long press anywhere on a card (even over its buttons) to start rearranging. */
    private fun Modifier.onLongPress(onLong: () -> Unit): Modifier = pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result = true
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.changes.all { !it.pressed }) break
                    if (e.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) { result = false; break }
                }
                result
            }
            if (r == null) onLong()
        }
    }

    @Composable
    private fun Draggable(tab: String, def: List<String>, key: String, visible: List<String>, content: @Composable () -> Unit) {
        val editing = homeEditing
        val dragging = draggingKey == key
        val gap = with(LocalDensity.current) { 20.dp.toPx() }
        val wiggle = rememberInfiniteTransition(label = "wiggle")
        val phase = (key.hashCode() and 3) * 40
        val angle by wiggle.animateFloat(-0.9f, 0.9f, infiniteRepeatable(tween(130 + phase, easing = LinearEasing), RepeatMode.Reverse), label = "a")
        Box(
            Modifier.onSizeChanged { dragHeights[key] = it.height }
                .graphicsLayer {
                    if (editing && !dragging) rotationZ = angle
                    if (dragging) { translationY = dragOffset; scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 24f }
                }
                .zIndex(if (dragging) 1f else 0f)
                .then(if (!editing) Modifier.onLongPress { homeEditing = true } else Modifier),
        ) {
            content()
            if (editing) Box(
                Modifier.matchParentSize().pointerInput(key) {
                    detectDragGestures(
                        onDragStart = { draggingKey = key; dragOffset = 0f },
                        onDragEnd = { draggingKey = null; dragOffset = 0f },
                        onDragCancel = { draggingKey = null; dragOffset = 0f },
                    ) { change, drag ->
                        change.consume()
                        dragOffset += drag.y
                        val vis = orderOf(tab, def).filter { it in visible }
                        val i = vis.indexOf(key)
                        val other = when {
                            dragOffset > 0 && i < vis.lastIndex -> vis[i + 1]
                            dragOffset < 0 && i > 0 -> vis[i - 1]
                            else -> null
                        }
                        if (other != null) {
                            val oh = (dragHeights[other] ?: 0) + gap
                            if (kotlin.math.abs(dragOffset) > oh / 2) {
                                val order = orderOf(tab, def).toMutableList()
                                val a = order.indexOf(key); val b = order.indexOf(other)
                                order.removeAt(a); order.add(b, key)
                                setOrder(tab, order)
                                dragOffset += if (dragOffset > 0) -oh else oh
                            }
                        }
                    }
                },
            )
        }
    }

    // ---------- tabs ----------

    private fun tasksIn(s: Snapshot, vararg areas: String): List<Task>? = s.tasks?.filter { it.area in areas }

    private fun orderOf(tab: String, def: List<String>): List<String> {
        val saved = if (tab == "home") cfg.homeOrder else cfg.tabOrder[tab] ?: emptyList()
        return saved.filter { it in def }.distinct().let { it + def.filter { d -> d !in it } }
    }

    private fun setOrder(tab: String, order: List<String>) {
        update(if (tab == "home") cfg.copy(homeOrder = order) else cfg.copy(tabOrder = cfg.tabOrder + (tab to order)))
    }

    /** Draws a tab's blocks in the saved order; long-press to rearrange on every tab. */
    @Composable
    private fun Rearrange(tab: String, def: List<String>, hidden: Set<String> = emptySet(), extra: @Composable (String) -> Unit = {}, render: @Composable (String) -> Unit) {
        val th = LocalTh.current
        val vis = orderOf(tab, def).filter { it !in hidden }
        if (homeEditing) T("Drag a card up or down. Tap Done when finished.", 12.sp, th.muted)
        vis.forEach { k ->
            androidx.compose.runtime.key(k) {
                Draggable(tab, def, k, vis) { render(k) }
                if (homeEditing) extra(k)
            }
        }
    }

    private val SUB_AREAS = mapOf("Apartment" to listOf("Flat"), "Finances" to listOf("Money"), "Travel" to listOf("Travel"),
        "CIMA" to listOf("CIMA"), "Content" to listOf("Content"), "Music" to listOf("Music"))

    private fun pageId(url: String): String = Regex("[0-9a-fA-F]{32}").findAll(url.replace("-", "")).lastOrNull()?.value?.lowercase() ?: ""

    private val pageBlocks = mutableStateMapOf<String, List<Block>>()
    private val pageErr = mutableStateMapOf<String, String>()

    private fun loadPage(id: String) {
        if (id.isEmpty()) return
        if (pageBlocks[id] == null) Store.pageCache(this, id)?.let { pageBlocks[id] = it }
        val token = Store.token(this)
        if (token.isEmpty()) { pageErr[id] = "Add your Notion token in Settings > Connections"; return }
        lifecycleScope.launch {
            try {
                val b = withContext(Dispatchers.IO) { NotionSrc.page(token, id) }
                pageBlocks[id] = b; pageErr.remove(id); Store.savePage(this@MainActivity, id, b)
            } catch (e: Exception) { pageErr[id] = friendly(e.message ?: "Couldn't load this page") }
        }
    }

    /** Row of sub-page chips under a main tab. */
    @Composable
    private fun SubNav(tc: Color, pages: List<Pair<String, String>>, sel: String, pick: (String) -> Unit) {
        val th = LocalTh.current
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("" to "Overview") + pages.map { it.first to it.first }).forEach { (k, label) ->
                val on = sel == k
                Box(Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) (if (th.glass) Color(0x55FFFFFF) else tc) else if (th.glass) Color(0x1FFFFFFF) else lerp(th.panel, tc, 0.12f))
                    .clickable { pick(k) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    T(label, 13.sp, if (on && !th.glass) th.onTab else if (th.glass) th.fg else lerp(th.fg, tc, 0.75f), weight = FontWeight.SemiBold, caps = true)
                }
            }
        }
    }

    @Composable
    private fun SubPageView(tc: Color, name: String, url: String, s: Snapshot) {
        val th = LocalTh.current
        val id = pageId(url)
        LaunchedEffect(id) { loadPage(id) }
        val areas = SUB_AREAS[name]
        if (areas != null) Section("Tasks") { TasksPanel(s, tasksIn(s, *areas.toTypedArray()), "No tasks here", "lane") }
        val blocks = pageBlocks[id]
        val err = pageErr[id]
        if (err != null) Flag(err)
        if (blocks == null && err == null) Panel { Muted("Loading...") }
        if (blocks != null) {
            val pages = blocks.filter { it.kind == "page" }
            if (pages.isNotEmpty()) Section("Pages") {
                pages.forEach { b ->
                    Row(Modifier.fillMaxWidth().card(14.dp, tc).clickable { open(b.url) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(b.text.ifBlank { "Untitled" }, 15.sp, th.fg, Modifier.weight(1f), weight = FontWeight.SemiBold, maxLines = 2)
                        T(">", 15.sp, th.muted)
                    }
                }
            }
            val text = blocks.filter { it.kind != "page" }
            if (text.isNotEmpty()) Panel {
                text.take(40).forEach { b ->
                    when (b.kind) {
                        "h" -> Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { T(b.text, 15.sp, th.fg, weight = FontWeight.Bold) }
                        "todo" -> Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            T(if (b.checked) "[x]" else "[ ]", 13.sp, th.muted, Modifier.width(30.dp))
                            T(b.text, 14.sp, if (b.checked) th.muted else th.fg)
                        }
                        else -> Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { T((if (b.kind == "li") "- " else "") + b.text, 14.sp, th.fg) }
                    }
                }
            }
            if (pages.isEmpty() && text.isEmpty()) Panel { Muted("Nothing to show on this page yet") }
        }
        Pill("Open page in Notion") { open(url) }
    }

    @Composable
    private fun HomeTab(s: Snapshot, go: (Int) -> Unit) {
        val th = LocalTh.current
        val problems = diag.values.filter { !it.ok }
        val hour = java.time.LocalTime.now(Cfg.zone).hour
        val greet = if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
        val lock = s.tasks?.count { it.lane == "Do now" } ?: 0
        Column {
            T("$greet, Mahesh.", 20.sp, th.fg, weight = FontWeight.Bold)
            if (lock > 0) T(if (lock == 1) "1 thing to lock in today" else "$lock things to lock in today", 13.sp, th.muted)
        }
        if (cfg.showErrors && problems.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (th.glass) Color(0x14FFFFFF) else lerp(th.panel, th.warn, 0.12f))
                .clickable { openSettings("advanced") }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                T(problems.joinToString(", ") { it.name } + (if (problems.size == 1) " has a problem" else " have problems"), 13.sp, th.fg, weight = FontWeight.SemiBold)
                T(problems.first().lastError + if (problems.first().cached) " Showing saved data." else "", 12.sp, th.muted, maxLines = 2)
                T("Tap for details", 11.sp, th.muted, caps = true)
            }
        }
        val three = listOf("steps", "sleep", "rhr")
        val small = three.filter { it !in cfg.homeWide && it !in cfg.homeHidden }
        val hidden = cfg.homeHidden + (if (small.isEmpty()) setOf("vitals") else emptySet()) +
            three.filter { it !in cfg.homeWide }.toSet() + (if (s.countdowns.isNullOrEmpty()) setOf("countdowns") else emptySet())
        val names = mapOf("steps" to "Steps", "sleep" to "Sleep", "rhr" to "Heart rate")
        Rearrange("home", HOME_CARDS, hidden, extra = { k ->
            if (k == "vitals") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                small.forEach { Pill("${names[it]} wide") { update(cfg.copy(homeWide = cfg.homeWide + it)) } }
            }
            if (k in three) Pill("Make ${names[k]} small") { update(cfg.copy(homeWide = cfg.homeWide - k)) }
        }) { key ->
            when (key) {
                "tasks" -> Section("Priority tasks", "All tasks", { open(Links.TASKS) }) { TasksPanel(s, s.tasks?.take(6), "Nothing to do. Nice.", "lane") }
                "vitals" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    small.forEach { k ->
                        when (k) {
                            "steps" -> MiniStat("Steps", s.health?.steps?.let { nf.format(it) } ?: "-", if (cfg.stepsTarget > 0) "of ${nf.format(cfg.stepsTarget)}" else null,
                                s.health?.steps?.let { if (cfg.stepsTarget > 0) (it.toFloat() / cfg.stepsTarget).coerceIn(0f, 1f) else null }, Modifier.weight(1f)) { openShealth("pedometer") }
                            "sleep" -> MiniStat("Sleep", s.health?.sleepMin?.takeIf { it > 0 }?.let { Fmt.duration(it) } ?: "-", if (cfg.sleepTargetMin > 0) "of ${Fmt.duration(cfg.sleepTargetMin.toLong())}" else null,
                                s.health?.sleepMin?.takeIf { it > 0 }?.let { if (cfg.sleepTargetMin > 0) (it.toFloat() / cfg.sleepTargetMin).coerceIn(0f, 1f) else null }, Modifier.weight(1f)) { openShealth("sleep") }
                            else -> MiniStat("Heart rate", s.health?.restingHr?.let { "$it" } ?: "-", "resting bpm", null, Modifier.weight(1f)) { openShealth("heartrate") }
                        }
                    }
                    repeat(3 - small.size) { Spacer(Modifier.weight(1f)) }
                }
                "calories" -> EnergyCard(s, false)
                "steps" -> StepsCard(s) { openShealth("pedometer") }
                "sleep" -> SleepCard(s) { openShealth("sleep") }
                "rhr" -> RhrCard(s) { openShealth("heartrate") }
                "events" -> Section("Upcoming events", "Open", { openCalendar() }) { EventsPanel(s) }
                "countdowns" -> CountdownRow(s)
                "feed" -> FeedSection(s)
                "weather" -> WeatherCard(s)
            }
        }
    }

    @Composable
    private fun LifeTab(s: Snapshot, sub: String) {
        val tc = LocalTh.current.tab[1]
        if (sub.isNotEmpty()) { Links.LIFE_PAGES.firstOrNull { it.first == sub }?.let { SubPageView(tc, it.first, it.second, s) }; return }
        Rearrange("life", listOf("progress", "admin")) { k ->
            when (k) {
                "progress" -> Section("Progress") { Panel { LifeBars(tc) } }
                else -> Section("Life admin", "Open", { open(Links.LIFE) }) {
                    TasksPanel(s, tasksIn(s, "Flat", "Money", "Life admin"), "Nothing on for flat, money or admin", "area")
                }
            }
        }
    }

    @Composable
    private fun HealthTab(s: Snapshot, sub: String) {
        val tc = LocalTh.current.tab[2]
        if (sub.isNotEmpty()) { Links.HEALTH_PAGES.firstOrNull { it.first == sub }?.let { SubPageView(tc, it.first, it.second, s) }; return }
        Rearrange("health", HEALTH_CARDS + listOf("tasks"), cfg.healthHidden) { key ->
            when (key) {
                "steps" -> StepsCard(s) { openShealth("pedometer") }
                "sleep" -> SleepCard(s) { openShealth("sleep") }
                "rhr" -> RhrCard(s) { openShealth("heartrate") }
                "weight" -> WeightCard(s)
                "nutrition" -> EnergyCard(s, true)
                "activity" -> ActivityTiles(s)
                "weekly" -> WeeklyCard(s)
                else -> Section("Health tasks") { TasksPanel(s, tasksIn(s, "Health"), "No health tasks") }
            }
        }
    }

    @Composable
    private fun CareerTab(s: Snapshot, sub: String) {
        val tc = LocalTh.current.tab[3]
        if (sub.isNotEmpty()) { Links.CAREER_PAGES.firstOrNull { it.first == sub }?.let { SubPageView(tc, it.first, it.second, s) }; return }
        Rearrange("career", listOf("cima", "study")) { k ->
            when (k) {
                "cima" -> Section("CIMA") {
                    val cima = s.countdowns?.filter { it.name.contains("CIMA", true) || it.name.contains("E2", true) }
                    if (cima.isNullOrEmpty()) Panel { Muted("No CIMA date in Countdowns yet") }
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        cima.take(2).forEach { c -> Tile(c.name, c.days.toString(), "days to go", tc, Modifier.weight(1f)) { openCountdown(c) } }
                        if (cima.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                else -> Section("Study and work") { TasksPanel(s, tasksIn(s, "CIMA", "Work"), "No CIMA or work tasks", "area") }
            }
        }
    }

    @Composable
    private fun CreateTab(s: Snapshot, sub: String) {
        val th = LocalTh.current
        val tc = th.tab[4]
        if (sub.isNotEmpty()) { Links.CREATE_PAGES.firstOrNull { it.first == sub }?.let { SubPageView(tc, it.first, it.second, s) }; return }
        val f = s.feed
        val yt = f?.optJSONObject("youtube")
        val ig = f?.optJSONObject("instagram")
        val ytHandle = yt?.optString("handle")?.takeIf { it.isNotEmpty() } ?: "@woozypictures"
        val igHandle = ig?.optString("handle")?.takeIf { it.isNotEmpty() } ?: "woozypictures"
        Rearrange("create", listOf("youtube", "instagram", "content")) { k ->
            when (k) {
                "youtube" -> Section("YouTube" + (yt?.optString("channel")?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")) {
                    if (yt == null) Panel { Muted("Stats arrive with the morning refresh") }
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val go = { openApp("com.google.android.youtube", "https://www.youtube.com/$ytHandle") }
                        Tile("Subscribers", nf.format(yt.optLong("subs")), if (yt.optLong("subs30") != 0L) "${yt.optLong("subs30")} in 30d" else null, tc, Modifier.weight(1f), go)
                        Tile("Views", nf.format(yt.optLong("views")), "+${nf.format(yt.optLong("views30"))} in 30d", tc, Modifier.weight(1f), go)
                        Tile("Videos", nf.format(yt.optLong("videos")), null, tc, Modifier.weight(1f), go)
                    }
                }
                "instagram" -> Section("Instagram" + (ig?.optString("handle")?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")) {
                    if (ig == null) Panel { Muted("Instagram stats arrive with the morning refresh") }
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val go = { openApp("com.instagram.android", "https://www.instagram.com/$igHandle") }
                        Tile("Followers", nf.format(ig.optLong("followers")), null, tc, Modifier.weight(1f), go)
                        Tile("Reach", if (ig.has("reach30")) nf.format(ig.optLong("reach30")) else "-", "30 days", tc, Modifier.weight(1f), go)
                        Tile("Posts", if (ig.has("posts")) nf.format(ig.optLong("posts")) else "-", null, tc, Modifier.weight(1f), go)
                    }
                }
                else -> Section("Content and music") { TasksPanel(s, tasksIn(s, "Content", "Music"), "No content tasks", "area") }
            }
        }
    }

    // ---------- home cards ----------

    @Composable
    private fun CountdownRow(s: Snapshot) {
        val tc = LocalTh.current.tab[0]
        val cds = s.countdowns
        if (cds.isNullOrEmpty()) return
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cds.take(3).forEach { c -> Tile(c.name, c.days.toString(), if (c.days == 1L) "day" else "days", tc, Modifier.weight(1f)) { openCountdown(c) } }
            repeat(3 - minOf(3, cds.size)) { Spacer(Modifier.weight(1f)) }
        }
    }

    @Composable
    private fun EventsPanel(s: Snapshot) {
        val th = LocalTh.current
        var range by rememberSaveable { mutableIntStateOf(0) }
        val z = Cfg.zone
        val today = LocalDate.now(z)
        val firstDay = if (cfg.weekStart == "SUN") DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        val weekEnd = today.with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "This week", "This month").forEachIndexed { i, label ->
                Box(Modifier.clip(RoundedCornerShape(50))
                    .background(if (range == i) (if (th.glass) Color(0x55FFFFFF) else th.tab[0]) else if (th.glass) Color(0x1FFFFFFF) else lerp(th.panel, th.fg, 0.07f))
                    .clickable { range = i }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    T(label, 12.sp, if (range == i && !th.glass) th.onTab else th.fg, weight = FontWeight.SemiBold)
                }
            }
        }
        val ev = s.events
        val list = ev?.filter { e ->
            val d = Instant.ofEpochMilli(e.start).atZone(z).toLocalDate()
            when (range) {
                0 -> d == today || (e.allDay && d.isBefore(today))
                1 -> e.allDay && !d.isAfter(weekEnd)
                else -> e.allDay && !d.isAfter(monthEnd)
            }
        }
        Panel {
            when {
                ev == null -> Muted(if (CalendarSrc.granted(this@MainActivity)) "Calendar hasn't loaded yet" else "Allow calendar access in Settings > Connections")
                list.isNullOrEmpty() -> Muted(when (range) { 0 -> "Nothing else today"; 1 -> "No all-day events this week"; else -> "No all-day events this month" })
                else -> list.take(12).forEach { EventRow(it, range > 0) }
            }
        }
        if (range > 0) T("Week and month show all-day events only.", 11.sp, th.muted)
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
                if (yt != null) Column(Modifier.clickable { openApp("com.google.android.youtube", "https://www.youtube.com/" + (yt.optString("handle").ifEmpty { "@woozypictures" })) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
            Column(Modifier.fillMaxWidth().card(18.dp).clickable { openWeather() }.padding(vertical = 4.dp)) {
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
    private val C_ENERGY = Color(0xFFF2A488)
    private val C_PROTEIN = Color(0xFF7BC99A)
    private val C_CARBS = Color(0xFF62C4D1)
    private val C_FAT = Color(0xFFBE98E6)

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
    private fun MiniStat(title: String, value: String, sub: String?, pct: Float?, modifier: Modifier, onClick: () -> Unit) {
        val th = LocalTh.current
        Column(modifier.heightIn(min = 96.dp).card(16.dp).clickable(onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            T(title, 11.sp, th.muted, weight = FontWeight.SemiBold, caps = true)
            Column {
                T(value, 22.sp, th.fg, weight = FontWeight.Bold, maxLines = 1)
                if (sub != null) T(sub, 11.sp, th.muted, maxLines = 1)
                if (pct != null) { Spacer(Modifier.height(6.dp)); Segments(pct, th.tab[2], 6.dp) }
            }
        }
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
        HCard("Weight", { openShealth("weight") }) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            T(h.weightKg?.let { Fmt.weight(it) } ?: "-", 30.sp, th.fg, weight = FontWeight.Bold)
            if (cfg.weighInsPerWeek > 0 && x != null) T("Weigh-ins this week: ${x.weighIns7} of ${cfg.weighInsPerWeek}", 12.sp, th.muted)
            Spacer(Modifier.height(6.dp))
            Fresh(x?.weightAt)
        }
    }

    @Composable
    private fun Donut(pct: Float, color: Color, value: String, label: String) {
        val th = LocalTh.current
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = size.minDimension * 0.13f
                    val tl = Offset(sw / 2, sw / 2)
                    val sz = Size(size.width - sw, size.height - sw)
                    drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(sw))
                    if (pct > 0f) drawArc(color, -90f, 360f * pct.coerceIn(0f, 1f), false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(value, 16.sp, th.fg, weight = FontWeight.Bold, maxLines = 1)
                    T("kcal", 10.sp, th.muted)
                }
            }
            T(label, 12.sp, th.muted, weight = FontWeight.Medium)
        }
    }

    @Composable
    private fun MacroBar(label: String, value: Double?, target: Int, unit: String, color: Color) {
        val th = LocalTh.current
        val has = value != null && value > 0
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth()) {
                T(label, 13.sp, th.fg, Modifier.weight(1f), weight = FontWeight.SemiBold)
                T(if (has) nf.format(value!!.toLong()) + (if (target > 0) " / ${nf.format(target)}" else "") + " $unit" else "- " + (if (target > 0) "/ ${nf.format(target)} $unit" else ""), 13.sp, th.muted)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.28f))) {
                if (has && target > 0) Box(Modifier.fillMaxWidth((value!! / target).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
            }
        }
    }

    /** Cronometer-style energy summary and macro targets. Cronometer writes these totals to Health Connect. */
    @Composable
    private fun EnergyCard(s: Snapshot, full: Boolean) {
        val th = LocalTh.current
        val h = s.health
        val x = s.healthX
        HCard(if (full) "Nutrition today" else "Calories", { openApp("com.cronometer.android", "https://cronometer.com") }) {
            if (h == null) { T(healthMissing(), 14.sp, th.muted); return@HCard }
            val eaten = h.eatenKcal?.takeIf { it > 0 }
            val burned = (h.totalKcal ?: h.activeKcal)?.takeIf { it > 0 }
            val target = cfg.caloriesTarget
            val left = if (target > 0 && eaten != null) target - eaten else if (target > 0) target.toDouble() else null
            if (full) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Donut(if (target > 0 && eaten != null) (eaten / target).toFloat() else 0f, C_ENERGY, eaten?.let { nf.format(it.toLong()) } ?: "-", "Consumed")
                    Donut(if (burned != null) 1f else 0f, C_FAT, burned?.let { nf.format(it.toLong()) } ?: "-", "Expenditure")
                    Donut(if (left != null && target > 0) (left / target).toFloat().coerceIn(0f, 1f) else 0f, C_CARBS, left?.let { nf.format(it.toLong()) } ?: "-", "Remaining")
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    T(eaten?.let { nf.format(it.toLong()) } ?: "-", 30.sp, th.fg, weight = FontWeight.Bold)
                    if (target > 0) T(" / ${nf.format(target)} kcal", 14.sp, th.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (left != null && eaten != null) T((if (left >= 0) nf.format(left.toLong()) + " left" else nf.format((-left).toLong()) + " over"), 12.sp, th.muted)
                Spacer(Modifier.height(6.dp))
            }
            MacroBar("Energy", eaten, cfg.caloriesTarget, "kcal", C_ENERGY)
            MacroBar("Protein", x?.proteinG, cfg.proteinTarget, "g", C_PROTEIN)
            val net = if (x?.carbsG != null) (x.carbsG - (x.fibreG ?: 0.0)).coerceAtLeast(0.0) else null
            MacroBar("Net carbs", net, cfg.carbsTarget, "g", C_CARBS)
            MacroBar("Fat", x?.fatG, cfg.fatTarget, "g", C_FAT)
            if (full && cfg.fibreTarget > 0) MacroBar("Fibre", x?.fibreG, cfg.fibreTarget, "g", C_PROTEIN)
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
    private fun StepsBars(values: List<Long?>, target: Int, color: Color) {
        val th = LocalTh.current
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val maxV = maxOf((values.filterNotNull().maxOrNull() ?: 0L).toFloat(), target.toFloat(), 1f)
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.62f
            values.forEachIndexed { i, v ->
                if (v != null && v > 0) {
                    val bh = size.height * (v / maxV)
                    drawRoundRect(if (target > 0 && v >= target) color else color.copy(alpha = 0.45f), Offset(i * slot + (slot - bw) / 2, size.height - bh), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 4))
                }
            }
            if (target > 0) {
                val y = size.height * (1f - target / maxV)
                drawLine(th.muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
    }

    @Composable
    private fun WeeklyCard(s: Snapshot) {
        val th = LocalTh.current
        var span by rememberSaveable { mutableIntStateOf(7) }
        val hist = s.history
        HCard(if (span == 7) "Weekly overview" else "Monthly overview", null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "7 days", 30 to "30 days").forEach { (n, label) ->
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (span == n) th.tab[2].copy(alpha = if (th.glass) 0.35f else 1f) else Color(0x22888888))
                        .clickable { span = n }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        T(label, 12.sp, if (span == n && !th.glass) th.onTab else th.fg, weight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (hist == null || hist.days.isEmpty()) { T("History appears after the first successful health refresh.", 13.sp, th.muted); return@HCard }
            val todayStr = LocalDate.now(Cfg.zone).toString()
            val all = hist.days.filter { it.date != todayStr }
            val win = all.takeLast(span)
            val prev = if (span == 7) all.dropLast(span).takeLast(span) else emptyList()
            T("Steps per day" + if (cfg.stepsTarget > 0) " (dashed line = target)" else "", 11.sp, th.muted)
            StepsBars(win.map { it.steps }, cfg.stepsTarget, th.tab[2])
            Spacer(Modifier.height(6.dp))
            @Composable fun row(label: String, f: (DayStat) -> Double?, fmt: (Double) -> String, dfmt: (Double) -> String) {
                val v = win.mapNotNull(f)
                val pv = prev.mapNotNull(f)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.weight(1f)) {
                        T(label, 13.sp, th.muted)
                        if (v.isNotEmpty() && v.size < span) T("based on ${v.size} of $span days", 10.sp, th.amber)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        T(if (v.isEmpty()) "-" else fmt(v.average()), 15.sp, th.fg, weight = FontWeight.Medium)
                        if (v.isNotEmpty() && pv.size >= 3) T(dfmt(v.average() - pv.average()) + " vs last week", 10.sp, th.muted)
                    }
                }
            }
            fun sg(d: Double) = if (d >= 0) "+" else "-"
            row("Average steps", { it.steps?.toDouble() }, { nf.format(it.toLong()) }, { sg(it) + nf.format(kotlin.math.abs(it).toLong()) })
            row("Average sleep", { it.sleepMin?.toDouble() }, { Fmt.duration(it.toLong()) }, { sg(it) + kotlin.math.abs(it).toLong() + " min" })
            row("Average resting HR", { it.rhr?.toDouble() }, { "${it.toInt()} bpm" }, { sg(it) + kotlin.math.abs(it).toInt() + " bpm" })
            row("Average calories", { it.kcal }, { nf.format(it.toLong()) + " kcal" }, { sg(it) + nf.format(kotlin.math.abs(it).toLong()) })
            row("Average protein", { it.protein }, { nf.format(it.toLong()) + " g" }, { sg(it) + kotlin.math.abs(it).toLong() + " g" })
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
            T("Completed days only. Missing days are left out, not counted as zero.", 10.sp, th.muted)
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
    private fun TasksPanel(s: Snapshot, list: List<Task>?, empty: String = "Nothing to do. Nice.", groupBy: String? = null) {
        val th = LocalTh.current
        @Composable fun item(t: Task) {
            Column(Modifier.fillMaxWidth().clickable { open(t.url) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                T(t.title, 15.sp, th.fg, weight = FontWeight.Medium, maxLines = 2)
                val meta = listOf(if (groupBy == "area") null else t.area, if (groupBy == "lane") null else t.lane, t.due.take(10).takeIf { it.isNotEmpty() }?.let { "due " + fmtDate(it) })
                    .filter { !it.isNullOrBlank() }.joinToString(" · ")
                if (meta.isNotEmpty()) T(meta, 11.sp, th.muted, caps = true)
            }
        }
        Panel {
            when {
                list == null -> {
                    val st = diag["Notion tasks"]
                    Muted(when {
                        Store.token(this@MainActivity).isEmpty() -> "Add your Notion token in Settings > Connections"
                        st != null && !st.ok -> st.lastError
                        else -> "Tasks haven't loaded yet. Pull down to refresh."
                    })
                }
                list.isEmpty() -> Muted(empty)
                groupBy != null -> {
                    val groups = list.groupBy { (if (groupBy == "area") it.area else it.lane).ifBlank { "Other" } }
                    groups.entries.forEach { (g, items) ->
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) { T(g, 11.sp, th.muted, weight = FontWeight.Bold, caps = true) }
                        items.take(6).forEach { item(it) }
                    }
                }
                else -> list.take(8).forEach { item(it) }
            }
        }
    }

    @Composable
    private fun EventRow(e: Event, withDate: Boolean = false) {
        val th = LocalTh.current
        val z = Cfg.zone
        val start = Instant.ofEpochMilli(e.start).atZone(z)
        val today = LocalDate.now(z)
        val day = when (start.toLocalDate()) {
            today -> ""
            today.plusDays(1) -> "Tmrw "
            else -> start.format(DateTimeFormatter.ofPattern("EEE ", UK))
        }
        val time = if (withDate) Fmt.dayMonth(start.toLocalDate()) + " · " + start.format(DateTimeFormatter.ofPattern("EEE", UK))
            else if (e.allDay) day.ifEmpty { "Today " } + "all day" else day + Fmt.time(start)
        Row(Modifier.fillMaxWidth().clickable { openEvent(e.id) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(time.trim(), 12.sp, th.muted, modifier = Modifier.width(if (withDate) 110.dp else 96.dp), maxLines = 1, weight = FontWeight.Medium, caps = true)
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
