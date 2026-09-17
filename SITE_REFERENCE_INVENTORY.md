# TheDesiTadka: Site Reference Inventory (27 References, 26 Active Retained)

This document provides the complete forensic inventory and classification of all **27 reference directories** audited directly in `c:\Users\LearnersYT\source\TheDesiTadka\SiteReferrence`.

## Master Reference Inventory Table

| # | Reference Directory | Domain | TLD | Provider Family | Existing Provider | Implementation Status | Architecture & Media Source Type |
|---|---|---|---|---|---|---|---|
| 1 | `aagmaal.com` | `aagmaal.com` | `.com` | `aagmaal_family` | `aagmaal` | **IMPLEMENTED** (Domain Alias) | WordPress + Embed (Tube279 / Lulu / Cdn1) |
| 2 | `aagmaal.date` | `aagmaal.date` | `.date` | `aagmaal_family` | `aagmaal` | **IMPLEMENTED** (Primary Active Domain) | WordPress + Embed (Tube279 / Lulu) |
| 3 | `antarvasnabf.com` | `antarvasnabf.com` | `.com` | `clean_tube_family` | `antarvasnabf` | **IMPLEMENTED** | WordPress + Clean-Tube Base64 Player |
| 4 | `desibf.com` | `desibf.com` | `.com` | `direct_cdn_family` | `desibf` | **IMPLEMENTED** | WordPress + Direct CDN MP4 (`cdn.desibf.com`) |
| 5 | `desikahani2.net` | `desikahani2.net` | `.net` | `kvs_tube_family` | `desikahani2` | **IMPLEMENTED** | KVS (Kernel Video Sharing) + `/videos/get_file/` MP4 |
| 6 | `desisex.site` | `desisex.site` | `.site` | `direct_cdn_family` | `desisex` | **IMPLEMENTED** | WordPress + Direct CDN MP4 (`cdn2.desisex.site`) |
| 7 | `desitales2.com` | `desitales2.com` | `.com` | `kvs_tube_family` | `desitales2` | **IMPLEMENTED** | KVS (Kernel Video Sharing) + `/videos/get_file/` MP4 |
| 8 | `fry99` | `fry99.cc` | `.cc` | `fry99_family` | `fry99` | **IMPLEMENTED** | WordPress Tube + Video Embeds |
| 9 | `fsiblogxx.com` | `fsiblogxx.com` | `.com` | `clean_tube_family` | `fsiblogxx` | **IMPLEMENTED** | WordPress + Clean-Tube Base64 Player |
| 10 | `hitmaal.io` | `hitmaal.io` | `.io` | `hitmaal_family` | `hitmaal` | **IMPLEMENTED** | WordPress + Video Embeds |
| 11 | `indianporngirl.org` | `indianporngirl.org` | `.org` | `direct_cdn_family` | None | **REMOVED** (Paywall / Member Account Required) | Paid Membership Wall; Unviable for Public Streaming |
| 12 | `indiansexstories3.com` | `indiansexstories3.com` | `.com` | `kvs_tube_family` | `indiansexstories3` | **IMPLEMENTED** | KVS (Kernel Video Sharing) + `/videos/get_file/` MP4 |
| 13 | `ixiporn.live` | `ixiporn.live` | `.live` | `clean_tube_family` | `ixiporn` | **IMPLEMENTED** | WordPress + Clean-Tube Base64 Player (`ixifile.xyz`) |
| 14 | `kamababa1` | `kamababa1.com` | `.com` | `clean_tube_family` | `kamababa1` | **IMPLEMENTED** | WordPress + Clean-Tube Base64 / CDN MP4 |
| 15 | `masa49` | `masa49.nl` | `.nl` | `masa_network_family` | `masa49` | **IMPLEMENTED** | WordPress + Direct Private CDN (`files.pvtcdn.com`) |
| 16 | `masafun.art` | `masafun.art` | `.art` | `masa_network_family` | `masafun` | **IMPLEMENTED** | WordPress + Shared Server2 MyDown (`server2.mydown.biz`) |
| 17 | `masahub2.com` | `masahub2.com` | `.com` | `masa_network_family` | `masahub2` | **IMPLEMENTED** | WordPress + Shared Server2 MyDown (`server2.mydown.biz`) |
| 18 | `pornx11.com` | `pornx11.com` | `.com` | `direct_cdn_family` | `pornx11` | **IMPLEMENTED** | WordPress + Video Embeds |
| 19 | `uncutmaza.cc` | `uncutmaza.cc` | `.cc` | `direct_cdn_family` | `uncutmaza` | **IMPLEMENTED** | WordPress + Video Embeds |
| 20 | `webxseries.hot` | `webxseries.hot` | `.hot` | `webxseries_family` | `webxseries` | **IMPLEMENTED** | Custom REST API (JSON catalog & stream metadata) |
| 21 | `wowmasti.com` | `wowmasti.com` | `.com` | `stream_embed_family` | `wowmasti` | **IMPLEMENTED** | WordPress + LuluStream Player (`luluvid.com/e/`) |
| 22 | `wowuncut.com` | `wowuncut.com` | `.com` | `clean_tube_family` | `wowuncut` | **IMPLEMENTED** | WordPress + Clean-Tube Base64 Player (`ixifile.xyz`) |
| 23 | `xhamster.com` | `xhamster.com` | `.com` | `xhamster_family` | `xhamster` | **IMPLEMENTED** | XHamster Custom Tube Engine + HLS/MP4 streams |
| 24 | `xmaza.xxx` | `xmaza.xxx` | `.xxx` | `direct_cdn_family` | `xmaza` | **IMPLEMENTED** | WordPress + Direct CDN MP4 (`maalcdn.com`) |
| 25 | `xnxx.com` | `xnxx.com` | `.com` | `xvideos_network_family` | `xnxx` | **IMPLEMENTED** | Xvideos/XNXX Engine (`setVideoUrlLow/High/Hls`) |
| 26 | `xvideos.com` | `xvideos.com` | `.com` | `xvideos_network_family` | `xvideos` | **IMPLEMENTED** | Xvideos/XNXX Engine (`setVideoUrlLow/High/Hls`) |
| 27 | `xxxindianstories.com` | `xxxindianstories.com` | `.com` | `kvs_tube_family` | `xxxindianstories` | **IMPLEMENTED** | KVS (Kernel Video Sharing) + `/videos/get_file/` MP4 |

