package uk.woozy.hq

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
    val openReleases: () -> Unit,
    val licenseText: () -> String,
)

private val ZONES = listOf("Europe/London", "Europe/Dublin", "Europe/Paris", "America/New_York", "America/Los_Angeles", "Asia/Kolkata", "Australia/Sydney", "device")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    s: Settings, onChange: (Settings) -> Unit, tokenSaved: Boolean, dob: String,
    snap: Snapshot, diag: Map<String, SourceState>, errlog: List<ErrEntry>, start: String,
    versionText: String, actions: SettingsActions,
) {
    val th = LocalTh.current
    var open by rememberSaveable { mutableStateOf(start.ifEmpty { "general" }) }
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

    @Composable fun Section(key: String, title: String, content: @Composable () -> Unit) {
        val isOpen = open == key
        Column(Modifier.fillMaxWidth().card(18.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = if (isOpen) "" else key }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, 16.sp, th.fg, Modifier.weight(1f), weight = FontWeight.Bold)
                T(if (isOpen) "Hide" else "Show", 12.sp, th.muted, caps = true)
            }
            if (isOpen) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { content() }
        }
    }

    @Composable fun Btn(label: String, click: () -> Unit) {
        Box(Modifier.padding(vertical = 4.dp)) { Chip(label, false, click) }
    }

    Column(Modifier.fillMaxSize().background(th.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Settings", 26.sp, th.fg, Modifier.weight(1f), weight = FontWeight.Bold)
            T("Back button to return", 11.sp, th.muted)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("general", "General") {
                Opts("Theme", listOf("hq" to "HQ", "frosted" to "Frosted"), s.theme) { onChange(s.copy(theme = it)) }
                Opts("Temperature", listOf("C" to "Celsius", "F" to "Fahrenheit"), s.tempUnit) { onChange(s.copy(tempUnit = it)) }
                Opts("Weight", listOf("kg" to "kg", "lb" to "lb", "st" to "st + lb"), s.weightUnit) { onChange(s.copy(weightUnit = it)) }
                Opts("Distance (saved for later)", listOf("km" to "km", "mi" to "miles"), s.distanceUnit) { onChange(s.copy(distanceUnit = it)) }
                Opts("Currency (saved for later)", listOf("GBP" to "GBP", "USD" to "USD", "EUR" to "EUR"), s.currency) { onChange(s.copy(currency = it)) }
                Opts("Date format", listOf("DD/MM/YYYY" to "DD/MM/YYYY", "MM/DD/YYYY" to "MM/DD/YYYY", "YYYY-MM-DD" to "YYYY-MM-DD", "D MMM YYYY" to "D MMM YYYY"), s.dateFormat) { onChange(s.copy(dateFormat = it)) }
                Opts("Time", listOf("24" to "24-hour", "12" to "12-hour"), if (s.time24) "24" else "12") { onChange(s.copy(time24 = it == "24")) }
                Opts("Week starts", listOf("MON" to "Monday", "SUN" to "Sunday"), s.weekStart) { onChange(s.copy(weekStart = it)) }
                Opts("Timezone", ZONES.map { it to (if (it == "device") "Device" else it.substringAfter('/').replace('_', ' ')) }, s.timezone) { onChange(s.copy(timezone = it)) }
            }

            Section("health", "Health targets") {
                NumField("Daily steps", s.stepsTarget, "steps") { onChange(s.copy(stepsTarget = it)) }
                NumField("Sleep", s.sleepTargetMin / 60, "hours", "Whole hours") { onChange(s.copy(sleepTargetMin = it * 60)) }
                NumField("Calories", s.caloriesTarget, "kcal") { onChange(s.copy(caloriesTarget = it)) }
                NumField("Protein", s.proteinTarget, "g") { onChange(s.copy(proteinTarget = it)) }
                NumField("Fibre", s.fibreTarget, "g") { onChange(s.copy(fibreTarget = it)) }
                NumField("Water", s.waterTargetMl, "ml", "Saved only. HQ doesn't read water yet") { onChange(s.copy(waterTargetMl = it)) }
                NumField("Weigh-ins per week", s.weighInsPerWeek, "") { onChange(s.copy(weighInsPerWeek = it)) }
                T("Leave a target empty and HQ shows the measurement without a target. Figures are for tracking only, not medical advice.", 12.sp, th.muted)
                Spacer(Modifier.height(8.dp))
                T("Health cards to show", 13.sp, th.muted, weight = FontWeight.Medium)
                HEALTH_CARDS.forEach { k ->
                    Toggle(HEALTH_NAMES[k] ?: k, null, k !in s.healthHidden) { on ->
                        onChange(s.copy(healthHidden = if (on) s.healthHidden - k else s.healthHidden + k))
                    }
                }
            }

            Section("dashboard", "Home cards") {
                s.homeOrder.forEachIndexed { i, k ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { T(HOME_NAMES[k] ?: k, 15.sp, th.fg, weight = FontWeight.Medium) }
                        Chip("Up", false) { if (i > 0) onChange(s.copy(homeOrder = s.homeOrder.toMutableList().also { it.add(i - 1, it.removeAt(i)) })) }
                        Spacer(Modifier.width(6.dp))
                        Chip("Down", false) { if (i < s.homeOrder.lastIndex) onChange(s.copy(homeOrder = s.homeOrder.toMutableList().also { it.add(i + 1, it.removeAt(i)) })) }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = k !in s.homeHidden, onCheckedChange = { on -> onChange(s.copy(homeHidden = if (on) s.homeHidden - k else s.homeHidden + k)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = if (th.glass) Color(0x99FFFFFF) else th.tab[0], checkedThumbColor = if (th.glass) Color(0xFF2E2E2E) else Color.White),
                        )
                    }
                }
                Btn("Reset order") { onChange(s.copy(homeOrder = HOME_CARDS, homeHidden = emptySet())) }
            }

            Section("refresh", "Refresh") {
                Toggle("Refresh in the background", "Android runs this when it chooses, so timings are approximate.", s.autoRefresh) { onChange(s.copy(autoRefresh = it)) }
                Opts("Every", INTERVALS.map { it to (if (it < 60) "$it min" else "${it / 60} h") }.map { it.first.toString() to it.second }, s.intervalMin.toString()) {
                    onChange(s.copy(intervalMin = it.toInt()))
                }
                Toggle("Refresh when HQ opens", null, s.refreshOnLaunch) { onChange(s.copy(refreshOnLaunch = it)) }
                Toggle("Keep last good data if a source fails", null, s.retainData) { onChange(s.copy(retainData = it)) }
                Toggle("Show source problems on Home", null, s.showErrors) { onChange(s.copy(showErrors = it)) }
                Spacer(Modifier.height(6.dp))
                T("Morning refresh (06:43)", 13.sp, th.muted, weight = FontWeight.Medium)
                T("The 06:43 run is a separate scheduled task that writes the HQ Feed (YouTube, Instagram, email) into Notion. It isn't an app setting, and the app can't force an exact time because Android may delay background work. The app picks the feed up on its next refresh. Manual refresh is always available.", 12.sp, th.muted)
                Btn("Refresh now") { actions.refreshNow() }
            }

            Section("notifications", "Notifications") {
                T("All off by default. Each one can fire at most once a day. No habit, step or workout nagging.", 12.sp, th.muted)
                Toggle("Overdue tasks", null, s.nOverdue) { onChange(s.copy(nOverdue = it)) }
                Toggle("Upcoming countdowns", "Within 3 days", s.nCountdowns) { onChange(s.copy(nCountdowns = it)) }
                Toggle("Failed refreshes", null, s.nFailed) { onChange(s.copy(nFailed = it)) }
                Toggle("Source connection problems", null, s.nSources) { onChange(s.copy(nSources = it)) }
            }

            Section("connections", "Connections") {
                T(if (tokenSaved) "Notion token saved" else "No Notion token yet", 14.sp, if (tokenSaved) th.ok else th.warn, weight = FontWeight.SemiBold)
                TextField(if (tokenSaved) "Replace Notion token" else "Notion token", "", "Paste token (ntn_...)", secret = true) { if (it.isNotBlank()) actions.saveToken(it) }
                TextField("Birthday (for the Life bar)", dob, "YYYY-MM-DD") { actions.saveDob(it) }
                Spacer(Modifier.height(4.dp))
                Btn("Allow calendar and Health Connect") { actions.allowAccess() }
                T("The token is stored only in the app's private storage on this phone.", 12.sp, th.muted)
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
                T("Updates arrive through Obtainium. Clearing cached data keeps your settings and token and never touches Notion or any other service.", 12.sp, th.muted)
                if (showLicence) {
                    Spacer(Modifier.height(8.dp))
                    T("Google Sans, SIL Open Font License 1.1", 13.sp, th.fg, weight = FontWeight.SemiBold)
                    T(actions.licenseText(), 10.sp, th.muted)
                }
            }
        }
    }
}
