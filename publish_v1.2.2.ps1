<#
.SYNOPSIS
    Publish TheDesiTadka v1.2.2 Release to GitHub
.DESCRIPTION
    Creates a GitHub release v1.2.2, uploads TheDesiTadka-release.apk,
    uploads signed-manifest.json, and verifies the release is live.
#>

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   TheDesiTadka v1.2.2 Release Publisher" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$RepoRoot = $PSScriptRoot
$repo = "DBiswas57/TheDesiTadka"
$tagName = "v1.2.2"
$releaseTitle = "TheDesiTadka v1.2.2"

# 1. Copy and Prepare APK Artifacts
$builtApk = Join-Path $RepoRoot "Mobile\app\build\outputs\apk\release\app-release.apk"
$releaseDir = Join-Path $RepoRoot "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
}

$apkPath = Join-Path $releaseDir "TheDesiTadka-release.apk"
$versionedApkPath = Join-Path $releaseDir "TheDesiTadka-v1.2.2-release.apk"

if (Test-Path $builtApk) {
    Copy-Item $builtApk $apkPath -Force
    Copy-Item $builtApk $versionedApkPath -Force
    Write-Host "[+] Copied fresh release APK to $apkPath and $versionedApkPath" -ForegroundColor Green
} elseif (-not (Test-Path $apkPath)) {
    Write-Error "[-] APK not found at $builtApk or $apkPath"
    exit 1
}

$fileHash = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash.ToLower()
Write-Host "[+] APK SHA-256: $fileHash" -ForegroundColor Green

# 2. Retrieve GitHub Token
Write-Host "`n[*] Retrieving GitHub credentials..." -ForegroundColor Yellow
$credInput = "protocol=https`nhost=github.com`nusername=DBiswas57"
$credOutput = $credInput | git credential fill
$token = ""
foreach ($line in ($credOutput -split "`n")) {
    if ($line.Trim().StartsWith("password=")) {
        $token = $line.Trim().Substring(9)
        break
    }
}

if (-not $token) {
    Write-Error "[-] Could not retrieve GitHub token from git credential manager."
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Accept"        = "application/vnd.github+json"
    "User-Agent"    = "TheDesiTadka-Publisher"
}

# Verify user
$user = Invoke-RestMethod -Uri "https://api.github.com/user" -Headers $headers -Method Get
Write-Host "[+] Authenticated as GitHub user: $($user.login)" -ForegroundColor Green

$releaseNotes = @"
TheDesiTadka Release v1.2.2

Major Playback & Playlist Updates:
- Seamless Boundary Auto-Pagination:
  - Reaching the end of a category or provider feed automatically fetches the next page in the background.
  - Appends new items to playlist and seamlessly plays the next video without interruption.
- Fixed Playlist Reset & 'No Content Left' Bug:
  - Resolved race condition where related items would overwrite loaded category/feed playlists.
  - Normalizes detail URLs across providers to prevent playlist matching drops.
  - Retains full loaded video playlists across Home and Category navigation.
- Next & Previous Navigation Enhancements:
  - Synchronized Next/Previous buttons and horizontal swipe gestures across PlayerScreen and DetailsScreen.
  - Full boundary handling: previous video disabled at index 0; next video dynamically queries next page if available.
- Admin Notice System v1.2.2:
  - Integrated in-app update notification broadcast to inform all users of updates and changes.

Package Verification:
- Package Name: com.thedesitadka.app
- Version Code: 23
- Version Name: 1.2.2
- SHA-256: $fileHash
"@

# 3. Create or Update GitHub Release
Write-Host "`n[*] Creating or updating release $tagName on GitHub..." -ForegroundColor Yellow
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tagName" -Headers $headers -Method Get
    Write-Host "[*] Found existing release: $($release.id)"
    $patchPayload = @{
        name = $releaseTitle
        body = $releaseNotes
    } | ConvertTo-Json
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$($release.id)" -Headers $headers -Method Patch -Body ([System.Text.Encoding]::UTF8.GetBytes($patchPayload)) -ContentType "application/json; charset=utf-8"
    Write-Host "[+] Updated release metadata for: $($release.id)" -ForegroundColor Green
} catch {
    $createPayload = @{
        tag_name         = $tagName
        target_commitish = "main"
        name             = $releaseTitle
        body             = $releaseNotes
        draft            = $false
        prerelease       = $false
    } | ConvertTo-Json

    $jsonBytes = [System.Text.Encoding]::UTF8.GetBytes($createPayload)
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Method Post -Body $jsonBytes -ContentType "application/json; charset=utf-8"
    Write-Host "[+] Created release: $($release.id) at $($release.html_url)" -ForegroundColor Green
}

