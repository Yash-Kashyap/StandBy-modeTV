<#
  StandBy TV - one-step TV setup (Windows PowerShell)

  Installs the app over ADB, allows the Now Playing music screen, and makes StandBy TV
  the screensaver that starts automatically when the TV is idle.

  Usage (from the repo folder):
    powershell -ExecutionPolicy Bypass -File tools\setup-tv.ps1 -TvIp 192.168.1.9

  Options:
    -IdleMinutes 5     minutes of no use before StandBy TV starts   (default 5)
    -SleepHours 4      hours the screensaver runs before the TV turns itself off (0 = never)
    -Apk <path>        APK to install (default: apk\StandBy-TV.apk next to this repo)
    -SkipInstall       only apply the settings
#>
param(
    [Parameter(Mandatory = $true)][string]$TvIp,
    [int]$IdleMinutes = 5,
    [int]$SleepHours = 4,
    [string]$Apk = "",
    [switch]$SkipInstall
)

$Pkg = "com.yash.standbytv"
$Listener = "$Pkg/$Pkg.MediaListener"
$Dream = "$Pkg/.StandbyDream"

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "    OK  $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "    !!  $msg" -ForegroundColor Yellow }

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "adb was not found. Install Google's platform-tools first:" -ForegroundColor Red
    Write-Host "  winget install Google.PlatformTools     (then open a NEW PowerShell window)"
    exit 1
}

$Serial = if ($TvIp -match ":") { $TvIp } else { "${TvIp}:5555" }
function Tv { & adb -s $Serial @args }
function Get-Setting($ns, $key) { ((Tv shell settings get $ns $key) | Out-String).Trim() }

# 1. Connect -----------------------------------------------------------------
Step "Connecting to the TV at $Serial"
adb connect $Serial | Out-Host
Start-Sleep -Seconds 2
$state = ((adb -s $Serial get-state 2>$null) | Out-String).Trim()
if ($state -ne "device") {
    Warn "The TV did not accept the connection yet."
    Write-Host "    On the TV: a pop-up 'Allow USB debugging?' should appear - tick 'Always allow from this computer' and choose OK."
    Write-Host "    No pop-up? Turn on Developer options > USB debugging (see README), then run this script again."
    exit 1
}
Ok "Connected"

# 2. Install -----------------------------------------------------------------
if (-not $SkipInstall) {
    if ($Apk -eq "") { $Apk = Join-Path (Split-Path $PSScriptRoot -Parent) "apk\StandBy-TV.apk" }
    if (-not (Test-Path $Apk)) { Write-Host "APK not found: $Apk  (use -Apk <path>)" -ForegroundColor Red; exit 1 }
    Step "Installing $(Split-Path $Apk -Leaf)"
    $out = (Tv install -r $Apk | Out-String)
    if ($out -match "Success") { Ok "Installed" } else { Write-Host $out; Warn "Install failed - see the message above"; exit 1 }
}

# 3. Music controls (Now Playing) ---------------------------------------------
Step "Allowing the Now Playing screen to see Spotify / YouTube Music / YouTube"
Tv shell cmd notification allow_listener $Listener 2>$null | Out-Null
$cur = Get-Setting secure enabled_notification_listeners
if ($cur -notmatch [regex]::Escape($Listener)) {
    # Some TVs ignore 'cmd notification' - add ourselves to the list while keeping existing entries.
    $new = if ($cur -eq "" -or $cur -eq "null") { $Listener } else { "${cur}:${Listener}" }
    Tv shell settings put secure enabled_notification_listeners "$new" | Out-Null
    $cur = Get-Setting secure enabled_notification_listeners
}
if ($cur -match [regex]::Escape($Listener)) { Ok "Music access allowed" } else { Warn "Could not allow music access on this TV (other screens still work)" }

# 4. Screensaver -------------------------------------------------------------
Step "Making StandBy TV the screensaver (starts after $IdleMinutes min idle)"
Tv shell settings put secure screensaver_enabled 1 | Out-Null
Tv shell settings put secure screensaver_activate_on_sleep 1 | Out-Null
Tv shell settings put secure screensaver_components $Dream | Out-Null
Tv shell settings put system screen_off_timeout ($IdleMinutes * 60000) | Out-Null
# The TV's own "turn off" timer must be LONGER than the screensaver delay, or the TV just sleeps.
$sleepMs = if ($SleepHours -le 0) { 0 } else { $SleepHours * 3600000 }
Tv shell settings put secure sleep_timeout $sleepMs | Out-Null

# 5. Verify ------------------------------------------------------------------
Step "Checking"
$checks = @(
    @("secure", "screensaver_enabled", "1"),
    @("secure", "screensaver_activate_on_sleep", "1"),
    @("secure", "screensaver_components", $Dream),
    @("system", "screen_off_timeout", "$($IdleMinutes * 60000)"),
    @("secure", "sleep_timeout", "$sleepMs")
)
$allOk = $true
foreach ($c in $checks) {
    $v = Get-Setting $c[0] $c[1]
    if ($v -eq $c[2]) { Ok "$($c[1]) = $v" } else { Warn "$($c[1]) = $v (expected $($c[2]))"; $allOk = $false }
}
if (-not $allOk) { Warn "Some settings did not stick - this TV's maker may lock them. See Troubleshooting in the README." }

# 6. Preview -----------------------------------------------------------------
Step "Showing the screensaver now as a test (press any remote button to close it)"
Tv shell am start -n com.android.systemui/.Somnambulator 2>$null | Out-Null
Write-Host "`nDone. Leave the TV idle on the home screen for $IdleMinutes minutes and StandBy TV starts by itself." -ForegroundColor Green
