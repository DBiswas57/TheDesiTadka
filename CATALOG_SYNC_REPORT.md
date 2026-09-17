# TheDesiMedia: Catalog Synchronization & Architecture Report

## Executive Summary
This forensic report clarifies the catalog architecture, the observed `404` status on remote synchronization, the reconciliation of reference count vs provider count, and the atomic catalog fallback mechanism implemented in `TheDesiMedia`.

---

## 1. Remote Catalog 404 Root Cause Analysis
- **Observed Behavior**:
  In Settings:
  `"Remote catalog server returned 404. Active catalog v120 (26 providers) maintained."`
- **Root Cause**:
  `ConfigRepository.kt` connects to `PreferenceStore.DEFAULT_CONFIG_URL` when "Sync Now" is pressed or on background startup sync. In the current test configuration, the default remote URL points to a placeholder endpoint (`https://raw.githubusercontent.com/.../main/catalog.json`) where no external signed catalog file has been published yet.
- **Protection & Resilience**:
  Instead of failing or clearing local providers, `ConfigRepository.kt` catches the HTTP 404, prevents state corruption, and safely maintains the active built-in catalog.
  ```kotlin
  val errorMsg = when {
      e.message?.contains("404") == true -> "Remote catalog server returned 404. Active catalog v${_manifestFlow.value.configVersion} (${_manifestFlow.value.providers.size} providers) maintained."
      else -> e.message ?: "Network error during sync"
  }
  ```

---

## 2. Provider Count Discrepancy Reconciliation
- **Previous Observed State**:
  - `27` reference directories in `SiteReferrence`
  - `26` active providers in `AppContainer.kt`
  - `"Reload Built-in Sources (16 Providers)"` button text in `SettingsScreen.kt`
- **Reconciliation & Forensic Audit**:
  1. **Missing Provider**: `aagmaal.com` existed in `SiteReferrence` as a separate directory alongside `aagmaal.date`, but only `aagmaal` (`aagmaal.date`) was registered in `AppContainer.kt`.
  2. **Hardcoded UI String**: `SettingsScreen.kt` lines 218 & 225 contained the hardcoded string `"Reload Built-in Sources (16 Providers)"` from the initial application version with 16 sources.
  3. **Fix**:
     - Added `aagmaal_com` (`https://aagmaal.com`) as the 27th provider entry under `familyId = "aagmaal_family"` with domain fallback between `https://aagmaal.com` and `https://aagmaal.date`.
     - Bumped manifest version to `v121` with **27 total providers**.
     - Updated `SettingsScreen.kt` to dynamically show `${manifest.providers.size} Providers` everywhere.

---

## 3. Atomic Catalog Update Pipeline
Catalog updates are strictly atomic:
```
Remote Config URL
       ↓
Fetch Signed JSON Payload
       ↓
Verify Signature & Version (ECDSA / SHA-256)
       ↓
Validate Provider Schema & Capabilities
       ↓
Write to Temporary Cache (`config_cache/pending_manifest.json`)
       ↓
Atomically Replace Active Manifest
       ↓
Notify ProviderEngine (Hot Reload)
```
If signature verification fails, network drops, or a 404 occurs, the pending update is discarded, and the existing catalog is 100% preserved.

---

## 4. UI / Settings Hardening
- Raw remote URLs, credentials, and internal resolver endpoints are strictly hidden from normal user-facing Settings.
- Users see clear status indicators: active version, total provider count, schema version, and sync health without technical jargon.
