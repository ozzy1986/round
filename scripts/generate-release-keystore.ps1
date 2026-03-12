# Generate release keystore for Round (Raund) Android app and configure local.properties.
# Run from repo root (e.g. d:\laragon\www\raund). Requires JAVA_HOME set (e.g. Android Studio JBR or JDK).
# The keystore is created at android/keystore/raund-release.jks (android/keystore/ is in .gitignore).

$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot + "\.."
$androidDir = Join-Path $repoRoot "android"
$keystoreDir = Join-Path $androidDir "keystore"
$keystorePath = Join-Path $keystoreDir "raund-release.jks"
$localPropsPath = Join-Path $androidDir "local.properties"

$keytool = $env:JAVA_HOME
if (-not $keytool) {
    Write-Error "JAVA_HOME is not set. Set it to your JDK or Android Studio JBR (e.g. C:\Program Files\Android\Android Studio\jbr) and run again."
}
$keytool = Join-Path $keytool "bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    Write-Error "keytool not found at $keytool. Set JAVA_HOME to a valid JDK."
}

# Relative path from app module project dir (android/app) to keystore.
# Use forward slashes so Gradle/Java properties do not misinterpret backslashes.
$storeFileRelative = "../keystore/raund-release.jks"

# Generate random password (alphanumeric, 16 chars)
Add-Type -AssemblyName 'System.Web'
$password = [System.Web.Security.Membership]::GeneratePassword(16, 4) -replace '[^a-zA-Z0-9]', 'x'

New-Item -ItemType Directory -Path $keystoreDir -Force | Out-Null

# -dname CN=Round, OU=App, O=Round, L=City, ST=State, C=US for non-interactive
& $keytool -genkeypair -v -keystore $keystorePath -keyalg RSA -keysize 2048 -validity 10000 `
    -alias raund -storepass $password -keypass $password `
    -dname "CN=Round, OU=App, O=Round, L=City, ST=State, C=US"

if (-not (Test-Path $keystorePath)) {
    Write-Error "Keystore was not created."
}

# Merge into local.properties: keep existing lines, add or replace RELEASE_* lines
$lines = @()
if (Test-Path $localPropsPath) {
    $lines = Get-Content $localPropsPath
}
$releaseKeys = @{
    "RELEASE_STORE_FILE"    = $storeFileRelative
    "RELEASE_STORE_PASSWORD" = $password
    "RELEASE_KEY_ALIAS"     = "raund"
    "RELEASE_KEY_PASSWORD"  = $password
}
$seen = @{}
$newLines = foreach ($line in $lines) {
    $key = ($line -split "=", 2)[0].Trim()
    if ($releaseKeys.ContainsKey($key)) {
        $seen[$key] = $true
        "$key=$($releaseKeys[$key])"
    } else {
        $line
    }
}
foreach ($k in $releaseKeys.Keys) {
    if (-not $seen[$k]) {
        $newLines += "$k=$($releaseKeys[$k])"
    }
}
$newLines | Set-Content $localPropsPath -Encoding UTF8

Write-Host "Created $keystorePath and updated $localPropsPath with release signing config."
Write-Host "Back up the keystore and passwords securely; you need them for all future releases."
