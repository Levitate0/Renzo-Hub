<#
.SYNOPSIS
  Sweeps the Renzo `demo` build across the Google Play form factors and pages,
  saving one screenshot per combination.

.DESCRIPTION
  Requires the demo variant already installed and SIGNED IN to your server
  (this script does not log in for you). Each shot is taken by relaunching the
  app straight onto a page with `am start -e page <name>`, so the results are
  deterministic instead of depending on tap coordinates.

  The emulator's resolution is changed per form factor and ALWAYS restored,
  including on Ctrl-C.

.EXAMPLE
  .\capture-play-shots.ps1
  .\capture-play-shots.ps1 -FormFactors phone,tv -Pages discover,library,updates
  .\capture-play-shots.ps1 -TitleId 21   # also shoots the series + player pages
#>
[CmdletBinding()]
param(
    [string]   $Serial,
    [string]   $Out = ".\play-shots",
    [string[]] $FormFactors = @("phone", "tablet7", "tablet10", "tv"),
    [string[]] $Pages,
    [int]      $TitleId = 0,
    [int]      $Episode = 1,
    [string]   $Apk,
    [switch]   $SkipInstall
)

$ErrorActionPreference = "Stop"

# Renzo Hub: one activity hosts both halves, so the entry point is HubActivity,
# not Renzo's old MainActivity (which no longer exists — RenzoRoot is a
# composable hosted by HubActivity).
$Package  = "top.levitatemedia.renzo.demo"
$Activity = "top.levitatemedia.renzo.hub.HubActivity"

# --- adb ---------------------------------------------------------------------
function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    )
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    throw "adb not found. Add platform-tools to PATH, or set ANDROID_HOME."
}

$Adb = Resolve-Adb
Write-Host "adb: $Adb"

function Adb {
    # NOT named $Args - that shadows PowerShell's automatic variable.
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Arguments)
    $full = @()
    if ($Serial) { $full += @("-s", $Serial) }
    $full += $Arguments
    & $Adb @full
}

# --- form factors ------------------------------------------------------------
# width x height x density. TV is shot from an ordinary AVD: `-e tv 1` forces
# the leanback layout, landscape and hidden system bars.
$Profiles = [ordered]@{
    phone    = @{ Size = "1080x1920"; Density = 420; Tv = $false }
    tablet7  = @{ Size = "1200x1920"; Density = 320; Tv = $false }
    tablet10 = @{ Size = "1920x1200"; Density = 240; Tv = $false }
    tv       = @{ Size = "1920x1080"; Density = 320; Tv = $true  }
}

# Settle delay per page: grids and the player need longer to paint.
$AllPages = [ordered]@{
    "discover"      = 3500
    "category"      = 3500
    "library"       = 3500
    "library-folder"= 3500
    "updates"       = 3500
    "history"       = 3000
    "downloads"     = 3000
    "search"        = 3500
    "drawer"        = 2500
    "accountmenu"   = 2500
    "account"       = 2500
    "credentials"   = 2500
    "defaults"      = 2500
    "apikey"        = 2500
    "users"         = 3000
    "settings"      = 3000
    "appearance"    = 2500
    "title"         = 4000
    "player"        = 6000
}

if (-not $Pages) { $Pages = @($AllPages.Keys) }
# title/player need an id from the user's own library.
if ($TitleId -le 0) {
    $skipped = $Pages | Where-Object { $_ -in @("title", "player") }
    if ($skipped) {
        Write-Warning "Skipping $($skipped -join ', ') - pass -TitleId <anilistId> to include them."
    }
    $Pages = $Pages | Where-Object { $_ -notin @("title", "player") }
}

foreach ($p in $Pages) {
    if (-not $AllPages.Contains($p)) { throw "Unknown page '$p'. Known: $($AllPages.Keys -join ', ')" }
}
foreach ($f in $FormFactors) {
    if (-not $Profiles.Contains($f)) { throw "Unknown form factor '$f'. Known: $($Profiles.Keys -join ', ')" }
}

