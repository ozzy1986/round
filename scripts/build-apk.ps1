# Build an Android APK and copy it to android/ for easy transfer to the phone.
# Default target is localRelease: non-debuggable, R8/resource shrinking enabled,
# debug-signed for deterministic local QA builds.
# Run from project root: .\scripts\build-apk.ps1

param(
    [ValidateSet("localRelease", "release", "debug")]
    [string]$BuildType = "localRelease"
)

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
$buildConfig = switch ($BuildType) {
    "debug" {
        @{
            Task      = "assembleDebug"
            OutputDir = "debug"
            FileName  = "app-debug.apk"
            CopyName  = "Round-debug.apk"
        }
    }
    "release" {
        @{
            Task      = "assembleRelease"
            OutputDir = "release"
            FileName  = "app-release.apk"
            CopyName  = "Round-release.apk"
        }
    }
    default {
        @{
            Task      = "assembleLocalRelease"
            OutputDir = "localRelease"
            FileName  = "app-localRelease.apk"
            CopyName  = "Round-local-release.apk"
        }
    }
}
$apkOut = "$androidDir\app\build\outputs\apk\$($buildConfig.OutputDir)\$($buildConfig.FileName)"
$copyTo = "$androidDir\$($buildConfig.CopyName)"

Push-Location $androidDir
try {
    if (Test-Path ".\gradlew.bat") {
        Write-Host "Building $BuildType APK..."
        & .\gradlew.bat --stop | Out-Host
        $gradleArgs = @($buildConfig.Task)
        if ($BuildType -ne "debug") {
            $gradleArgs = @(
                "--no-daemon",
                "--no-parallel",
                "-Dkotlin.compiler.execution.strategy=in-process",
                $buildConfig.Task
            )
        }
        & .\gradlew.bat @gradleArgs
    } else {
        Write-Host "Gradle wrapper not found. Use Android Studio: open android/ then Build > Build APK(s)."
        Write-Host "Or add Gradle wrapper (gradle wrapper) then run: cd android; .\gradlew.bat $($buildConfig.Task)"
        exit 1
    }
    if (Test-Path $apkOut) {
        Copy-Item $apkOut $copyTo -Force
        Write-Host "APK copied to: $copyTo"
        Write-Host "APK output: $apkOut"
    } else {
        Write-Host "Build may have failed. Check output above. Expected: $apkOut"
        exit 1
    }
} finally {
    Pop-Location
}
