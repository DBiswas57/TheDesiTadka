# APK INTEGRITY & ANTI-TAMPER DESIGN: TheDesiTadka

================================================================================
PROJECT: TheDesiTadka Android Application
TARGET: Layered Production Application Integrity Verification
STATUS: IMPLEMENTED & VERIFIED
================================================================================

## 1. Problem Statement & Fallacy of Static Whole-APK Hashing

Relying solely on a hardcoded whole-APK SHA-256 hash to enforce application integrity is technically flawed in Android production workflows:
1. **Build Non-Determinism**: ZIP timestamps, APK Signature Scheme v2/v3 block alignments, and toolchain versions alter the overall APK hash even when bytecode is identical.
2. **Brittle Updates**: Every official version update would invalidate the hardcoded hash before the update is even applied, leading to broken self-verification.

Therefore, `TheDesiTadka` implements **Layered Cryptographic Identity & Certificate Pinning**, which Android's security architecture natively enforces.

---

## 2. Layered Integrity Architecture

```
[Installed APK Package]
        |
        v
Layer 1: Package Identity Check (com.thedesitadka.app)
        |
        v
Layer 2: Build Variant Discrimination (BuildConfig.DEBUG vs RELEASE)
        |
        v
Layer 3: Android Signing Certificate Extraction (SigningInfo / SigningCertificates)
        |
        v
Layer 4: Pinned Production Certificate SHA-256 Digest Validation
        |
        v
Layer 5: Safe Degraded State (Non-Destructive, Zero Crash Loops)
```

### 2.1 Layer 1 — Package Identity Verification
- Enforces that `context.packageName` matches `ApkIntegrityManager.EXPECTED_PACKAGE_NAME` (`"com.thedesitadka.app"`).
- Rejects repackaged clones attempting to run under alternate package IDs (e.g. `com.mod.thedesitadka`).

### 2.2 Layer 2 — Build Variant Discrimination
- Distinguishes development/test builds from official release distributions:
  - When `isDebugBuild == true`: Integrity verifier marks the state as `DEVELOPMENT_DEBUG`, allowing developer iteration and debugging while logging diagnostic notices.
  - When `isDebugBuild == false`: The verifier strictly enforces production certificate pinning.

### 2.3 Layer 3 — Cryptographic Signing Certificate Extraction
- Implemented in `AndroidApkIntegrityChecker.kt`:
  - On Android 9.0 (API 28) and above: Queries `packageManager.getPackageInfo(..., PackageManager.GET_SIGNING_CERTIFICATES)` and reads `signingInfo.apkContentsSigners`.
  - On legacy platforms (API 26-27): Queries `PackageManager.GET_SIGNATURES`.
  - Computes the SHA-256 digest of the X.509 signing certificate.

### 2.4 Layer 4 — Pinned Production Certificate Verification
- The computed certificate SHA-256 fingerprint is verified against `ApkIntegrityManager.OFFICIAL_RELEASE_CERT_SHA256`.
- If an attacker uses tools like MT Manager, APK Editor, or `apksigner` with their own key, the signature verification immediately fails with `TAMPERED_OR_UNTRUSTED`.

### 2.5 Layer 5 — Graceful Failure & Safe Degraded State
In accordance with production safety requirements:
- **No Crash Loops**: An integrity failure does not force an infinite crash loop.
- **No User Data Destruction**: User bookmarks, watch history, and downloaded media files are never wiped or destroyed.
- **Safe Mode**: The application marks the build as unverified (`IntegrityVerdict.TAMPERED_OR_UNTRUSTED`), disables publisher monetization reporting to protect publisher account standing from fraudulent attribution, and warns the user in the Diagnostics screen.

---

## 3. GitHub Update Integrity Pipeline

When an update is downloaded via `AppUpdateManager`:
1. **SHA-256 Checksum Validation**: If official release notes contain a 64-character hex hash, the downloaded file's SHA-256 is validated prior to archive parsing.
2. **Archive Parsing**: `packageManager.getPackageArchiveInfo(...)` verifies archive structural validity.
3. **Package Name Verification**: Ensures `archiveInfo.packageName == "com.thedesitadka.app"`.
4. **Certificate Match Verification**: Extracts signing certificates from the archive and verifies that they bit-for-bit match the currently installed app's signing certificates.
5. **Atomic File Deletion on Failure**: Any failed verification immediately wipes the downloaded APK from the application's cache directory before `Intent.ACTION_VIEW` can ever be triggered.
