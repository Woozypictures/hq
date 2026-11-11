# HQ handover (for audit)

Owner: Mahesh Sharma. Written 10 Nov 2026. Personal system, single user, nothing is shared or public except the widget pages. No secrets are in this file or in the repo.

## 1. Goal

One hub for Mahesh's life with the least upkeep. Notion holds the data. A personal Android app ("HQ") is the one-page summary: tap Refresh, it pools everything, and tapping any item opens the right Notion page or app. A matching home-screen widget replaces three Notion widgets.

## 2. Pieces and where they live

| Piece | Where | Notes |
|---|---|---|
| Notion workspace | DASHBOARD page `ea4d45cd57594ee7987f9120609c8293` | Hubs: T O D A Y, L I F E, H E A L T H, C A R E E R, C R E A T E |
| HQ Android app + widget | GitHub `Woozypictures/hq` (private), branch `main` | Kotlin + Jetpack Compose, package `uk.woozy.hq` |
| Builds / releases | GitHub Actions `.github/workflows/build.yml` | Signed APK published as a release `v0.<run>`; build log pushed to orphan branch `build-log` |
| App updates on phone | Obtainium, pointed at the private repo with a read-only fine-grained GitHub token | |
| Web widgets for Notion embeds | GitHub Pages, repo `Woozypictures/widgets` (public) at `woozypictures.github.io/widgets/` | clock, life, weather, pomodoro, countdown (`countdown.html?t=<name>&d=<YYYY-MM-DD>`) |
| Morning refresh | Scheduled Claude task "Morning Notion refresh", id `trig_018caKbH6bTmSAmBG1oKzx2p`, daily 06:43 Europe/London | Writes Today page content and the HQ Feed page |
| HQ Feed | Notion page `4144afc9-3b00-8116-889c-f5fc44bf53df` under T O D A Y | One JSON code block read by the app |
| Old HQ artifact | https://claude.ai/artifact/5ahzkqtsMjKSjEyTjjdJ5t | Lore Club HQ Basic. Superseded, retire when Mahesh says so. Its look is the app's "HQ" theme |
| Phone widgets (not ours) | KWGT "Frosted Glass" pack, 26 presets | Reference for the widget look only |

## 3. Data flow

Live from the phone each refresh (and every 30 minutes via WorkManager):
- Notion API (needs an internal "API token" connection named HQ, added to DASHBOARD so access inherits to child pages): tasks, countdowns, HQ Feed. Notion-Version `2025-09-03`, queries use `data_sources/{id}/query`.
- Health Connect (read): steps, sleep, resting HR, weight, active/total calories, nutrition. Samsung Health and Cronometer feed Health Connect.
- Calendar provider (read, next 3 days).
- open-meteo (Slough: 51.51, -0.59).

Only via the morning Claude task (written to HQ Feed JSON): YouTube stats (vidIQ), Instagram stats (vidIQ, currently null), emails needing a reply (Gmail, read-only), heads-up notes.

HQ Feed JSON shape:
```
{"updated":"YYYY-MM-DDTHH:MM",
 "youtube":{"channel","subs","views","videos","views30","subs30"},
 "instagram":{"handle","followers","posts","reach30"} | null,
 "emails":[{"from","subject","url"}],
 "notes":["..."]}
```

Notion IDs used by the app (all in `Sources.kt`, object `Links`, `NotionSrc`):
- T A S K S data source `b9b6a15d-50d6-46bd-901b-0b332aba7157` (DB `89e4f6a36ea9414ca8b4684774ec4284`). Properties: Task, Area (Flat, Health, CIMA, Content, Money, Life admin, Work, Music), Status, Impact, Urgency, Energy fit, Time, Due, Notes, formulas Score and Lane (Do now >= 20, Schedule >= 8, Park, Done).
- Countdowns data source `7ec5d191-6cfa-4977-953f-9889beef130a`: Event, Date, Show on Today, Source, formula Days left.
- Hub child page IDs are hard-coded in `Links`. If Mahesh renames or moves pages, the IDs still work; if he deletes and recreates, they break.

## 4. App structure (`app/src/main/java/uk/woozy/hq/`)

