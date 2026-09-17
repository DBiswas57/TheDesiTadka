# MONETIZATION & INTEGRITY TROUBLESHOOTING GUIDE: TheDesiTadka

================================================================================
PROJECT: TheDesiTadka Android Application
TARGET: Runtime Diagnostic Codes, Failure Recovery, and Log Analysis
================================================================================

## 1. Diagnostic Error Taxonomy

All monetization and update operations emit structured logcat events through `StreamHubLogger`. To preserve user privacy and network compliance, logs **never** contain authorization tokens, passwords, cookies, or user telemetry.

| Error Code | Category | Root Cause | System Action | Resolution / Fix |
|---|---|---|---|---|
| `EXOCLICK_NOT_CONFIGURED` | Monetization | Zone ID missing or empty in configuration | Skips ExoClick; attempts fallback | Configure valid zone ID in `AdConfigRepository` |
| `JUICYADS_NOT_CONFIGURED` | Monetization | Zone ID missing or empty in configuration | Skips JuicyAds; attempts fallback | Configure valid zone ID in `AdConfigRepository` |
| `AD_PROVIDER_UNAVAILABLE` | Monetization | Network returned HTTP error or provider is down | Triggers fallback provider | Check network connectivity or provider server status |
| `AD_LOAD_TIMEOUT` | Monetization | Ad failed to load within bounded 5,000 ms window | Cancels coroutine; invokes fallback | Normal behavior under high network latency; no user impact |
| `AD_RENDER_FAILED` | Monetization | WebView renderer error or unsupported HTML element | Collapses ad slot | Check webview compatibility; slot collapses safely |
| `AD_NETWORK_ERROR` | Monetization | Device offline or DNS failure resolving ad domain | Collapses ad slot | Normal when offline; content continues working |
| `AD_CONSENT_REQUIRED` | Privacy | Consent not granted under strict privacy policies | Delivers non-personalized ads | Request user consent via dialog if targeting EEA |
| `AD_INTEGRATION_UNSUPPORTED` | Capability | Requested format (e.g. native SDK) is not supported | Degrades to sandboxed HTML | Use supported formats (HTML Zone tag, VAST) |
| `UPDATE_SOURCE_UNAVAILABLE` | Update | GitHub API down or rate-limited | Preserves current version; logs warning | Retry update check after backoff |
| `UPDATE_SIGNATURE_INVALID` | Security | Downloaded update APK signed with wrong certificate | Aborts install; deletes downloaded file | Verify release was signed with official release keystore |
| `UPDATE_CHECKSUM_INVALID` | Security | SHA-256 does not match release notes hash | Aborts install; deletes downloaded file | Re-upload uncorrupted release artifact to GitHub |
| `APP_INTEGRITY_FAILED` | Anti-Tamper | Package name or cert does not match official release | Enters safe restricted state; disables ads | Reinstall official build from official GitHub repository |
| `UNTRUSTED_BUILD` | Anti-Tamper | MT Manager or APK Editor modification detected | Flags build as untrusted in Diagnostics | Build from authentic source code |
| `WRONG_SIGNING_CERTIFICATE` | Anti-Tamper | SHA-256 fingerprint differs from pinned cert | Rejects official trust status | Ensure signing with official production keystore |

---

## 2. Common Scenarios & Diagnostic Flows

### Scenario A: Ad Slot Appears Blank or Collapses
- **Diagnosis**: Both primary (ExoClick) and secondary (JuicyAds) ad requests timed out or returned no-fill.
- **Verification**: Check logcat for:
  `[StreamHubLogger][AdPlacementEngine] Placement HOME_FEED: all providers failed or timed out. Collapsing slot.`
- **Expected Behavior**: Content list smoothly adjusts to fill space. Video playback, search, and downloads remain 100% operational.

### Scenario B: Update Check Returns "Verification Failed"
- **Diagnosis**: The downloaded APK from GitHub failed certificate pinning or checksum verification.
- **Verification**: Check logcat for:
  `[StreamHubLogger][AppUpdateManager] Untrusted signing certificate in update APK! Installation aborted.`
- **Recovery**: The malicious or corrupt APK is immediately removed from the device cache (`/data/user/0/com.thedesitadka.app/cache/updates/`). The user is protected from tampered installs.

### Scenario C: App Marked as "Development Debug" in Diagnostics
- **Diagnosis**: App was compiled with `./gradlew assembleDebug`.
- **Resolution**: This is expected for development builds. For production certification, build with `./gradlew assembleRelease`.