# --- device prep -------------------------------------------------------------
Write-Host "Waiting for device..."
Adb wait-for-device | Out-Null
for ($i = 0; $i -lt 60; $i++) {
    $booted = (Adb shell getprop sys.boot_completed) -replace '\s', ''
    if ($booted -eq "1") { break }
    Start-Sleep -Seconds 2
}

if (-not $SkipInstall) {
    if (-not $Apk) {
        $Apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\demo\app-demo.apk"
    }
    if (Test-Path $Apk) {
        Write-Host "Installing $Apk"
        Adb install -r -t $Apk | Out-Null
    } else {
        Write-Warning "APK not found at $Apk - using whatever is already installed."
    }
}

$results = @()

try {
    # Animations off, so nothing is caught mid-transition.
    foreach ($k in @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
        Adb shell settings put global $k 0 | Out-Null
    }
    # Tidy status bar: 12:00, full battery, full signal, no notification icons.
    try {
        Adb shell settings put global sysui_demo_allowed 1 | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command enter | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false | Out-Null
    } catch {
        Write-Warning "System UI demo mode unavailable on this image - status bar left as is."
    }

    foreach ($ff in $FormFactors) {
        $prof = $Profiles[$ff]
        Write-Host ""
        Write-Host "=== $ff  $($prof.Size) @ $($prof.Density)dpi ===" -ForegroundColor Cyan

        Adb shell wm size $prof.Size    | Out-Null
        Adb shell wm density $prof.Density | Out-Null
        Start-Sleep -Milliseconds 1500

        $dir = Join-Path $Out $ff
        New-Item -ItemType Directory -Force -Path $dir | Out-Null

        $n = 0
        foreach ($page in $Pages) {
            $n++
            $delay = $AllPages[$page]

            # -e app renzo skips the launch picker. Without it every capture
            # lands on the picker instead of the requested page, because
            # HubActivity starts with target=null on anything that isn't
            # leanback. See tools/README-CAPTURE.md — this extra needs the
            # HubActivity seed change to have been applied.
            $startArgs = @("shell", "am", "start", "-S", "-W", "-n", "$Package/$Activity",
                           "-e", "app", "renzo", "-e", "page", $page)
            if ($prof.Tv)           { $startArgs += @("-e", "tv", "1") }
            if ($page -eq "search") { $startArgs += @("-e", "query", "a") }
            if ($TitleId -gt 0 -and ($page -eq "title" -or $page -eq "player")) {
                $startArgs += @("-e", "id", "$TitleId", "-e", "ep", "$Episode")
            }
            Adb @startArgs | Out-Null
            Start-Sleep -Milliseconds $delay

            $name = "{0:d2}-{1}.png" -f $n, $page
            $dest = Join-Path $dir $name

            # screencap + pull, NOT `exec-out > file`: PowerShell's redirection
            # re-encodes the stream and produces a corrupt PNG.
            Adb shell screencap -p /sdcard/renzo-shot.png | Out-Null
            Adb pull /sdcard/renzo-shot.png $dest | Out-Null

            $size = ""
            if (Test-Path $dest) {
                $bytes = (Get-Item $dest).Length
                $size = "{0:n0} KB" -f ($bytes / 1KB)
            }
            Write-Host ("  {0,-16} -> {1}  {2}" -f $page, $name, $size)
            $results += [pscustomobject]@{ FormFactor = $ff; Page = $page; File = $dest; Size = $size }
        }
    }
}
finally {
    Write-Host ""
    Write-Host "Restoring device state..." -ForegroundColor Yellow
    Adb shell wm size reset    | Out-Null
    Adb shell wm density reset | Out-Null
    foreach ($k in @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
        Adb shell settings put global $k 1 | Out-Null
    }
    try { Adb shell am broadcast -a com.android.systemui.demo -e command exit | Out-Null } catch { }
    Adb shell rm -f /sdcard/renzo-shot.png | Out-Null
}

Write-Host ""
$results | Format-Table -AutoSize
Write-Host "$($results.Count) screenshots in $Out"
