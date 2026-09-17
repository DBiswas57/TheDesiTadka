# JUICYADS PUBLISHER INTEGRATION: TheDesiTadka

================================================================================
NETWORK: JuicyAds Publisher Network
INTEGRATION TYPE: Sandboxed `jads.js` Zone Embeds + Official v1.0 Statistics API
ENGINEERING STATUS: IMPLEMENTED & VERIFIED
================================================================================

## 1. Official Documentation & Integration Capability Report

- **Provider**: JuicyAds
- **Officially Documented**: Yes (JuicyAds Publisher Documentation & API v1.0 Specification).
- **Native Android SDK**: **Not Available / Not Provided** by JuicyAds. JuicyAds operates as a digital ad network utilizing JavaScript/HTML ad tags (`jads.js`) and RESTful XML/JSON statistics endpoints.
- **Officially Permitted Android Integration**:
  1. Isolated sandboxed WebView rendering official JuicyAds zone tags.
  2. Querying publisher earnings and performance metrics via the JuicyAds v1.0 API.
- **Unsupported / Prohibited Methods**:
  - Pretending JuicyAds has a native Android `.aar` library.
  - Reverse-engineering internal ad-serving calls.
  - Fabricating synthetic impressions or clicks.

---

## 2. JuicyAdsAdapter Implementation

- **File**: `Mobile/app/src/main/java/com/thedesitadka/app/monetization/JuicyAdsAdapter.kt`
- **Interface**: Implements `AdProvider`.
- **Initialization**: Validates zone configuration and marks provider `READY`.

### 2.1 Display Ad Zone Tag Generation
For display ad placements (Home, Detail, Downloads, Player Companion), `JuicyAdsAdapter` generates the official sandboxed HTML snippet compliant with JuicyAds publisher tags:

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
  <ins id="1098765" data-width="300" data-height="250"></ins>
  <script type="text/javascript" data-cfasync="false" async src="https://js.juicyads.com/jads.js"></script>
</body>
</html>
```

### 2.2 Publisher Reporting & Statistics (API v1.0)
- **Base URL**: `https://api.juicyads.com/v1.0/`
- **Supported Endpoints**:
  - `GET /site/list`: Retrieves list of approved sites and active zones.
  - `GET /stats/popunder`: Retrieves popunder impression and revenue stats.
  - `GET /stats/banner`: Retrieves banner performance metrics.
- **Authentication**: `api_key` query parameter or Bearer header (`api_key=<token>`).
- **Credential Protection**:
  - Credentials are never embedded in source code or committed to GitHub.
  - Configuration is persisted securely in local encrypted/private DataStore (`AdConfigRepository`).
  - The UI settings screen strictly suppresses all API tokens and internal credentials.

---

## 3. Sandboxed WebView Security & Compliance

1. **Isolation**: The ad container runs inside an isolated `AdWebView` completely detached from media streaming and scraping WebViews.
2. **File Access Disabled**: `allowFileAccess = false` and `allowContentAccess = false` strictly prevent ads from reading internal app files or sqlite databases.
3. **External Click Routing**: All clicks navigating to destination landing pages are intercepted and launched in the user's default browser via `Intent(Intent.ACTION_VIEW)`.
4. **Failure Resiliency**: If JuicyAds servers return empty content or fail to load, `AdFallbackController` collapses the slot within 5 seconds without blocking any video stream or catalog operation.
