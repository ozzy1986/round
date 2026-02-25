# Build Android debug APK and copy to project root as Round-debug.apk for easy transfer to phone.
# Run from project root: .\scripts\build-apk.ps1

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$projectRoot\android")) {
    $projectRoot = $PSScriptRoot
    while (-not (Test-Path "$projectRoot\android") -and $projectRoot) { $projectRoot = Split-Path -Parent $projectRoot }
}
if (-not (Test-Path "$projectRoot\android")) {
    Write-Error "Project root with android/ not found. Run from repo root or scripts/."
}
$androidDir = "$projectRoot\android"
$apkOut = "$androidDir\app\build\outputs\apk\debug\app-debug.apk"
$copyTo = "$projectRoot\Round-debug.apk"

Push-Location $androidDir
try {
    if (Test-Path ".\gradlew.bat") {
        & .\gradlew.bat assembleDebug
    } else {
        Write-Host "Gradle wrapper not found. Use Android Studio: open android/ then Build > Build APK(s)."
        Write-Host "Or add Gradle wrapper (gradle wrapper) then run: cd android; .\gradlew.bat assembleDebug"
        exit 1
    }
    if (Test-Path $apkOut) {
        Copy-Item $apkOut $copyTo -Force
        Write-Host "APK copied to: $copyTo"
        Write-Host "Copy this file to your phone and open it to install."
    } else {
        Write-Host "Build may have failed. Check output above. Expected: $apkOut"
        exit 1
    }
} finally {
    Pop-Location
}
