# EXOCLICK PUBLISHER INTEGRATION: TheDesiTadka

================================================================================
NETWORK: ExoClick (EXADS Architecture)
INTEGRATION TYPE: Sandboxed Web Zone Tags + VAST 2.0/3.0 + Official API v2
ENGINEERING STATUS: IMPLEMENTED & VERIFIED
================================================================================

## 1. Official Documentation & Integration Capability Report

- **Provider**: ExoClick / EXADS
- **Officially Documented**: Yes (ExoClick Publisher Guidelines & API v2 Documentation).
- **Native Android SDK**: **Not Available / Not Provided** by ExoClick. ExoClick provides web-standard JavaScript/HTML zone tags, standard VAST URLs for video players, and a RESTful v2 statistics API.
- **Officially Permitted Android Integration**:
  1. Isolated sandboxed WebView rendering official ExoClick zone scripts.
  2. Direct VAST tag delivery for IAB-compliant media players.
  3. Server-side / client-side statistics querying via ExoClick API v2.
- **Unsupported / Prohibited Methods**:
  - Reverse-engineering private SDKs.
  - Fabricating impression or click tracking pixels.
  - Injecting arbitrary JavaScript into Media3 ExoPlayer directly.

---

## 2. ExoClickAdapter Implementation

- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/ExoClickAdapter.kt`
- **Interface**: Implements `AdProvider`.
- **Initialization**: Validates zone configuration and marks provider `READY`.

### 2.1 Display Ad Zone Tag Generation
For display ad placements (Home, Detail, Downloads, Player Companion), `ExoClickAdapter` synthesizes the official sandboxed HTML snippet compliant with ExoClick ad-server specifications:

```html
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    body { margin: 0; padding: 0; background-color: #141414; display: flex; justify-content: center; align-items: center; height: 100vh; overflow: hidden; }
  </style>
</head>
<body>
  <script type="application/javascript">
    var ad_idzone = "5671234";
    var ad_width = "300";
    var ad_height = "250";
  </script>
  <script type="application/javascript" src="https://a.exdynsrv.com/ads.js"></script>
</body>
</html>
```

### 2.2 VAST Video Ad Integration
- For video ad placements in `AdFormat.VAST_VIDEO`, `ExoClickAdapter` resolves the configured VAST tag URL:
  `https://syndication.exoclick.com/splash.php?idzone={zoneId}&type=8`
- Can be passed directly into IAB VAST parsers or ExoPlayer IMA extensions.
- When no valid VAST URL is configured or video ads are disabled, it gracefully degrades to a companion display banner (`300x100` or `300x250`) anchored safely outside the player controls.

### 2.3 Publisher Reporting & Statistics (API v2)
- **Base URL**: `https://api.exoclick.com/v2/`
- **Two-Step Authentication Specification**:
  1. Static Publisher API Token is exchanged via `POST https://api.exoclick.com/v2/login` with `{"api_token":"<api_token>"}`.
  2. The endpoint returns a temporary `Bearer` session token (`expires_in`: 43200s / 12h) with a refresh token.
  3. `ExoClickAdapter` caches the Bearer session token in memory and automatically refreshes before expiration.
- **Reporting Method**: `GET /statistics/p/date?date-from=YYYY-MM-DD&date-to=YYYY-MM-DD`
- **Authentication Header**: `Authorization: Bearer <session_bearer_token>`
- **Site Status Query**: `GET /sites` to monitor compliance and ownership verification.
- **Configured Token**: Stored in `ExoClickConfig.apiToken` and synchronized with `AdConfigRepository`.

---

### 2.4 ExoClick Publisher Site Approval & Zone Creation Guide

To ensure compliance approval from ExoClick reviewers:
1. **Never submit repository URLs (`https://github.com/`)**:
   - ExoClick will reject with `Insufficient content or site unavailable` because GitHub is not a publisher domain owned by you.
2. **Setup a Dedicated Landing Page or Domain**:
   - Create a clean landing page for `TheDesiTadka` (showing app features, preview screenshots, and an APK download button).
   - Alternatively, use a custom domain or web portal.
3. **Verify Ownership**:
   - In ExoClick **Sites & Zones -> Sites**, click on the site and verify ownership using either the HTML `<meta name="exoclick-site-verification" content="...">` tag or by uploading the `exoclick-verification.txt` file to the root of your domain.
4. **Create Ad Zones**:
   - Once the site is **Approved**, navigate to **Sites & Zones -> Zones -> New Zone**.
   - Create the following recommended zones:
     - **Zone 1 (Home Feed & Details)**: 300x250 Banner (Medium Rectangle).
     - **Zone 2 (Player Companion & Downloads)**: 300x100 Mobile Banner.
     - **Zone 3 (Player Preroll)**: In-Stream Video (VAST Tag).
   - Place the resulting numerical Zone IDs into `ExoClickConfig` in `AdConfig.kt`:
     - `homeZoneId = "<zone_1_id>"`
     - `detailZoneId = "<zone_1_id>"`
     - `playerCompanionZoneId = "<zone_2_id>"`
     - `playerVastTagUrl = "https://syndication.exoclick.com/splash.php?idzone=<zone_3_id>&type=8"`

---

## 3. Publisher Guidelines & Anti-Fraud Compliance

In accordance with ExoClick publisher terms:
1. **Accurate Reporting**: Impressions are recorded only when the WebView signals actual document rendering completion.
2. **Click Verification**: Clicks are registered only upon genuine user interaction when the WebView triggers `shouldOverrideUrlLoading`.
3. **No Auto-Redirects**: The sandboxed `AdWebView` prohibits automatic redirects (`window.location`) that hijack user navigation.
4. **No Pop-under Flooding**: Full-screen pop-unders are rejected within the native app container.
5. **No Fake Views**: Automated reloading or synthetic impression generation is strictly prohibited.
