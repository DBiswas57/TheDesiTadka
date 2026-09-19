# Production Release v1.0.8 Publisher & Verifier
$ErrorActionPreference = "Stop"

$Notes = @"
TheDesiTadka Release v1.0.8

New Features and Enhancements:
- Added 18 English media sites with full taxonomy (categories, channels, models, tags, and streams)
- Fixed KamaBaba site integration by updating domain resolution, selectors, and referer headers
- Fixed XNXX site integration with todays-selection feed routing, data-mzl image extraction, and HTML5 video playback
- Enhanced Home site selection system with first-launch selection gate
- Added contributing source indicators and quick-customization controls directly on Home screen
- Synchronized Home site selection seamlessly with Settings in real-time
- Verified full media catalog accessibility across all 47 providers in Media Providers view

Package Verification:
- Package Name: com.thedesitadka.app
- Version Code: 9
- Version Name: 1.0.8
- Integrity: Validated with clean automated unit tests and R8 minification
"@

Write-Host "[*] Executing publish_release.ps1 for v1.0.8..." -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File .\publish_release.ps1 -NewVersion "1.0.8" -ReleaseNotes $Notes

if ($LASTEXITCODE -ne 0) {
    Write-Error "[-] Publishing script failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "`n[*] Verifying latest release on GitHub Releases API..." -ForegroundColor Yellow
$apiUrl = "https://api.github.com/repos/DBiswas57/TheDesiTadka/releases/latest"
$latest = Invoke-RestMethod -Uri $apiUrl -Method Get -Headers @{ "User-Agent" = "TheDesiTadka-Verifier" }

Write-Host "[+] Latest Tag on GitHub: $($latest.tag_name)" -ForegroundColor Green
Write-Host "[+] Release Title: $($latest.name)" -ForegroundColor Green
Write-Host "[+] Published At: $($latest.published_at)" -ForegroundColor Green
Write-Host "[+] Assets uploaded:" -ForegroundColor Green
foreach ($asset in $latest.assets) {
    Write-Host "    - $($asset.name) ($([math]::Round($asset.size / 1MB, 2)) MB) -> $($asset.browser_download_url)" -ForegroundColor Cyan
}

if ($latest.tag_name -eq "v1.0.8") {
    Write-Host "`n[SUCCESS] TheDesiTadka v1.0.8 is live and ready for end-user update notifications!" -ForegroundColor Green
} else {
    Write-Warning "[!] Expected tag v1.0.8 but found $($latest.tag_name)"
}
