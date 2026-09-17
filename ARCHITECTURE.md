# TheDesiMedia: System Architecture Document

## 1. Executive Summary

TheDesiMedia is designed around a single architectural invariant:
> **The APK binary is an immutable execution engine; all provider definitions and endpoint mappings are mutable, cryptographically signed declarative data.**

This architecture decouples the lifecycle of the Android application from the volatility of external third-party media sources. Changes on upstream providers can be remediated instantly by updating and signing a remote JSON manifest, requiring zero APK re-compilation or store deployment.

---

## 2. High-Level System Architecture

```
                    ┌────────────────────────────────────────────────────────┐
                    │                   Remote HTTPS CDN                     │
                    │   Signed Manifest Envelope (/config/manifest.json)     │
                    └───────────────────────────┬────────────────────────────┘
                                                │
                                                ▼  HTTPS Fetch
                    ┌────────────────────────────────────────────────────────┐
                    │              TheDesiMedia Android Client               │
                    │                                                        │
                    │  ┌──────────────────────────────────────────────────┐  │
                    │  │                   core-config                    │  │
                    │  │  1. Download via Hardened OkHttpClient (core-net)│  │
                    │  │  2. Ed25519 Cryptographic Verification (security)│  │
                    │  │  3. Schema, Expiration, Version & SSRF Checks    │  │
                    │  │  4. Atomic 3-Tier Cache (Active/Prev/KnownGood)  │  │
                    │  └────────────────────────┬─────────────────────────┘  │
                    │                           │ Normalized ProviderConfigs │
                    │                           ▼                            │
                    │  ┌──────────────────────────────────────────────────┐  │
                    │  │                provider-engine                   │  │
                    │  │  ProviderEngine Router & Health Monitor          │  │
                    │  │  ├─ HtmlSelectorAdapter (Jsoup CSS Engine)       │  │
                    │  │  ├─ WordPressRestAdapter (WP-JSON v2 Parser)     │  │
                    │  │  ├─ RssFeedAdapter (XML / Atom Parser)           │  │
                    │  │  └─ JsonApiAdapter (Dynamic REST Mapping)        │  │
                    │  └────────────────────────┬─────────────────────────┘  │
                    │                           │ Normalized Domain Entities │
                    │                           │ (VideoItem, MediaSource)   │
                    │                           ▼                            │
                    │  ┌──────────────────────────────────────────────────┐  │
                    │  │                  app module                      │  │
                    │  │  ├─ Jetpack Compose Material 3 UI (8 Screens)    │  │
                    │  │  ├─ Media3 ExoPlayer Engine (StateFlow Ticker)   │  │
                    │  │  ├─ Room Database (History, Favorites, Offline)  │  │
                    │  │  ├─ WorkManager Download Engine (Speed/ETA/Range)│  │
                    │  │  └─ Cloudflare In-App Verification Bridge        │  │
                    │  └──────────────────────────────────────────────────┘  │
                    └────────────────────────────────────────────────────────┘
```

---

## 3. Modular Boundaries & Responsibilities

TheDesiMedia enforces strict module boundaries to avoid circular dependencies:

### `:core-model`
- Pure Kotlin data models and domain contracts.
- Independent of Android framework SDKs and UI libraries.
- Defines: `VideoItem`, `MediaSource`, `ContentCategoryDefinition`, `ProviderCapability`, `PlaybackCapability`, `DownloadCapability`, `ProviderManifest`, and future extension interfaces (`TorrentSearchProvider`, `SeedrClient`, `ProxyProvider`, `AlternativeMediaResolver`).

### `:core-security`
- Hardened cryptographic verification and runtime integrity.
- Contains:
  - `Ed25519Verifier`: High-speed RFC 8032 digital signature verification using BouncyCastle.
  - `UrlSecurityValidator`: Comprehensive anti-SSRF guardrails blocking local, private, and cloud metadata targets.
  - `StreamHubLogger`: Structured logging with automatic sensitive data redaction.

### `:core-network`
- Low-level networking stack built on OkHttp 4.
- Enforces request timeouts (15s connect, 20s read), HTTP header injection (User-Agent, Referer), and response size ceilings (5 MB) to prevent memory exhaustion.
- Features pluggable `CookieProvider` bridging WebKit session cookies to native OkHttp requests.

### `:core-config`
- Remote configuration repository, local cache tier, and validation engine.
- Implements:
  - `ConfigVerifier`: Validates signed envelopes and decrypts payloads.
  - `ConfigValidator`: Validates schema, version monotonicity, and AST selector safety.
  - `ConfigCache`: Thread-safe atomic file persistence for active, previous, and known-good manifests.

### `:provider-engine`
- Dynamic provider registry, lifecycle management, and protocol adapters.
- Normalizes disparate upstream site structures into consistent domain entities (`VideoItem`, `MediaSource`).
- Supports extensible CSS selectors, JSON REST APIs, RSS/Atom feeds, and iframe/player decoders.

### `:app`
- Application entry point, dependency injection (`AppContainer`), Room database, WorkManager background downloaders, Media3 player engine, and Jetpack Compose Material 3 UI.

---

## 4. Future Architectural Extension Boundaries (Stubs Only)
To allow scaling to 500+ providers and lawful future integrations without refactoring the core app, four isolated interface boundaries are established in `:core-model`:

1. **`AlternativeMediaResolver`**:
   - `resolveAlternativeStream(contentId, metadata)`
   - Default: `NoOpAlternativeMediaResolver`
2. **`TorrentSearchProvider`**:
   - `search(query): Result<List<TorrentMetadata>>`
   - Default: `UnsupportedTorrentSearchProvider`
3. **`SeedrClient`**:
   - `authenticate()`, `getTransfers()`, `getFiles()`
   - Default: `MockSeedrClient`
4. **`ProxyProvider` & `ProxyHealthChecker`**:
   - `getCandidateProxies()`, `checkHealth(proxy)`
   - Built for legitimate network environments without bypassing access controls.