$releaseId = $release.id

# 4. Upload APK Asset
$fileName = "TheDesiTadka-release.apk"
Write-Host "`n[*] Processing asset: $fileName..." -ForegroundColor Cyan

$existingAsset = $release.assets | Where-Object { $_.name -eq $fileName }
if ($existingAsset) {
    Write-Host "[*] Replacing existing asset $($existingAsset.id)..."
    Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/assets/$($existingAsset.id)" -Headers $headers -Method Delete
}

$uploadUri = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$fileName"
Write-Host "[*] Uploading $fileName ($([math]::Round((Get-Item $apkPath).Length / 1MB, 2)) MB)..."

$fileBytes = [System.IO.File]::ReadAllBytes($apkPath)
$uploadHeaders = @{
    "Authorization"  = "Bearer $token"
    "Accept"         = "application/vnd.github+json"
    "Content-Type"   = "application/vnd.android.package-archive"
    "Content-Length" = $fileBytes.Length
}

$uploaded = Invoke-RestMethod -Uri $uploadUri -Headers $uploadHeaders -Method Post -Body $fileBytes
Write-Host "[+] Uploaded: $($uploaded.name) -> $($uploaded.browser_download_url)" -ForegroundColor Green

# 5. Upload signed-manifest.json Asset
$manifestPath = Join-Path $RepoRoot "config-tools\signed-manifest.json"
if (Test-Path $manifestPath) {
    $manifestName = "signed-manifest.json"
    Write-Host "`n[*] Processing asset: $manifestName..." -ForegroundColor Cyan
    $existingManifest = $release.assets | Where-Object { $_.name -eq $manifestName }
    if ($existingManifest) {
        Write-Host "[*] Replacing existing asset $($existingManifest.id)..."
        Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/assets/$($existingManifest.id)" -Headers $headers -Method Delete
    }

    $manifestUploadUri = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$manifestName"
    $manifestBytes = [System.IO.File]::ReadAllBytes($manifestPath)
    $manifestHeaders = @{
        "Authorization"  = "Bearer $token"
        "Accept"         = "application/vnd.github+json"
        "Content-Type"   = "application/json"
        "Content-Length" = $manifestBytes.Length
    }

    $uploadedManifest = Invoke-RestMethod -Uri $manifestUploadUri -Headers $manifestHeaders -Method Post -Body $manifestBytes
    Write-Host "[+] Uploaded: $($uploadedManifest.name) -> $($uploadedManifest.browser_download_url)" -ForegroundColor Green
}

# 6. Verify release
Write-Host "`n[*] Verifying release is live..." -ForegroundColor Yellow
$verify = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tagName" -Headers $headers -Method Get
if ($verify.assets.Count -gt 0) {
    Write-Host "[+] Release verified: $($verify.assets.Count) asset(s) attached" -ForegroundColor Green
    foreach ($a in $verify.assets) {
        Write-Host "    - $($a.name): $($a.browser_download_url)"
    }
} else {
    Write-Warning "[-] No assets found on release $tagName"
}

# 7. Verify AppUpdateManager Endpoint
Write-Host "`n[*] Verifying latest release API endpoint for app update detection..." -ForegroundColor Yellow
$latest = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/latest" -Headers $headers -Method Get
Write-Host "[+] Latest release tag on GitHub: $($latest.tag_name)" -ForegroundColor Green
Write-Host "[+] Latest release name:           $($latest.name)" -ForegroundColor Green
Write-Host "[+] Latest release APK URL:        $(($latest.assets | Where-Object { $_.name -like '*.apk' }).browser_download_url)" -ForegroundColor Green

if ($latest.tag_name -eq $tagName) {
    Write-Host "`n[SUCCESS] App update detection verified! All v1.1.9, v1.2.0, and v1.2.1 users will now detect v1.2.2 update." -ForegroundColor Green
} else {
    Write-Warning "[*] Latest tag is $($latest.tag_name) while target is $tagName"
}
