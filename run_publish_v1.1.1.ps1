<#
.SYNOPSIS
    Publish TheDesiTadka v1.1.1 Release to GitHub
.DESCRIPTION
    Creates a GitHub release v1.1.1, uploads TheDesiTadka-release.apk,
    and verifies the release is live.
#>

$ErrorActionPreference = "Stop"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   TheDesiTadka v1.1.1 Release Publisher" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$RepoRoot = $PSScriptRoot
$repo = "DBiswas57/TheDesiTadka"
$tagName = "v1.1.1"
$releaseTitle = "TheDesiTadka v1.1.1"
$releaseNotes = @"
TheDesiTadka Release v1.1.1

Changes:
- Fixed video playback for ChiggyWiggy.com (improved stream resolution and referer handling)
- Fixed SxyPrn.com content loading (desktop user-agent enforcement, Cloudflare clearance fix)
- Fixed KamaBaba download issues (updated entry point to mykamababa.com, added proper referer headers)
- Improved update installation flow (handles install permission prompts and re-triggers install after granting permission)
- Enhanced media stream resolution (added videojs/JSON source parsing and script tag exclusion)
- Core engine optimizations and stability improvements

Package Verification:
- Package Name: com.thedesitadka.app
- Version Code: 12
- Version Name: 1.1.1
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

# 4. Verify release
Write-Host "`n[*] Verifying release is live..." -ForegroundColor Yellow
$verify = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tagName" -Headers $headers -Method Get
if ($verify.assets.Count -gt 0) {
    Write-Host "[+] Release verified: $($verify.assets.Count) asset(s) attached" -ForegroundColor Green
} else {
    Write-Host "[!] Warning: No assets found on release" -ForegroundColor Yellow
}

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "   Release $tagName Published Successfully!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host "Release URL: $($release.html_url)"
Write-Host "Download: $($uploaded.browser_download_url)"
