package uk.woozy.hq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor as SC
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val HANDLE: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(pathData = addPathNodes("M3,15h18v-2L3,13v2zM3,19h18v-2L3,17v2zM3,11h18L21,9L3,9v2zM3,5v2h18L21,5L3,5z"), fill = SC(Color.Black)).build()

internal val BACK_ARROW: ImageVector = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(pathData = addPathNodes("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"), fill = SC(Color.Black)).build()

class SettingsActions(
    val saveToken: (String) -> Unit,
    val saveDob: (String) -> Unit,
    val allowAccess: () -> Unit,
    val refreshNow: () -> Unit,
    val manageHealth: () -> Unit,
    val exportPrefs: () -> Unit,
    val importPrefs: () -> Unit,
    val clearCache: () -> Unit,
    val openRepo: () -> Unit,
    val openUrl: (String) -> Unit = {},
    val name: () -> String = { "Mahesh" },
    val saveName: (String) -> Unit = {},
    val height: () -> String = { "" },
    val saveHeight: (String) -> Unit = {},
    val openReleases: () -> Unit,
    val licenseText: () -> String,
)

private val ZONES = listOf("Europe/London", "Europe/Dublin", "Europe/Paris", "America/New_York", "America/Los_Angeles", "Asia/Kolkata", "Australia/Sydney", "device")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    s: Settings, onChange: (Settings) -> Unit, tokenSaved: Boolean, dob: String,
    snap: Snapshot, diag: Map<String, SourceState>, errlog: List<ErrEntry>, start: String,
    versionText: String, actions: SettingsActions, onBack: () -> Unit = {},
) {
    val th = LocalTh.current
    var open by rememberSaveable { mutableStateOf(start) }
    androidx.activity.compose.BackHandler(open.isNotEmpty()) { open = "" }
    var showLicence by rememberSaveable { mutableStateOf(false) }

    @Composable fun Chip(label: String, on: Boolean, click: () -> Unit) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (on) (if (th.glass) Color(0x55FFFFFF) else th.tab[0]) else if (th.glass) Color(0x1FFFFFFF) else lerp(th.panel, th.fg, 0.07f))
                .clickable(onClick = click).padding(horizontal = 14.dp, vertical = 8.dp),
        ) { T(label, 13.sp, if (on && !th.glass) Color.White else th.fg, weight = FontWeight.SemiBold) }
    }

    @Composable fun Opts(title: String, options: List<Pair<String, String>>, cur: String, pick: (String) -> Unit) {
        Column(Modifier.padding(vertical = 6.dp)) {
            T(title, 13.sp, th.muted, weight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (v, l) -> Chip(l, v == cur) { pick(v) } }
            }
        }
    }

    @Composable fun Toggle(title: String, note: String?, on: Boolean, change: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                T(title, 15.sp, th.fg, weight = FontWeight.Medium)
                if (note != null) T(note, 12.sp, th.muted)
            }
            Switch(
                checked = on, onCheckedChange = change,
                colors = SwitchDefaults.colors(checkedTrackColor = if (th.glass) Color(0x99FFFFFF) else th.tab[0], checkedThumbColor = if (th.glass) Color(0xFF2E2E2E) else Color.White),
            )
        }
    }

    @Composable fun NumField(title: String, value: Int, unit: String, note: String? = null, save: (Int) -> Unit) {
        var txt by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                T(title, 15.sp, th.fg, weight = FontWeight.Medium)
                T(note ?: if (value > 0) "" else "Not set", 12.sp, th.muted)
            }
            BasicTextField(
                value = txt, onValueChange = { n -> val d = n.filter { it.isDigit() }.take(6); txt = d; save(d.toIntOrNull() ?: 0) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontFamily = GSans, fontSize = 16.sp, color = th.fg, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(th.fg),
                modifier = Modifier.width(88.dp).border(1.dp, th.hair, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            )
            T(" $unit", 13.sp, th.muted, Modifier.width(40.dp))
        }
    }

    @Composable fun TextField(title: String, value: String, hint: String, secret: Boolean = false, onSave: (String) -> Unit) {
        var txt by remember(value) { mutableStateOf(value) }
        Column(Modifier.padding(vertical = 6.dp)) {
            T(title, 13.sp, th.muted, weight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = txt, onValueChange = { txt = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = GSans, fontSize = 15.sp, color = th.fg),
                cursorBrush = SolidColor(th.fg),
                decorationBox = { inner -> Box { if (txt.isEmpty()) T(hint, 15.sp, th.muted); inner() } },
                modifier = Modifier.fillMaxWidth().border(1.dp, th.hair, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(6.dp))
            Chip("Save", false) { onSave(txt); if (secret) txt = "" }
        }
    }


    // Like Woozy Fit: a list of rows, each opening its own page. Only the open page draws its content.
    @Composable fun Section(key: String, title: String, content: @Composable () -> Unit) {
        if (open.isEmpty()) {
            Row(Modifier.fillMaxWidth().card(18.dp).clickable { open = key }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, 16.sp, th.fg, Modifier.weight(1f), weight = FontWeight.SemiBold)
                T("\u203A", 22.sp, th.muted)
            }
        } else if (open == key) {
            Column(Modifier.fillMaxWidth().card(18.dp).padding(16.dp)) { content() }
        }
    }

    @Composable fun Btn(label: String, click: () -> Unit) {
        Box(Modifier.padding(vertical = 4.dp)) { Chip(label, false, click) }
    }


    @Composable fun SchemePicker(cur: String, pick: (String) -> Unit) {
        Column(Modifier.padding(vertical = 6.dp)) {
            T("Colour scheme", 13.sp, th.muted, weight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                THEME_NAMES.forEach { (id, name) ->
                    val t = themeFor(id)
                    val on = id == cur
                    Column(Modifier.width(88.dp).clickable { pick(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(
                            Modifier.width(88.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(t.bg)
                                .border(if (on) 2.dp else 1.dp, if (on) th.fg else th.hair, RoundedCornerShape(12.dp)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(5.dp)).background(t.tab[0]))
                            Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp)).background(t.panel).border(1.dp, t.hair, RoundedCornerShape(5.dp)))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                t.tab.take(5).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(it)) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        T(name, 11.sp, if (on) th.fg else th.muted, weight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, center = true)
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(th.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = { if (open.isNotEmpty()) open = "" else onBack() }), contentAlignment = Alignment.Center) {
                Icon(BACK_ARROW, contentDescription = "Back", tint = th.fg, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(8.dp))
            T(PAGE_TITLES[open] ?: "Settings", 26.sp, th.fg, Modifier.weight(1f), weight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("general", "Display") {
                Opts("Appearance", listOf("system" to "Match phone", "light" to "Light", "dark" to "Dark"), s.appearance) { onChange(s.copy(appearance = it)) }
                SchemePicker(s.theme) { onChange(s.copy(theme = it)) }
                Opts("Temperature", listOf("C" to "Celsius", "F" to "Fahrenheit"), s.tempUnit) { onChange(s.copy(tempUnit = it)) }
                Opts("Weight", listOf("kg" to "kg", "lb" to "lb", "st" to "st + lb"), s.weightUnit) { onChange(s.copy(weightUnit = it)) }
                Opts("Distance (saved for later)", listOf("km" to "km", "mi" to "miles"), s.distanceUnit) { onChange(s.copy(distanceUnit = it)) }
                Opts("Currency (saved for later)", listOf("GBP" to "GBP", "USD" to "USD", "EUR" to "EUR"), s.currency) { onChange(s.copy(currency = it)) }
                Opts("Date format", listOf("DD/MM/YYYY" to "DD/MM/YYYY", "MM/DD/YYYY" to "MM/DD/YYYY", "YYYY-MM-DD" to "YYYY-MM-DD", "D MMM YYYY" to "D MMM YYYY"), s.dateFormat) { onChange(s.copy(dateFormat = it)) }
                Opts("Time", listOf("24" to "24-hour", "12" to "12-hour"), if (s.time24) "24" else "12") { onChange(s.copy(time24 = it == "24")) }
                Opts("Week starts", listOf("MON" to "Monday", "SUN" to "Sunday"), s.weekStart) { onChange(s.copy(weekStart = it)) }
                Opts("Timezone", ZONES.map { it to (if (it == "device") "Device" else it.substringAfter('/').replace('_', ' ')) }, s.timezone) { onChange(s.copy(timezone = it)) }
            }


            Section("profile", "You and targets") {
                T("Your picture is the icon of the DASHBOARD page in Notion. Change it there and it updates here and in Woozy Fit on the next refresh.", 13.sp, th.muted)
                TextField("Name", actions.name(), "Name") { actions.saveName(it) }
                Spacer(Modifier.height(8.dp))
                T("Targets", 13.sp, th.muted, weight = FontWeight.Medium)
                if (snap.fit != null) T("Calories, protein, carbs, fat and water come from Woozy Fit. Open Woozy Fit > Settings > Targets to change them.", 12.sp, th.muted)
                NumField("Daily steps", s.stepsTarget, "steps") { onChange(s.copy(stepsTarget = it)) }
                NumField("Sleep", s.sleepTargetMin / 60, "hours", "Whole hours") { onChange(s.copy(sleepTargetMin = it * 60)) }
                if (snap.fit == null) NumField("Calories", s.caloriesTarget, "kcal") { onChange(s.copy(caloriesTarget = it)) }
                if (snap.fit == null) NumField("Protein", s.proteinTarget, "g") { onChange(s.copy(proteinTarget = it)) }
                if (snap.fit == null) NumField("Net carbs", s.carbsTarget, "g") { onChange(s.copy(carbsTarget = it)) }
                if (snap.fit == null) NumField("Fat", s.fatTarget, "g") { onChange(s.copy(fatTarget = it)) }
                if (snap.fit == null) NumField("Fibre", s.fibreTarget, "g") { onChange(s.copy(fibreTarget = it)) }
                if (snap.fit == null) NumField("Water", s.waterTargetMl, "ml", "Saved only. HQ doesn't read water yet") { onChange(s.copy(waterTargetMl = it)) }
                NumField("Weigh-ins per week", s.weighInsPerWeek, "") { onChange(s.copy(weighInsPerWeek = it)) }
                Spacer(Modifier.height(8.dp))
                T("Health cards to show", 13.sp, th.muted, weight = FontWeight.Medium)
                HEALTH_CARDS.forEach { k ->
                    Toggle(HEALTH_NAMES[k] ?: k, null, k !in s.healthHidden) { on ->
                        onChange(s.copy(healthHidden = if (on) s.healthHidden - k else s.healthHidden + k))
                    }
                }
            }

            Section("dashboard", "Home cards") {
                val keys = s.homeOrder.filter { it !in listOf("steps", "sleep", "rhr") }
                val cur by rememberUpdatedState(s)
                val change by rememberUpdatedState(onChange)
                var dragKey by remember { mutableStateOf<String?>(null) }
                var off by remember { mutableStateOf(0f) }
                val rowH = with(LocalDensity.current) { 56.dp.toPx() }
                keys.forEach { k ->
                    val dragging = dragKey == k
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).graphicsLayer { if (dragging) { translationY = off; shadowElevation = 16f } }.zIndex(if (dragging) 1f else 0f)
                            .background(if (dragging) th.panel else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HANDLE, contentDescription = "Drag", tint = th.muted,
                            modifier = Modifier.size(40.dp).padding(8.dp).pointerInput(k) {
                                detectDragGestures(
                                    onDragStart = { dragKey = k; off = 0f },
                                    onDragEnd = { dragKey = null; off = 0f },
                                    onDragCancel = { dragKey = null; off = 0f },
                                ) { ch, d ->
                                    ch.consume()
                                    off += d.y
                                    val order = cur.homeOrder
                                    val vis = order.filter { it !in listOf("steps", "sleep", "rhr") }
                                    val i = vis.indexOf(k)
                                    val other = when { off > rowH / 2 && i < vis.lastIndex -> vis[i + 1]; off < -rowH / 2 && i > 0 -> vis[i - 1]; else -> null }
                                    if (other != null) {
                                        val n = order.toMutableList()
                                        n.removeAt(n.indexOf(k)); n.add(n.indexOf(other).let { if (off > 0) it + 1 else it }, k)
                                        change(cur.copy(homeOrder = n))
                                        off += if (off > 0) -rowH else rowH
                                    }
                                }
                            })
                        T(HOME_NAMES[k] ?: k, 15.sp, th.fg, Modifier.weight(1f).padding(start = 4.dp), weight = FontWeight.Medium)
                        Switch(
                            checked = k !in s.homeHidden, onCheckedChange = { on -> onChange(s.copy(homeHidden = if (on) s.homeHidden - k else s.homeHidden + k)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = if (th.glass) Color(0x99FFFFFF) else th.tab[0], checkedThumbColor = if (th.glass) Color(0xFF2E2E2E) else Color.White),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                T("Steps, sleep and heart rate", 13.sp, th.muted, weight = FontWeight.Medium)
                T("Small cards share one row. Switch one to wide for a full-width card (you can also do this from Home by long-pressing).", 12.sp, th.muted)
                listOf("steps" to "Steps", "sleep" to "Sleep", "rhr" to "Heart rate").forEach { (k, name) ->
                    Toggle("Show $name", null, k !in s.homeHidden) { on -> onChange(s.copy(homeHidden = if (on) s.homeHidden - k else s.homeHidden + k)) }
                    Toggle("$name as wide card", null, k in s.homeWide) { on -> onChange(s.copy(homeWide = if (on) s.homeWide + k else s.homeWide - k)) }
                }
                Btn("Quick glance layout") { onChange(s.copy(homeOrder = GLANCE_ORDER + HOME_CARDS.filter { it !in GLANCE_ORDER }, homeHidden = HOME_CARDS.toSet() - GLANCE_ORDER.toSet(), homeWide = emptySet())) }
                Btn("Reset order") { onChange(s.copy(homeOrder = HOME_CARDS, homeHidden = emptySet(), homeWide = emptySet())) }
            }


            Section("connections", "Data and connections") {
                T(if (tokenSaved) "Notion token saved" else "No Notion token yet", 14.sp, if (tokenSaved) th.ok else th.warn, weight = FontWeight.SemiBold)
                TextField(if (tokenSaved) "Replace Notion token" else "Notion token", "", "Paste token (ntn_...)", secret = true) { if (it.isNotBlank()) actions.saveToken(it) }
                TextField("Birthday (for the Life bar)", dob, "YYYY-MM-DD") { actions.saveDob(it) }
                Spacer(Modifier.height(4.dp))
                Btn("Allow calendar and Health Connect") { actions.allowAccess() }
                Spacer(Modifier.height(10.dp))
                T("Refresh", 13.sp, th.muted, weight = FontWeight.Medium)
                Toggle("Refresh in the background", "Android runs this when it chooses, so timings are approximate.", s.autoRefresh) { onChange(s.copy(autoRefresh = it)) }
                Opts("Every", INTERVALS.map { it to (if (it < 60) "$it min" else "${it / 60} h") }.map { it.first.toString() to it.second }, s.intervalMin.toString()) {
                    onChange(s.copy(intervalMin = it.toInt()))
                }
                Toggle("Refresh when HQ opens", null, s.refreshOnLaunch) { onChange(s.copy(refreshOnLaunch = it)) }
                Toggle("Keep last good data if a source fails", null, s.retainData) { onChange(s.copy(retainData = it)) }
                Toggle("Show source problems on Home", null, s.showErrors) { onChange(s.copy(showErrors = it)) }
                Spacer(Modifier.height(6.dp))
                Btn("Refresh now") { actions.refreshNow() }
            }

            Section("notifications", "Notifications") {
                Toggle("Overdue tasks", null, s.nOverdue) { onChange(s.copy(nOverdue = it)) }
                Toggle("Upcoming countdowns", "Within 3 days", s.nCountdowns) { onChange(s.copy(nCountdowns = it)) }
                Toggle("Failed refreshes", null, s.nFailed) { onChange(s.copy(nFailed = it)) }
                Toggle("Source connection problems", null, s.nSources) { onChange(s.copy(nSources = it)) }
            }


            Section("advanced", "Advanced") {
                T("Version $versionText", 14.sp, th.fg, weight = FontWeight.Medium)
                T(if (snap.updated > 0) "Last refresh " + Fmt.clock(snap.updated) else "Not refreshed yet", 12.sp, th.muted)
                Spacer(Modifier.height(8.dp))
                T("Sources", 13.sp, th.muted, weight = FontWeight.Medium)
                SOURCES.forEach { n ->
                    val st = diag[n]
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            T(n, 14.sp, th.fg, Modifier.weight(1f), weight = FontWeight.Medium)
                            T(when { st == null -> "No data"; st.ok -> "OK"; st.cached -> "Failed, showing saved"; else -> "Failed" }, 13.sp, if (st == null) th.muted else if (st.ok) th.ok else th.warn)
                        }
                        if (st != null) {
                            T("Tried " + (if (st.lastAttempt > 0) Fmt.clock(st.lastAttempt) else "-") + " · worked " + (if (st.lastSuccess > 0) Fmt.clock(st.lastSuccess) else "never"), 11.sp, th.muted)
                            if (!st.ok) T(st.lastError, 11.sp, th.warn)
                        }
                    }
                }
                if (errlog.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    T("Recent errors", 13.sp, th.muted, weight = FontWeight.Medium)
                    errlog.take(10).forEach { T(Fmt.clock(it.t) + " · " + it.source + ": " + it.msg, 11.sp, th.muted) }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Manage Health Connect") { actions.manageHealth() }
                    Btn("Export preferences") { actions.exportPrefs() }
                    Btn("Import from clipboard") { actions.importPrefs() }
                    Btn("Clear cached data") { actions.clearCache() }
                    Btn("Open GitHub repo") { actions.openRepo() }
                    Btn("Releases / updates") { actions.openReleases() }
                    Btn(if (showLicence) "Hide font licence" else "Font licence") { showLicence = !showLicence }
                }
                if (showLicence) {
                    Spacer(Modifier.height(8.dp))
                    T("Google Sans, SIL Open Font License 1.1", 13.sp, th.fg, weight = FontWeight.SemiBold)
                    T(actions.licenseText(), 10.sp, th.muted)
                }
            }
            // About shows on the list page: who made it, the version, and the socials.
            if (open.isEmpty()) Column(Modifier.fillMaxWidth().card(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("About", 16.sp, th.fg, weight = FontWeight.Bold)
                T("Created by Woozypictures", 14.sp, th.fg)
                T("App version: " + versionText.substringBefore(" ("), 14.sp, th.muted)
                SOCIALS.forEach { url ->
                    T(url, 14.sp, th.tab[0], Modifier.fillMaxWidth().clickable { actions.openUrl(url) }.padding(vertical = 6.dp), weight = FontWeight.Medium)
                }
            }
        }
    }
}

val PAGE_TITLES = mapOf("general" to "Display", "profile" to "You and targets", "dashboard" to "Home cards", "connections" to "Data and connections", "notifications" to "Notifications", "advanced" to "Advanced")

/** Woozypictures socials, shown in About in every app. */
val SOCIALS = listOf(
    "https://www.instagram.com/woozypictures/",
    "https://www.youtube.com/@WoozyPictures",
    "https://x.com/WoozyPictures",
)