- `Model.kt`: data classes, `Store` (SharedPreferences: Notion token, birthday, step goal, theme, last snapshot JSON).
- `Sources.kt`: `NotionSrc`, `WeatherSrc`, `CalendarSrc`, `HealthSrc`, `Repo.refresh` (parallel fetch; a failed source keeps its last good value and records a short error), `friendly()` error text.
- `MainActivity.kt`: Compose UI. Tabs Home, Life, Health, Career, Create. Themes "hq" (cream, tab colours, Hanken Grotesk) and "frosted" (dark #2e2e2e glass, ProductSans). `open()` sends Notion links to the Notion app via `notion://` with package `notion.id`, falling back to the browser.
- `HqWidget.kt`: 3x1 widget drawn to a bitmap in the Frosted Glass style (fill 5%>22% white gradient, rim 23%>38%, 30-square bar, ProductSans). Always frosted.
- `RefreshWorker.kt`: 30-minute periodic refresh.
- Min SDK 28, target 35. Health Connect permissions declared in the manifest.

## 5. Build, signing, updates

- CI builds on every push to `main` and on manual dispatch. Version code = run number.
- Signing key: a self-signed RSA key. It is NOT in the repo. It lives in GitHub Actions secrets `HQ_KEYSTORE_B64` and `HQ_KEYSTORE_PASS`. A copy was generated in the Claude container's scratch space and given to Mahesh as a text file. **Risk:** if both the secret and Mahesh's copy are lost, future builds will have a new signature and Obtainium/Android will refuse to update in place (uninstall and reinstall needed). Audit item: confirm Mahesh keeps a backup.
- First build succeeded (v0.1), second (v0.2) succeeded. Neither the compile nor the UI was run on a device by Claude; Mahesh installed and confirmed it boots.
- Build log for debugging: `git fetch origin build-log && git show origin/build-log:build.log`.

## 6. Decisions and constraints

- Single user, personal use, sideloaded. No Play Store.
- Gmail, Calendar, vidIQ are only reachable through Claude connectors, which only work in Claude sessions. That is why those items arrive through the morning task and the Notion feed page, not live in the app.
- Mahesh does not want an extra dashboard to maintain, and wants the fewest inputs. Retire redundancy rather than add it.
- Kept after review: Moodboard, Fitness, Goals, Reading, Habits, Apartment (name kept), Portfolios, Healthcare, Travel, Content, Finances, Music. CIMA is to be used heavily.
- Trashed (30-day recovery): House, Shopping, Food, Energy DB, Ideabox DB. Wix widget site blanked.
- Style preferences: letter-spaced titles ("T O D A Y") for his own pages; no em-dashes; £ in thousands in finance commentary.

## 7. Known issues and open items (audit these first)

1. vidIQ Instagram: Mahesh says it is connected on the vidIQ website, but `vidiq_instagram_connected_accounts` fails with a schema error (the account record is missing tokenStatus, accessLevel, ownerInsightsAvailable, publishingAvailable, fallbackReason). Without `platformAccountId` the owner-insights call cannot run. Instagram stays null in HQ Feed until this is fixed. Possible vidIQ-side bug or a stale link record.
2. Notion deep links: before v0.3 `https://www.notion.so/...` links opened in Chrome. v0.3 switches to `notion://` with package `notion.id`. Needs on-device confirmation on Samsung S25 Ultra / One UI 8.5.
3. Notion connection access: tasks/countdowns/feed fail with "can't see that page" until the HQ connection is added to DASHBOARD. Mahesh reports this is now sorted.
4. Health Connect: aggregation and background read are untested on device. The README for the KWGT pack notes the same for its own formulas.
5. Not yet reworked in Notion: CIMA as a study tracker, Finances, Fitness weekly log, Goals, Travel (a Wanderlog link property; the Wanderlog MCP is unofficial and meant to run locally), Habits extra columns (hide, do not delete).
6. Old HQ artifact and any leftover Wix site not yet formally retired or deleted.
7. The morning task prompt now includes the HQ Feed step. First scheduled run with it is 11 Nov 06:43. Verify the JSON it writes parses (the app ignores a malformed feed and shows the last good one).

## 8. Suggested audit checklist

- Security: no tokens or keystore in the repo (`git log -p` and `git grep -i "password\|secret\|ntn_"`). Notion token is stored only in the app's private SharedPreferences. The GitHub token Obtainium uses should be fine-grained, read-only, repo `hq` only.
- Permissions: only INTERNET, READ_CALENDAR and Health Connect read permissions are declared. Confirm nothing broader.
- Correctness: `NotionSrc.prop()` handling of formula and date fields; countdown day maths use Europe/London; calendar all-day time-zone shift in `CalendarSrc`; Health aggregation time ranges.
- Failure handling: each source fails independently and keeps the last good value; check the Needs attention card on Home shows real reasons.
- Reliability: WorkManager 30-minute refresh versus Samsung battery restrictions (the app may need "Unrestricted" battery).
- Maintainability: hard-coded Notion IDs in `Links`; consider reading hub children from the API instead.
- Cost/usage: the morning task runs on a Claude model and several connectors every day; confirm it fits the plan's usage.

## 9. v0.4 changes (ChatGPT audit spec)
- Settings screen (full page, accordion): General, Health targets, Home cards, Refresh, Notifications, Connections, Advanced. Stored in separate prefs file `hq_settings` (JSON); theme and step goal migrated from old prefs once, old values untouched.
- Defaults: theme HQ, Celsius, km, kg, GBP, DD/MM/YYYY, Europe/London, week Monday, 24h; steps 10,000, sleep 8h; calories/protein/fibre/water/weigh-ins unset (shown without a target); auto refresh on, 30 min, refresh on launch on, keep last data on, show errors on; all notifications off.
- Home cards (order and visibility configurable): Priority tasks, Steps, Sleep, Resting HR, Upcoming events, Countdowns, HQ Feed, Weather. Problems banner appears when a source fails.
- Health tab: steps, sleep, RHR, weight, nutrition (calories, protein, fibre), calories burned, weekly overview (7/30 days, "n of N days" notes, missing days never counted as zero). Each card shows recorded time and last Health Connect read.
- Health Connect aggregation logic unchanged. Extra reads (protein, fibre, history) live in HealthExtra.kt.
- Diagnostics: per-source last attempt/success/status/error in Status.kt; failure keeps cached data. Shown in Settings > Advanced.
- Notifications: optional, once per day each, POST_NOTIFICATIONS requested only when one is switched on.
- Font: Google Sans (SIL OFL 1.1, from @fontsource/google-sans 5.3.2) bundled for both themes; licence in assets/licenses. Hanken Grotesk removed. Widget still uses Product Sans from the owner's KWGT pack (proprietary, private repo, personal use); decide whether to switch it.
- Not done: water reading, charts, drag reorder (up/down used), distance/currency are saved but unused, 06:43 is the Claude scheduled task not an app timer.
- Build: GitHub Actions run 5 succeeded. Not tested on a device.

## 10. v0.5 to v0.7 (Nov 2026)
- Colour schemes ported from the HQ artifact (16 + Frosted), Appearance setting (match phone / light / dark; dark variant derived for light schemes). Font unchanged.
- Pull to refresh; long-press any card on any tab to drag-rearrange (per-tab order saved in settings).
- Tabs have icons and sub-page chips (Life: Apartment, Finances, Travel, Healthcare, Archive; Health: Fitness, Habits; Career: CIMA, Portfolios, Goals, Be Greater; Create: Content, Music, Moodboard, Reading). Sub-pages read the Notion page blocks (blocks/{id}/children, cached) and show child pages as cards plus tasks by area. Hub links removed.
- Home: steps/sleep/heart rate as 3 small cards (can be made wide), Calories and macros card (Cronometer-style via Health Connect: energy, protein, net carbs, fat), events toggle (Today / This week / This month; week and month = all-day only), countdowns open the matching calendar event, weather opens the weather app, YouTube/Instagram tiles open the apps, Samsung Health deep links (shealth://tracker.*, UNVERIFIED, falls back to launching the app).
- Targets: Health Connect and Cronometer expose no targets, so they are settings. Calories 2297, protein 150, net carbs 260, fat 70 seeded once from the Cronometer screenshot.
- Instagram: vidiq_instagram_connected_accounts is broken on vidIQ's side; the morning task now uses vidiq_social_account_list + vidiq_instagram_owner_insights (0 credits). Feed JSON gained youtube.handle.
- Notion: Archive moved under LIFE; Notes page removed from the app; the Notes page itself still exists in Notion (trash denied by the tool, delete by hand).
- Build: GitHub Actions run 11 succeeded. Not tested on a device.
