# Sends a calendar link from your clipboard to SubcalScriber on a Light Phone plugged in over USB,
# for phones without Tool Manager. The link goes over the cable only; it isn't printed or saved here.
#
#   .\tool\scripts\add-feed.ps1                 # link from the clipboard, named after the calendar
#   .\tool\scripts\add-feed.ps1 -Name Family    # choose the name
#   .\tool\scripts\add-feed.ps1 -Link <url>     # give the link instead of using the clipboard
#
# Needs a debug build of SubcalScriber (the ones on GitHub are) and USB debugging on.
param(
    [string]$Name = "",
    [string]$Link = "",
    [string]$Serial = ""
)
$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$tool = "com.jmsbrain.subcalscriber"

if (-not $Link) { $Link = (Get-Clipboard -Raw) }
$Link = "$Link".Trim()
if ($Link -notmatch '^(?i)(https?|webcals?)://\S+$') {
    Write-Host "No calendar link found. Copy a link starting with https:// or webcal:// first (or use -Link)."
    exit 1
}

# The phone, never the emulator.
if (-not $Serial) {
    $phones = & $adb devices | Select-String '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value } |
        Where-Object { $_ -notlike 'emulator-*' }
    if (@($phones).Count -ne 1) {
        Write-Host "Plug in exactly one phone (found $(@($phones).Count)), or pass -Serial."
        exit 1
    }
    $Serial = @($phones)[0]
}

$line = if ($Name) { "${Name}: $Link" } else { $Link }
$file = "files/shared/add-feeds/link-$(Get-Date -Format yyyyMMddHHmmss).txt"
$line | & $adb -s $Serial shell "run-as $tool sh -c 'mkdir -p files/shared/add-feeds && cat > $file'"
if ($LASTEXITCODE -ne 0) { Write-Host "Couldn't reach SubcalScriber on the phone. Is it installed?"; exit 1 }

& $adb -s $Serial shell am force-stop $tool | Out-Null
& $adb -s $Serial shell monkey -p $tool -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
Write-Host "Sent. SubcalScriber is opening on the phone and will add the feed."
