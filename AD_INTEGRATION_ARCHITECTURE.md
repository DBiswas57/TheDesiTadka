# AD INTEGRATION ARCHITECTURE: TheDesiTadka

================================================================================
PROJECT: TheDesiTadka Android Application
TARGET: Unified Production-Grade Monetization Subsystem
STATUS: FULLY IMPLEMENTED & VERIFIED
================================================================================

## 1. Architectural Overview

The monetization subsystem is designed to operate completely decoupled from the application's core media streaming, scraping, and download engines. Third-party advertising networks must never impede video playback, catalog synchronization, or download functionality.

```
TheDesiTadka Android Application
    |
    +-- AppContainer
            |
            +-- MonetizationManager (Central Facade)
                    |
                    +-- AdProvider (Interface)
                    |     +-- ExoClickAdapter
                    |     +-- JuicyAdsAdapter
                    |
                    +-- AdPlacementEngine (Placement Resolution & Execution)
                    |
                    +-- AdLifecycleManager (Activity Lifecycle Coordination)
                    |
                    +-- AdFrequencyController (Spam & Rotation Prevention)
                    |
                    +-- AdConsentManager (Privacy & Credential Isolation)
                    |
                    +-- AdIntegrityManager (Layered APK & Signing Verification)
                    |
                    +-- AdEventTracker (Zero-Fabrication Analytics)
                    |
                    +-- AdConfigRepository (Jetpack DataStore Persistence)
                    |
                    +-- AdFallbackController (Bounded 5-Second Timeout & Collapse)
```

---

## 2. Core Components

### 2.1 MonetizationManager
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/MonetizationManager.kt`
- **Role**: Single entry point for all UI layers (Composables, Activities).
- **Responsibilities**:
  - Initializes provider adapters (`initialize()`).
  - Routes placement requests through `AdPlacementEngine`.
  - Dispatches impression and click events to `AdPlacementEngine`.
  - Exposes `isMonetizationActive()` and `getStatusSummary()`.

### 2.2 AdProvider Interface & State Machine
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdProvider.kt`
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdProviderType.kt`
- **Supported Providers**:
  - `AdProviderType.EXOCLICK` ("ExoClick Publisher")
  - `AdProviderType.JUICYADS` ("JuicyAds Publisher")
- **Formal State Machine**:
  ```
  UNINITIALIZED -> INITIALIZING -> READY -> LOADING -> LOADED -> DISPLAYING -> DESTROYED
                                       \         \-> FAILED -----/
                                        \-> UNAVAILABLE
  ```
- **Guarantees**:
  - An HTTP 200 is never treated as an impression.
  - A WebView creation is never treated as an ad load.
  - State transitions strictly reflect real provider lifecycle events.

### 2.3 AdPlacementEngine
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdPlacementEngine.kt`
- **Supported Placements**:
  - `HOME_FEED`: Displayed after 8 media cards in the browse grid.
  - `DETAIL_PAGE`: Displayed below synopsis, before related content.
  - `PLAYER_COMPANION`: Non-intrusive bottom banner in portrait mode outside the video viewport.
  - `DOWNLOADS_BOTTOM`: Anchored at the bottom of the offline downloads list.
- **Resolution Strategy**:
  1. Checks master `monetizationConfig.enabled`.
  2. Checks placement-specific toggle (`homeEnabled`, `detailEnabled`, etc.).
  3. Enforces frequency capping via `AdFrequencyController`.
  4. Resolves primary provider according to `AdProviderMode`.
  5. Executes request via `AdFallbackController` with bounded timeout.

### 2.4 AdFrequencyController
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdFrequencyController.kt`
- **Anti-Spam Controls**:
  - `cooldownBetweenPlacementsMs`: 10,000 ms global spacing between any two ad loads.
  - `frequencyCapSeconds`: 60 seconds per specific placement.
  - Screen rotation and Compose recomposition preserve loaded state in `rememberSaveable` or ViewModel state without triggering re-fetch.

### 2.5 AdFallbackController
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdFallbackController.kt`
- **Execution Pattern**:
  - Primary network load wrapped in `withTimeoutOrNull(5000ms)`.
  - On failure or timeout, immediately queries secondary fallback network.
  - If fallback fails or times out, returns `AdLoadResult.Failure`.
  - The UI slot instantly collapses with `expandVertically() / shrinkVertically()` animation. Content is never obstructed.

### 2.6 AdConsentManager
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdConsentManager.kt`
- **Privacy Enforcement**:
  - Sanitizes all ad request URLs to strip user tokens, session cookies, provider credentials, and media URLs.
  - Provides non-personalized ad parameters (`npa=1`) where applicable.

### 2.7 AdLifecycleManager
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdLifecycleManager.kt`
- **Lifecycle Integration**:
  - Implements `DefaultLifecycleObserver`.
  - `onPause`: Pauses ad timers and active WebViews.
  - `onResume`: Resumes timers.
  - `onDestroy`: Disposes WebViews and clears cached references to prevent memory leaks.

### 2.8 AdEventTracker
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/AdEventTracker.kt`
- **Event Taxonomy**:
  `REQUESTED`, `LOADED`, `IMPRESSION`, `CLICK`, `STARTED`, `FIRST_QUARTILE`, `MIDPOINT`, `THIRD_QUARTILE`, `COMPLETED`, `CLOSED`, `FAILED`, `TIMED_OUT`.
- **Integrity Rule**:
  - Events are emitted only when the provider's actual callback fires.
  - Fabricating impressions, clicks, or simulated revenue is strictly prohibited.

### 2.9 AdWebView (Hardened Sandbox)
- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/ui/AdWebView.kt`
- **Security Hardening**:
  - `javaScriptEnabled = true` (required for official JS zone tags).
  - `allowFileAccess = false` (blocks access to local `/data/data/...` files).
  - `allowContentAccess = false` (blocks content resolver access).
  - `allowFileAccessFromFileURLs = false`
  - `allowUniversalAccessFromFileURLs = false`
  - `setSupportMultipleWindows(false)`
  - Outbound link interception: URLs matching `http://` or `https://` are opened safely via `Intent(Intent.ACTION_VIEW)` in external system browser.
  - Custom scheme interception (e.g. `intent://`, `market://`, `tel:`) is validated or safely routed to avoid silent APK downloads or unauthorized system actions.
