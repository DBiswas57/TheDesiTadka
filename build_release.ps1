# StreamHub Automated Release Build Script (PowerShell)
# ======================================================
# Builds, tests, and packages release APK and AAB bundle with R8 minification.

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   StreamHub: Automated Production Release Build" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Environment Verification
$JdkPath = "C:\Program Files\Android\openjdk\jdk-21.0.8"
if (Test-Path $JdkPath) {
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $JdkPath, "Process")
    Write-Host "[+] Set JAVA_HOME -> $JdkPath" -ForegroundColor Green
} else {
    Write-Host "[*] Using system JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Yellow
}

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MobileDir = Join-Path $RepoRoot "Mobile"

if (-not (Test-Path $MobileDir)) {
    Write-Error "[-] Mobile directory not found at $MobileDir"
    exit 1
}

# 2. Run Gradle Build & Tests
Write-Host "`n[*] Starting Clean, Test & Release Assembly..." -ForegroundColor Yellow
Set-Location $MobileDir

try {
    .\gradlew.bat clean test :app:assembleRelease :app:bundleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Error "[-] Gradle build failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
} finally {
    Set-Location $RepoRoot
}

# 3. Locate and Report Artifacts
$ReleaseApkDir = Join-Path $MobileDir "app\build\outputs\apk\release"
$ReleaseBundleDir = Join-Path $MobileDir "app\build\outputs\bundle\release"

Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host "   Release Artifacts Generated Successfully" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

if (Test-Path $ReleaseApkDir) {
    $DestReleaseDir = Join-Path $RepoRoot "release"
    if (-not (Test-Path $DestReleaseDir)) {
        New-Item -ItemType Directory -Path $DestReleaseDir -Force | Out-Null
    }

    $SourceApk = Join-Path $ReleaseApkDir "app-release.apk"
    if (Test-Path $SourceApk) {
        Copy-Item -Path $SourceApk -Destination (Join-Path $DestReleaseDir "TheDesiTadka-release.apk") -Force
        Copy-Item -Path $SourceApk -Destination (Join-Path $DestReleaseDir "TheDesiTadka-v1.0.5-release.apk") -Force
        Write-Host "[+] Copied release APKs to: $DestReleaseDir" -ForegroundColor Cyan
    }

    Get-ChildItem -Path $ReleaseApkDir -Filter "*.apk" | ForEach-Object {
        $Hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        $SizeMB = [math]::Round($_.Length / 1MB, 2)
        Write-Host "[+] APK: $($_.Name) ($SizeMB MB)" -ForegroundColor Green
        Write-Host "    Path:   $($_.FullName)"
        Write-Host "    SHA256: $Hash"
    }
}

if (Test-Path $ReleaseBundleDir) {
    Get-ChildItem -Path $ReleaseBundleDir -Filter "*.aab" | ForEach-Object {
        $Hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        $SizeMB = [math]::Round($_.Length / 1MB, 2)
        Write-Host "[+] Bundle: $($_.Name) ($SizeMB MB)" -ForegroundColor Green
        Write-Host "    Path:   $($_.FullName)"
        Write-Host "    SHA256: $Hash"
    }
}

Write-Host "`n[+] Build completed successfully!" -ForegroundColor Green
