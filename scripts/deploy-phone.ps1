# Fast local Android phone deploy: build optimized APK, install, and launch.
# This is the iteration path for repeated device testing between code changes.
# For stricter local QA before commit/publish, keep using:
#   .\scripts\build-apk.ps1
#   cd android && .\gradlew.bat :app:lintDebug

param(
    [ValidateSet("localRelease", "debug", "release")]
    [string]$BuildType = "localRelease",
    [switch]$SkipBuild,
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"
$buildScript = Join-Path $PSScriptRoot "build-apk.ps1"
if (-not (Test-Path $buildScript)) {
    Write-Error "build-apk.ps1 not found next to deploy-phone.ps1"
}

$buildArgs = @{
    BuildType = $BuildType
    Fast = $true
    Install = $true
}

if ($SkipBuild) {
    $buildArgs.SkipBuild = $true
}

if (-not $NoLaunch) {
    $buildArgs.Launch = $true
}

& $buildScript @buildArgs
