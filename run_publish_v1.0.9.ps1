# Production Release v1.0.9 Publisher & Verifier
$ErrorActionPreference = "Stop"

$Notes = @"
TheDesiTadka Release v1.0.9

New Features and Enhancements:
- Added Extensible Video Host Resolver Plugin Engine in Provider Core
- Implemented Vixeo.io resolver plugin for embedded player iframe decryption and direct stream extraction
- Fixed watchxxxfree.xyz feed pagination and filtered duplicate top carousel slides from content listing
- Synchronized application package identity and unified debug and release builds
- Optimized automated in-app update checks and integrity verification
- Enhanced download manager stream resolution and playback stability

Package Verification:
- Package Name: com.thedesitadka.app
- Version Code: 10
- Version Name: 1.0.9
- Release SHA256: 06F52DFC6FB85836F1980E3222F79522F8AAF5711F9AA1E87BAC48B74B851A70
"@

Write-Host "[*] Executing publish_release.ps1 for v1.0.9..." -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File .\publish_release.ps1 -NewVersion "1.0.9" -ReleaseNotes $Notes

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

if ($latest.tag_name -eq "v1.0.9") {
    Write-Host "`n[SUCCESS] TheDesiTadka v1.0.9 is live and ready for end-user update notifications!" -ForegroundColor Green
} else {
    Write-Warning "[!] Expected tag v1.0.9 but found $($latest.tag_name)"
}
