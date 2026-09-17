# StreamHub: Secure Remote-Config Streaming Aggregator for Android

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20(API%2026%2B)-blue.svg)]()
[![Security](https://img.shields.io/badge/security-Ed25519%20%7C%20SSRF%20Guarded-success.svg)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple.svg)]()
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-deepgreen.svg)]()

StreamHub is a production-grade, modular Android application designed to aggregate and present **only legal, authorized, and publicly accessible video sources** via cryptographically signed remote configuration.

---

## 🎯 Architecture Philosophy

Traditional scraping and media aggregator apps suffer from three fatal flaws:
1. **Fragility**: Whenever a remote site updates its layout, the app breaks and requires an APK recompile and store resubmission.
2. **Security Vulnerability**: Attempting dynamic scripting (e.g. downloading DEX files or evaluating JavaScript) violates Google Play store policies and introduces severe remote code execution (RCE) vectors.
3. **Legal & DRM Infringement**: Attempting to bypass access controls or scrape DRM-protected feeds violates copyright standards and store policies.

**StreamHub solves these completely:**
- **Zero Arbitrary Executable Code**: Remote configurations are **strictly declarative data DSL** (JSON selectors, endpoint templates, capability masks). No DEX, eval, or runtime scripts are permitted.
- **Cryptographic Ed25519 Verification**: Every remote configuration payload must be signed with an Ed25519 private key held strictly in secure deployment infrastructure. The app verifies signatures using an embedded public key before activation.
- **Autonomous Provider Life-Cycle**: Adding or modifying provider endpoints requires zero APK rebuilds. If a site changes its DOM, a simple JSON update published to the CDN restores functionality instantly.
- **Strict SSRF & Network Hardening**: Built-in network-level filters reject RFC 1918 private subnets, loopbacks, link-local addresses, and cloud instance metadata targets (e.g., AWS/GCP `169.254.169.254`).

---

## 📁 Repository Structure

```
TheDesiTadka/
├── Mobile/                         # Android Studio Multi-Module Project
│   ├── app/                        # Main Application, Jetpack Compose UI, Room DB, Media3, WorkManager
│   ├── core-model/                 # Domain entities (VideoItem, ProviderConfig, StreamHubError)
│   ├── core-security/              # Ed25519 verification, SSRF defense, Logger redaction
│   ├── core-network/               # Hardened OkHttp client, SecurityInterceptor, timeout/size limits
│   ├── core-config/                # Manifest validation, atomic cache rotation, rollback manager
│   ├── provider-engine/            # ProviderAdapter system (HTML Jsoup, WordPress REST, RSS, JSON API)
│   └── gradle/                     # Gradle wrapper 8.9 & libs.versions.toml
├── config-tools/                   # Configuration Management & Signing CLI
│   ├── schema/                     # JSON Schema draft 2020-12 definition
│   ├── sign_config.py              # Canonical JSON + Ed25519 signing utility
│   ├── validate_config.py          # Security and schema validator
│   └── sample-manifest.json        # Production sample manifest for fry99, masa49 & public domain
├── build_release.ps1               # Automated release build script (PowerShell)
├── build_release.bat               # Automated release build script (Batch)
├── ARCHITECTURE.md                 # In-depth architectural blueprint
├── SECURITY.md                     # Security controls & defense-in-depth model
├── PROVIDER_DEVELOPMENT.md         # Guide for declaring new provider adapters
├── CONFIGURATION_GUIDE.md          # Operations guide for CDN hosting & manifest signing
├── TEST_REPORT.md                  # Automated & integration test results
├── RELEASE_CHECKLIST.md            # Pre-flight release verification
└── THREAT_MODEL.md                 # STRIDE threat analysis and mitigations
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK**: Java 21 LTS (`C:\Program Files\Android\openjdk\jdk-21.0.8` or system JDK 21)
- **Android SDK**: Build-Tools `34.0.0` or higher, Platform `android-35`
- **Python**: 3.10+ with `cryptography` package (for config signing)

### Building the Project
From the repository root:

```powershell
# Run the complete automated release pipeline (clean, test, R8 minify, assemble APK & AAB)
.\build_release.ps1
```

Or using Gradle directly in `Mobile/`:
```bash
cd Mobile
./gradlew clean test assembleRelease
```

---

## 🛡️ Security Architecture Highlights

| Layer | Mechanism | Protection |
|---|---|---|
| **Payload Integrity** | Ed25519 Digital Signatures | Protects against CDN compromise, MITM, and unauthorized configuration alterations. |
| **Network Defense** | `SecurityInterceptor` & `UrlSecurityValidator` | Blocks SSRF attempts to `localhost`, `127.0.0.1`, RFC1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), and Cloud Metadata (`169.254.169.254`). |
| **Data Protection** | Redacting Logger (`StreamHubLogger`) | Regex scrubbing prevents tokens, auth headers, cookies, and passwords from leaking into logcat. |
| **Config Resilience** | Multi-Tier Atomic Storage | Manages `active`, `previous`, and `lastKnownGood` configuration states. Automatically rolls back if validation fails. |
| **Code Integrity** | Full R8 Minification | Code obfuscation, dead code elimination, and resource shrinking enabled on all release variants. |

---

## 📺 Supported Reference Providers

StreamHub includes tested declarative adapters for reference video portals:
1. **Fry99 Video Portal (`fry99`)**: Public video index utilizing Jsoup-based CSS selectors for semantic extraction (`article.post`, `h2.entry-title a`, `img`).
2. **Masa49 Media Archive (`masa49`)**: Community media archive extracted via safe declarative markup parsing.
3. **Public Domain Video Archive (`public_domain_archive`)**: Open-access reference stream aggregator supporting progressive MP4 playback and authorized background downloads via WorkManager.

---

## 📄 License & Compliance

StreamHub is designed strictly for **authorized, publicly available media feeds**. It does not circumvent DRM, bypass paywalls, or circumvent anti-bot protections.
