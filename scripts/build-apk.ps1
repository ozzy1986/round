# Build an Android APK and optionally install/launch it on the phone.
# Default target is deterministic localRelease: non-debuggable, R8/resource
# shrinking enabled, debug-signed for repeatable local QA builds.
# Use -Fast for repeated phone deploys: keep Gradle warm and skip
# lintVitalLocalRelease because lintDebug remains the explicit QA gate.
# Run from project root: .\scripts\build-apk.ps1

param(
    [ValidateSet("localRelease", "release", "debug")]
    [string]$BuildType = "localRelease",
    [switch]$Fast,
    [switch]$Install,
    [switch]$Launch,
    [switch]$SkipBuild
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
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Ensure-AdbDevice([string]$AdbPath) {
    if (-not (Test-Path $AdbPath)) {
        Write-Error "adb.exe not found at $AdbPath"
    }
    $devicesOutput = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) {
        Write-Error "adb devices failed."
    }
    $connectedDevices = @(
        $devicesOutput |
            Select-Object -Skip 1 |
            Where-Object { $_.Trim() -and $_ -match "\sdevice$" }
    )
    if ($connectedDevices.Count -eq 0) {
        Write-Error "No authorized Android device connected. Check USB, authorization, and adb devices."
    }
}

Push-Location $androidDir
try {
    if (-not $SkipBuild) {
        if (Test-Path ".\gradlew.bat") {
            Write-Host "Building $BuildType APK..."
            $gradleArgs = @($buildConfig.Task)
            if ($Fast) {
                if ($BuildType -eq "localRelease") {
                    Write-Host "Fast localRelease build: reusing Gradle daemon/cache and skipping lintVitalLocalRelease."
                    $gradleArgs = @("-x", "lintVitalLocalRelease", $buildConfig.Task)
                }
            } elseif ($BuildType -ne "debug") {
                & .\gradlew.bat --stop | Out-Host
                $gradleArgs = @(
                    "--no-daemon",
                    "--no-parallel",
                    "-Dkotlin.compiler.execution.strategy=in-process",
                    $buildConfig.Task
                )
            }
            & .\gradlew.bat @gradleArgs
            if ($LASTEXITCODE -ne 0) {
                exit $LASTEXITCODE
            }
        } else {
            Write-Host "Gradle wrapper not found. Use Android Studio: open android/ then Build > Build APK(s)."
            Write-Host "Or add Gradle wrapper (gradle wrapper) then run: cd android; .\gradlew.bat $($buildConfig.Task)"
            exit 1
        }
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

if ($Install -or $Launch) {
    Ensure-AdbDevice -AdbPath $adbPath
}

if ($Install) {
    Write-Host "Installing APK on connected phone..."
    & $adbPath install -r $apkOut | Out-Host
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($Launch) {
    Write-Host "Launching app..."
    & $adbPath shell am start -n "com.raund.app/.MainActivity" | Out-Host
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
