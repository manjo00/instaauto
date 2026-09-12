# Pull everything needed to explain what the app did, and print it as one timeline.
#
# Written after 2026-09-09, when working out why a post had not published took a database
# pull, three dumpsys calls and a lot of guessing. All of that is here now.
#
#   powershell -File tools\diagnostics.ps1
#   powershell -File tools\diagnostics.ps1 -Hours 72
#
# Needs: the tablet connected with USB debugging, and a debug build installed (run-as only
# works on debuggable packages).

param(
    [int]$Hours = 168,
    [string]$Package = "com.autoinsta"
)

$ErrorActionPreference = "Stop"

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

$devices = & $adb devices | Select-String -Pattern "\tdevice$"
if (-not $devices) { throw "No device. Check the cable and that USB debugging is allowed." }

$out = Join-Path $env:TEMP "autoinsta-diagnostics"
New-Item -ItemType Directory -Force $out | Out-Null

# ── The app's own record ───────────────────────────────────────────────────
# WAL and SHM matter: recent writes live in the WAL, and pulling the .db alone gives a
# stale picture that looks convincingly complete.
foreach ($f in @("autoinsta.db", "autoinsta.db-wal", "autoinsta.db-shm")) {
    cmd /c "`"$adb`" exec-out run-as $Package cat databases/$f > `"$out\$f`"" 2>$null
}
if (-not (Test-Path "$out\autoinsta.db") -or (Get-Item "$out\autoinsta.db").Length -eq 0) {
    throw "Could not read the database. Is the DEBUG build installed?"
}

# ── What Android thinks of the app ─────────────────────────────────────────
# The 2026-09-09 root cause was here, not in the database: standby bucket 40 (RARE) meant
# even exact alarms were throttled to roughly one a day.
$bucket = (& $adb shell "am get-standby-bucket $Package").Trim()

# Anchored to "user," on purpose. A loose grep also matches the system allowlist, which
# every device has entries in and which says nothing about this app being exempt — that
# mismatch produced a confidently wrong "exempt: yes" the first time this ran.
$exempt = & $adb shell "dumpsys deviceidle whitelist" | Select-String "^user,$Package,"

$bucketName = switch ([int]$bucket) {
    5  { "EXEMPTED - on the battery allowlist, alarms run freely" }
    10 { "ACTIVE" }; 20 { "WORKING_SET" }; 30 { "FREQUENT" }
    40 { "RARE - alarms throttled to about one a day" }
    45 { "RESTRICTED - alarms barely run" }
    default { "unknown" }
}

Write-Host ""
Write-Host "=== Android's view of $Package ===" -ForegroundColor Cyan
Write-Host "  standby bucket : $bucket ($bucketName)"
if ($exempt) {
    Write-Host "  battery exempt : yes"
} else {
    Write-Host "  battery exempt : NO - this is what silently stops posts firing on time" -ForegroundColor Yellow
}

Write-Host ""
# Deliberately "mentions", not "armed": dumpsys alarm interleaves pending alarms with a
# history of cancelled ones, and claiming the difference without parsing it would be a
# guess dressed as a reading.
Write-Host "=== Alarm entries mentioning the app (pending + recent history) ===" -ForegroundColor Cyan
$alarms = & $adb shell "dumpsys alarm | grep -i '$Package' | head -6"
if ($alarms) { $alarms | ForEach-Object { "  $_" } } else { Write-Host "  nothing" }

# ── The timeline ───────────────────────────────────────────────────────────
$python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $python) { throw "python not found; needed to read the sqlite file" }

& $python (Join-Path $PSScriptRoot "render_diagnostics.py") $out $Hours

Write-Host ""
Write-Host "Database copied to $out" -ForegroundColor DarkGray
