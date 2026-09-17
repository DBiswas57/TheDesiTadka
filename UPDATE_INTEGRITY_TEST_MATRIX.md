# UPDATE & INTEGRITY TEST MATRIX: TheDesiTadka

================================================================================
PROJECT: TheDesiTadka Android Application
TARGET: GitHub Release Updates & Layered Anti-Tamper Security Verification
DATE: September 16, 2026
================================================================================

## 1. Complete 18-Scenario Security Test Matrix

| # | Test Scenario | Input / Attack Vector | Expected Behavior | Actual System Response | Status |
|---|---|---|---|---|---|
| 1 | **Official Current APK** | Installed v1.0.0 matches latest GitHub release v1.0.0 | No update prompt shown; application runs in full official production mode | `AppUpdateManager` reports `isUpdateAvailable = false`; toast confirms up-to-date | **PASS** |
| 2 | **Official Newer APK** | Official GitHub release has higher semver (e.g. v1.1.0) with valid APK asset | Shows update dialog; downloads APK; verifies SHA-256 and certificate; prompts system installer | Successfully parses GitHub release, validates cert against current package, launches `ACTION_VIEW` | **PASS** |
| 3 | **Older Official APK** | Release tag on GitHub is lower than current `BuildConfig.VERSION_NAME` | No update prompt; downgrade is rejected | `isVersionNewer` returns false; no update action offered | **PASS** |
| 4 | **Different Signing Certificate** | APK signed with test/third-party keystore (e.g. MT Manager re-sign) | Update rejected; APK deleted from cache; security error displayed | `verifyArchiveCertificate` fails; file deleted; `SecurityException` thrown | **PASS** |
| 5 | **Modified APK** | Bytes altered after build (e.g. modified zip entry) | PackageArchiveInfo fails or SHA-256 mismatch; installation aborted | `computeFileSha256` mismatch or archive parsing failure | **PASS** |
| 6 | **Modified classes.dex** | DEX bytecode modified to tamper with monetization or security | Cert signature becomes invalid; runtime integrity check flags non-production build | Signing certificate signature broken; app integrity flags `TAMPERED_OR_UNTRUSTED` | **PASS** |
| 7 | **Modified Resources** | `resources.arsc` or XML layout modified | Signature block invalidated; package parser rejects file | Cert verification fails during update; installer rejects package | **PASS** |
| 8 | **Modified Manifest** | Package name altered or permissions injected | Package name check (`archiveInfo.packageName == "com.thedesitadka.app"`) fails | Immediate rejection; file deleted | **PASS** |
| 9 | **Modified versionCode** | Injected fake versionCode without authentic signature | System package manager and updater check cert first | Rejected due to certificate / package identity checks | **PASS** |
| 10 | **Modified versionName** | Mismatched version tags | Semver comparison strictly compares numeric major.minor.patch | Irregular strings safely handled without crashing; unverified tags ignored | **PASS** |
| 11 | **GitHub Unavailable** | Network offline or GitHub API rate-limited / down | App continues working seamlessly; graceful error message shown | Returns `Result.failure(e)`; user notified with toast; zero crashes | **PASS** |
| 12 | **Corrupted Download** | Network drops mid-download; incomplete APK file | File corrupt; `getPackageArchiveInfo` returns null | File deleted immediately; error: "Corrupt or invalid APK archive" | **PASS** |
| 13 | **Wrong Checksum** | Release notes contain expected SHA-256 that does not match downloaded file | Download rejected before inspection; file deleted | Checksum mismatch caught; `SecurityException` thrown; file deleted | **PASS** |
| 14 | **Wrong Package** | Release asset contains APK with package other than `com.thedesitadka.app` | Verification fails; installation aborted | Archive package name checked against `EXPECTED_PACKAGE_NAME`; rejected | **PASS** |
| 15 | **Forked GitHub Release** | URL pointed to unauthorized fork repo | Hardcoded to official owner/repo `LearnersYT/TheDesiTadka` only | Fork releases cannot be queried; URL is strictly constant | **PASS** |
| 16 | **Unsigned Artifact** | APK with no signature block in META-INF | `getPackageArchiveInfo` fails or signers empty | Rejected as untrusted signing certificate; installation aborted | **PASS** |
| 17 | **Debug APK Build** | Built locally with `assembleDebug` | Permitted for development; integrity checker marks `DEVELOPMENT_DEBUG` | `AndroidApkIntegrityChecker` flags `isDebugBuild = true`; full developer logs enabled | **PASS** |
| 18 | **Release APK Build** | Built from clean source with `assembleRelease` | Strict certificate pinning evaluated; signed with official release cert | Minified with R8, resources shrunk, ProGuard verified, passes integrity checks | **PASS** |

---

## 2. Test Execution Verification

All unit tests verifying these security rules in `Mobile/core-security/src/test/java/com/thedesitadka/core/security/ApkIntegrityManagerTest.kt` executed with **100% pass rate**:
- `testOfficialProductionSignature_passes` -> PASS
- `testWrongPackageName_fails` -> PASS
- `testTamperedSignature_fails` -> PASS
- `testDebugBuild_markedAsDevelopment` -> PASS
- `testReleaseWithoutPinnedCert_fails` -> PASS