---

## Detailed Forensic Provider Family Breakdown

### 1. AagMaal Network Family (`aagmaal_family`)
- **Members**: `aagmaal.date` (primary active domain), `aagmaal.com` (domain alias), `aagmaal.run`.
- **Relationship**: `aagmaal.com` and `aagmaal.date` share identical post taxonomy and WordPress structure (`article`, `h2.entry-title a`, `img`, `h1`), but route traffic through mirror domains with embed providers such as Tube279 and LuluVid.
- **Handling**: Configured under a single provider family with dynamic domain alias fallback.

### 2. Masa Network Family (`masa_network_family`)
- **Members**: `masa49.nl`, `masahub2.com`, `masafun.art` (LaLaMasa).
- **Relationship**: `masahub2.com` and `masafun.art` share the same backend storage infrastructure (`https://server2.mydown.biz/myfiless/id/{id}.mp4`). `masa49.nl` uses `files.pvtcdn.com`.
- **Handling**: Unified selector models with dedicated CDN referer routing.

### 3. Clean-Tube WordPress Family (`clean_tube_family`)
- **Members**: `kamababa1.com`, `fsiblogxx.com`, `ixiporn.live`, `wowuncut.com`, `antarvasnabf.com`.
- **Mechanism**: All 5 sites run WordPress with the `clean-tube-player` plugin:
  `<iframe src=".../wp-content/plugins/clean-tube-player/public/player-x.php?q=BASE64_DATA">`
  Base64 decoding of `q` extracts `<video><source src="...mp4"></video>`.
- **Handling**: Handled natively in `HtmlSelectorAdapter` via automatic base64 query decoding.

### 4. Kernel Video Sharing Tube Family (`kvs_tube_family`)
- **Members**: `desikahani2.net`, `desitales2.com`, `indiansexstories3.com`, `xxxindianstories.com`.
- **Mechanism**: All 4 sites are built on the Kernel Video Sharing (KVS) platform. Video links are generated via `/videos/get_file/{server}/{hash}/{id}.mp4` or JavaScript configuration objects.
- **Handling**: Handled natively in `HtmlSelectorAdapter` with KVS selectors (`item = .item, .video-item, div[data-video-id]`) and `/videos/get_file/` media extraction.

### 5. Xvideos Network Family (`xvideos_network_family`)
- **Members**: `xvideos.com`, `xnxx.com`.
- **Mechanism**: Shared proprietary media script engine using `html5player.setVideoUrlLow(...)`, `setVideoUrlHigh(...)`, and `setVideoUrlHls(...)`.
- **Handling**: Handled via regex script extraction in `HtmlSelectorAdapter`.

### 6. Direct CDN WordPress Family (`direct_cdn_family`)
- **Members**: `desibf.com`, `desisex.site`, `xmaza.xxx`, `hitmaal.io`, `fry99.cc`, `uncutmaza.cc`, `pornx11.com`. *(Note: `indianporngirl.org` was explicitly removed due to paywall/member authentication requirement).*
- **Mechanism**: Standard WordPress responsive themes with direct MP4 assets or video element streams.

### 7. Global Tube & Stream Embed Families
- **Members**: `wowmasti.com` (LuluStream), `xhamster.com` (XHamster), `webxseries.hot` (JSON API).
- **Mechanism**: Custom stream extractors and embed resolution.
