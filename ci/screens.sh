#!/usr/bin/env bash
# Runs inside the emulator job. Never fails the job on one bad screen: every step is best effort and logged.
set -u
mkdir -p out
log() { echo "== $*" | tee -a out/log.txt; }
adb install -r fit.apk >>out/log.txt 2>&1
adb install -r hq.apk >>out/log.txt 2>&1
adb shell pm grant uk.woozy.hq android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell dumpsys package uk.woozy.hq | grep -i "READ_SUMMARY" >>out/log.txt 2>&1

shot() { sleep 2; adb exec-out screencap -p > "out/$1.png"; log "shot $1"; }

# Taps the first on-screen element whose text (or description) equals $1, ignoring case.
tap() {
  for attempt in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    xy=$(python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1].lower()
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1].lower()
    d = (re.search(r' content-desc="([^"]*)"', n) or [None, ''])[1].lower()
    if want in (t, d):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
    if [ -n "$xy" ]; then adb shell input tap $xy; log "tap $1 at $xy"; return 0; fi
    sleep 2
  done
  log "tap $1: not found"
  return 1
}

scroll_down() { adb shell input swipe $(($W/2)) $(($H*3/4)) $(($W/2)) $(($H/4)) 300; }

run_fit() {
  local tag=$1
  adb shell am force-stop uk.woozy.fit
  adb shell am start -n uk.woozy.fit/.MainActivity --ez demo true >/dev/null
  sleep 10
  shot "fit-$tag-1-today"
  scroll_down; shot "fit-$tag-2-today-lower"
  tap "Diary" && shot "fit-$tag-3-diary"
  tap "Train" && shot "fit-$tag-4-train"
  tap "Progress" && shot "fit-$tag-5-progress"
  tap "Today"
  tap "Settings" || tap "M"
  sleep 2; for i in 1 2 3 4 5; do scroll_down; done; shot "fit-$tag-6-settings-about"
  adb shell input keyevent 4
}

run_hq() {
  local tag=$1
  adb shell am force-stop uk.woozy.hq
  adb shell am start -n uk.woozy.hq/.MainActivity --ez demo true >/dev/null
  sleep 12
  shot "hq-$tag-1-home"
  scroll_down; shot "hq-$tag-2-home-lower"
  tap "Health" && sleep 2 && shot "hq-$tag-3-health"
  scroll_down; shot "hq-$tag-4-health-lower"
  scroll_down; shot "hq-$tag-5-health-bottom"
  tap "Settings" && sleep 2
  for i in 1 2 3 4 5 6; do scroll_down; done; shot "hq-$tag-6-settings-about"
  adb shell input keyevent 4
}

# Phone (Pixel 6 size, close to the S25 Ultra's 412 dp width)
adb shell wm size reset; adb shell wm density reset
W=1080; H=2400
run_fit phone
run_hq phone

# Galaxy Tab S9 Ultra, landscape: 2960 x 1848 at 340 dpi (about 1393 x 870 dp)
adb shell wm size 2960x1848; adb shell wm density 340
W=2960; H=1848
sleep 3
run_fit tablet
run_hq tablet

# Tablet in dark mode
adb shell cmd uimode night yes; sleep 2
adb shell am force-stop uk.woozy.fit; adb shell am start -n uk.woozy.fit/.MainActivity >/dev/null; sleep 8; shot "fit-tablet-dark-today"
adb shell am force-stop uk.woozy.hq; adb shell am start -n uk.woozy.hq/.MainActivity >/dev/null; sleep 10; shot "hq-tablet-dark-home"
adb shell cmd uimode night no

# Portrait tablet too
adb shell wm size 1848x2960; sleep 2
adb shell am force-stop uk.woozy.hq; adb shell am start -n uk.woozy.hq/.MainActivity >/dev/null; sleep 10; shot "hq-tablet-portrait-home"
adb shell am force-stop uk.woozy.fit; adb shell am start -n uk.woozy.fit/.MainActivity >/dev/null; sleep 8; shot "fit-tablet-portrait-today"
adb logcat -d -t 4000 | grep -E "AndroidRuntime|FATAL|uk.woozy" | tail -200 > out/logcat.txt
exit 0
