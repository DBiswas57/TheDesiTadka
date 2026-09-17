# TheDesiMedia: Domain Resolution & Provider Family Architecture

## 1. Overview
Online media aggregation providers frequently change their Top-Level Domains (TLDs) or mirror addresses due to registrar restrictions, CDN rotation, or domain migrations (e.g. `example.com` → `example.nl` → `example.co` → `example.cc`).

TheDesiMedia introduces a **Domain-Family Architecture** that separates the *logical provider implementation* from its *ephemeral active domain*. This allows the app to dynamically resolve, validate, and cache working domains without requiring a complete APK update or app rebuild every time a provider migrates.

---

## 2. Conceptual Hierarchy

```
ProviderFamily (e.g., "aagmaal_family")
       │
       ├── Domain Candidates: ["https://aagmaal.date", "https://aagmaal.com", "https://aagmaal.run"]
       │
       ▼
DomainResolver
       │
       ├── 1. Check Memory / Disk Cache (24-Hour TTL)
       ├── 2. Concurrency Lock (Deduplicate simultaneous resolutions)
       ├── 3. Health Check:
       │      - HTTP GET / HEAD request
       │      - Follow safe redirects (max 2 hops)
       │      - Verify Expected Provider Signature / Markers
       └── 4. Active Domain Persistence
       │
       ▼
Active Domain (e.g., "https://aagmaal.date")
       │
       ▼
Provider Adapter (HtmlSelectorAdapter / Custom Engine)
       ├── Content Listing
       ├── Detail Extraction
       └── Media Playback & Download
```

---

## 3. Key Components & Implementation Rules

### A. Candidate Domain Lists
Each `ProviderConfig` defines:
- `baseUrl`: Default or initial known-working URL.
- `domains`: List of alternative domains / mirrors for that provider family.
- `familyId`: Identifier grouping related aliases together.
- `validationMarker`: Required content string, title keyword, or HTML signature that must appear in the HTTP response to confirm that the domain belongs to the expected provider (protecting against domain hijacking, parking pages, or ISP blocks).

### B. Health Check & Validation
A domain is NOT marked healthy simply because it returns `HTTP 200`. The resolver validates:
1. `HTTP 200 OK` status code.
2. Content body contains the expected `validationMarker` (e.g., `"aagmaal"`, `"kamababa"`, `"masa"`).
3. Parking page / ISP block detection (e.g., pages with `"domain for sale"`, `"blocked by order"`, or `"namecheap"` are treated as invalid).

### C. Persistent Caching & TTL
- Verified working domains are stored in local persistent storage (`SharedPreferences` / JSON cache) with a timestamp.
- **Cache TTL**: 24 hours. The app never re-verifies healthy domains on startup or dashboard loads, eliminating network latency.
- Re-verification occurs only when:
  - The cached domain expires.
  - A network error (HTTP 404, 502, `UnknownHostException`, or connection timeout) occurs during content fetching.
  - The user manually taps "Refresh" in Settings or Dashboard.

### D. Concurrency & Deduplication
- Multiple screens or parallel image loads requesting domain resolution for the same provider share a `Mutex` / `ConcurrentHashMap` so only one health-check network call executes at a time.

### E. Security & Privacy Guarantees
- **No Arbitrary Domain Injection**: Only pre-configured or cryptographically signed remote candidate domains are checked.
- **URL Privacy**: Internal active domains are never displayed in user-facing UI or error messages. Normal UI shows provider names and friendly statuses.
- **Safety**: No access controls, CAPTCHAs, or authentication bypasses are implemented.
