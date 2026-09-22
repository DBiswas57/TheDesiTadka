<#
.SYNOPSIS
    Publish TheDesiTadka v1.1.4 Release to GitHub
.DESCRIPTION
    Creates a GitHub release v1.1.4, uploads TheDesiTadka-release.apk,
    uploads signed-manifest.json, and verifies the release is live.
#>

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   TheDesiTadka v1.1.4 Release Publisher" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$RepoRoot = $PSScriptRoot
$repo = "DBiswas57/TheDesiTadka"
$tagName = "v1.1.4"
$releaseTitle = "TheDesiTadka v1.1.4"
$releaseNotes = @"
TheDesiTadka Release v1.1.4

Changes:
- Added universal integration for first 8 recommended streaming tube networks (XNXX, XVideos, PornHub, xHamster, RedTube, YouPorn, Tube8, FreeOnes Tube)
- Implemented adaptive HLS master playlist (.m3u8) and MP4 direct stream extraction across all new providers
- Hardened top-right square category button with graceful error handling and non-blocking notification
- Enhanced navigation state preservation restoring scroll position and loaded items upon back navigation
- Upgraded provider manifest to version 161 with 56 active providers
- Verified streaming playback and download resolution across all supported networks
- Core engine stability and network resilience optimizations

Package Verification:
- Package Name: com.thedesitadka.app
- Version Code: 15
- Version Name: 1.1.4
- SHA-256 Checksum: FCCB97D4DCEB5E37EE16BA8AA37124F62D2BD8A400D5BB14CDB1C8B52B2F5955
"@

# 1. Retrieve GitHub Token
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

# 2. Create or Update GitHub Release
Write-Host "`n[*] Creating release $tagName on GitHub..." -ForegroundColor Yellow
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tagName" -Headers $headers -Method Get
    Write-Host "[*] Found existing release: $($release.id)"
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

# 3. Upload APK Asset
$apkPath = Join-Path $RepoRoot "release\TheDesiTadka-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "[-] APK not found at $apkPath"
    exit 1
}

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

# 4. Upload signed-manifest.json Asset
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

# 5. Verify release
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

# 6. Verify AppUpdateManager Endpoint
Write-Host "`n[*] Verifying latest release API endpoint for app update detection..." -ForegroundColor Yellow
$latest = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/latest" -Headers $headers -Method Get
Write-Host "[+] Latest release tag on GitHub: $($latest.tag_name)" -ForegroundColor Green
Write-Host "[+] Latest release name:           $($latest.name)" -ForegroundColor Green
Write-Host "[+] Latest release APK URL:        $(($latest.assets | Where-Object { $_.name -like '*.apk' }).browser_download_url)" -ForegroundColor Green

if ($latest.tag_name -eq $tagName) {
    Write-Host "`n[SUCCESS] App update detection verified! Existing users will detect v$tagName update." -ForegroundColor Green
} else {
    Write-Warning "[*] Latest tag is $($latest.tag_name) while target is $tagName"
}
