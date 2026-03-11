# Fix Android SDK folder names that cause "inconsistent location" Gradle warnings.
# Run from project root when Gradle reports e.g. build-tools;36.1.0-rc1 in 36.1.0.
# Uses android/local.properties sdk.dir; safe to run multiple times (idempotent).

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$projectRoot\android\local.properties")) {
    Write-Error "android/local.properties not found. Run from repo root."
}
$line = Get-Content "$projectRoot\android\local.properties" | Where-Object { $_ -match 'sdk\.dir=' } | Select-Object -First 1
if (-not $line -or $line -notmatch 'sdk\.dir=(.+)') {
    Write-Error "sdk.dir not found in android/local.properties"
}
$raw = $Matches[1].Trim()
$sdkDir = $raw.Replace('\\', '\').Replace('\:', ':')
$bt = Join-Path $sdkDir "build-tools"
$pl = Join-Path $sdkDir "platforms"
$done = 0
if ((Test-Path "$bt\36.1.0") -and -not (Test-Path "$bt\36.1.0-rc1")) {
    Rename-Item -LiteralPath "$bt\36.1.0" -NewName "36.1.0-rc1"; $done++
}
if ((Test-Path "$bt\36.1.0-2") -and -not (Test-Path "$bt\36.1.0")) {
    Rename-Item -LiteralPath "$bt\36.1.0-2" -NewName "36.1.0"; $done++
}
if ((Test-Path "$pl\android-36") -and -not (Test-Path "$pl\android-36-ext19")) {
    Rename-Item -LiteralPath "$pl\android-36" -NewName "android-36-ext19"; $done++
}
if ((Test-Path "$pl\android-36-2") -and -not (Test-Path "$pl\android-36")) {
    Rename-Item -LiteralPath "$pl\android-36-2" -NewName "android-36"; $done++
}
if ($done -eq 0) { Write-Host "SDK paths already consistent. No renames needed." } else { Write-Host "Renamed $done SDK folder(s)." }
