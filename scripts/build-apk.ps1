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
$buildLockTimeoutSeconds = 15
$deviceTimeoutSeconds = 30
$installTimeoutSeconds = 90
$launchTimeoutSeconds = 15
$buildTimeoutSeconds = if ($BuildType -eq "debug") {
    600
} elseif ($Fast) {
    900
} else {
    1200
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string[]]$ArgumentList = @(),
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [int]$TimeoutSeconds = 0,
        [Parameter(Mandatory = $true)]
        [string]$TimeoutMessage
    )

    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -NoNewWindow `
        -PassThru
    try {
        $completed = if ($TimeoutSeconds -gt 0) {
            $process.WaitForExit($TimeoutSeconds * 1000)
        } else {
            $process.WaitForExit()
            $true
        }
        if (-not $completed) {
            Stop-Process -Id $process.Id -Force
            throw $TimeoutMessage
        }
        $process.Refresh()
        $exitCode = $null
        try {
            $exitCode = $process.ExitCode
        } catch {
            $exitCode = 0
        }
        if ($null -eq $exitCode) {
            $exitCode = 0
        }
        if ($exitCode -ne 0) {
            throw "Command failed: $FilePath $($ArgumentList -join ' ') (exit code $exitCode)."
        }
    } finally {
        if ($process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
        }
        if ($process) {
            $process.Dispose()
        }
    }
}

function Acquire-AndroidBuildLock([int]$TimeoutSeconds) {
    $script:androidBuildMutex = New-Object System.Threading.Mutex($false, "Global\raund-android-build")
    $script:androidBuildLockTaken = $script:androidBuildMutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
    if (-not $script:androidBuildLockTaken) {
        throw "Another Raund Android build/deploy is already running. Wait for it to finish and retry."
    }
}

function Release-AndroidBuildLock {
    if ($script:androidBuildMutex) {
        if ($script:androidBuildLockTaken) {
            $script:androidBuildMutex.ReleaseMutex()
        }
        $script:androidBuildMutex.Dispose()
    }
}

function Ensure-AdbDevice([string]$AdbPath, [int]$DeviceTimeoutSeconds) {
    if (-not (Test-Path $AdbPath)) {
        throw "adb.exe not found at $AdbPath"
    }
    Invoke-NativeCommand `
        -FilePath $AdbPath `
        -ArgumentList @("start-server") `
        -WorkingDirectory $projectRoot `
        -TimeoutSeconds 15 `
        -TimeoutMessage "adb start-server timed out."
    $devicesOutput = & $AdbPath devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed."
    }
    $deviceLines = @(
        $devicesOutput |
            Select-Object -Skip 1 |
            Where-Object { $_.Trim() }
    )
    $unauthorizedDevices = @(
        $deviceLines |
            Where-Object { $_ -match "\sunauthorized$" }
    )
    if ($unauthorizedDevices.Count -gt 0) {
        throw "Android device is connected but unauthorized. Unlock it and accept the USB debugging prompt."
    }
    $offlineDevices = @(
        $deviceLines |
            Where-Object { $_ -match "\soffline$" }
    )
    if ($offlineDevices.Count -gt 0) {
        throw "Android device is offline. Reconnect USB or restart adb before deploying."
    }
    $connectedDevices = @(
        $deviceLines |
            Where-Object { $_.Trim() -and $_ -match "\sdevice$" }
    )
    if ($connectedDevices.Count -eq 0) {
        throw "No authorized Android device connected. Check USB, authorization, and adb devices."
    }
    Invoke-NativeCommand `
        -FilePath $AdbPath `
        -ArgumentList @("wait-for-device") `
        -WorkingDirectory $projectRoot `
        -TimeoutSeconds $DeviceTimeoutSeconds `
        -TimeoutMessage "Timed out waiting for the Android device."
}

try {
    Acquire-AndroidBuildLock -TimeoutSeconds $buildLockTimeoutSeconds
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
                    Invoke-NativeCommand `
                        -FilePath ".\gradlew.bat" `
                        -ArgumentList @("--stop") `
                        -WorkingDirectory $androidDir `
                        -TimeoutSeconds 30 `
                        -TimeoutMessage "Gradle stop timed out."
                    $gradleArgs = @(
                        "--no-daemon",
                        "--no-parallel",
                        "-Dkotlin.compiler.execution.strategy=in-process",
                        $buildConfig.Task
                    )
                }
                Invoke-NativeCommand `
                    -FilePath ".\gradlew.bat" `
                    -ArgumentList $gradleArgs `
                    -WorkingDirectory $androidDir `
                    -TimeoutSeconds $buildTimeoutSeconds `
                    -TimeoutMessage "Gradle build timed out. If Android Studio or another deploy is building this project, let it finish first."
            } else {
                Write-Host "Gradle wrapper not found. Use Android Studio: open android/ then Build > Build APK(s)."
                Write-Host "Or add Gradle wrapper (gradle wrapper) then run: cd android; .\gradlew.bat $($buildConfig.Task)"
                throw "Gradle wrapper not found."
            }
        }
        if (Test-Path $apkOut) {
            Copy-Item $apkOut $copyTo -Force
            Write-Host "APK copied to: $copyTo"
            Write-Host "APK output: $apkOut"
        } else {
            throw "Build may have failed. Expected APK not found: $apkOut"
        }
    } finally {
        Pop-Location
    }
} finally {
    Release-AndroidBuildLock
}

if ($Install -or $Launch) {
    Ensure-AdbDevice -AdbPath $adbPath -DeviceTimeoutSeconds $deviceTimeoutSeconds
}

if ($Install) {
    Write-Host "Installing APK on connected phone..."
    Invoke-NativeCommand `
        -FilePath $adbPath `
        -ArgumentList @("install", "-r", $apkOut) `
        -WorkingDirectory $projectRoot `
        -TimeoutSeconds $installTimeoutSeconds `
        -TimeoutMessage "APK install timed out. Reconnect the phone and verify USB debugging is active."
}

if ($Launch) {
    Write-Host "Launching app..."
    Invoke-NativeCommand `
        -FilePath $adbPath `
        -ArgumentList @("shell", "am", "start", "-n", "com.raund.app/.MainActivity") `
        -WorkingDirectory $projectRoot `
        -TimeoutSeconds $launchTimeoutSeconds `
        -TimeoutMessage "App launch timed out."
}
