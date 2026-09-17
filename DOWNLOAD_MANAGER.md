# TheDesiMedia — Download Manager Architecture & Implementation

## Overview
TheDesiMedia features a resilient, lifecycle-safe, background-capable Download Manager designed specifically to handle high-bandwidth media streams and network fluctuations on mobile devices.

---

## 1. Architectural Principles
1. **Separation of Playback vs Download Capabilities**:
   - Being playable does not automatically imply a stream can or should be downloaded.
   - Streams explicitly declare `PlaybackCapability` and `DownloadCapability`.
   - Authorized downloads are restricted to supported direct media formats (e.g. progressive MP4) from providers declaring `ProviderCapability.DOWNLOAD`.
2. **Never Expose Partially Downloaded Files**:
   - Data is downloaded into temporary `.part` files in external app storage (`Movies/`).
   - Only after successful completion, size verification, and stream flush is the file atomically moved to its final destination (`.mp4`).
3. **Resumable HTTP Range Streaming**:
   - If an ongoing download is paused or network drops, the existing byte length of `.part` is inspected.
   - Resumption issues a `Range: bytes={existingBytes}-` request to continue from the exact interruption byte offset.
4. **Structured Concurrency with WorkManager**:
   - `DownloadWorker` extends `CoroutineWorker` and operates as an Android Foreground Service with continuous notification progress updates.

---

## 2. State Machine

```
   [QUEUED]
      ↓
 [DOWNLOADING] ⇆ [PAUSED]
      ↓             ↓
 [COMPLETED]     [CANCELLED]
      ↓             ↓
  [DELETED]      [DELETED]
      ↑
   [FAILED] ➔ [RETRYING] ➔ [DOWNLOADING]
```

### Complete State Definitions:
- `QUEUED`: Download request registered and enqueued in WorkManager queue.
- `DOWNLOADING`: Worker actively streaming chunks from source server.
- `PAUSED`: User or system paused; partial `.part` file preserved on disk.
- `RETRYING`: Transient error occurred; automatic exponential backoff retry active.
- `COMPLETED`: Stream fully received, verified, and atomically renamed to `.mp4`.
- `FAILED`: Terminal HTTP or I/O error occurred (e.g. HTTP 403, 404, or disk full).
- `CANCELLED`: Aborted by user; temporary file deleted immediately.
- `DELETED`: Download record and local media file purged from disk and database.

---

## 3. Data Model (`DownloadRecordEntity`)
Stored persistently in Room database table `downloads`:

| Field | Type | Description |
|---|---|---|
| `id` | String | Unique Download ID (corresponds to VideoItem ID) |
| `contentId` | String | Normalized canonical media content identifier |
| `providerId` | String | Originating provider ID |
| `title` | String | Clean title of the video |
| `thumbnailUrl` | String | Remote thumbnail for downloads screen display |
| `mediaUrl` | String | Target media download URL |
| `localFilePath` | String | Absolute path to final `.mp4` file on device |
| `mimeType` | String | MIME type (`video/mp4`) |
| `totalBytes` | Long | Total size in bytes from `Content-Length` |
| `downloadedBytes`| Long | Number of bytes safely written to disk |
| `progress` | Int | Percentage completion (0 - 100) |
| `speedBytesPerSec`| Long | Current download speed sampled every 500ms |
| `etaSeconds` | Long | Estimated time remaining in seconds |
| `status` | DownloadStatus | Current lifecycle state |
| `error` | String? | Diagnostic error message (sanitized) |
| `retryCount` | Int | Number of execution retries |
| `createdAt` | Long | Timestamp of enqueue |
| `completedAt` | Long? | Timestamp of completion |

---

## 4. Header & Cookie Propagation
Certain media delivery networks (e.g. `pvtcdn.com`, `masa49.nl`) reject raw HTTP requests without valid referrers or clearance cookies. `DownloadWorker` dynamically inspects the media host and injects:
- **`User-Agent`**: Matching Android Chrome client.
- **`Referer`**: Domain-specific parent URL (e.g. `https://www.masa49.nl/`, `https://www.kamababa1.com/`).
- **`Cookie`**: Active session cookies bridged from `android.webkit.CookieManager`.

---

## 5. Persistence & Crash Recovery
When the application starts:
```kotlin
container.downloadRepository.recoverInterruptedDownloads()
```
Any job left in `DOWNLOADING` state due to process termination or device reboot is transitioned to `PAUSED`. The user can resume all or individual downloads cleanly with zero byte duplication.

---

## 6. Forensic Audit & Crash-Proofing Resolution

### Root Cause Analysis:
1. **`android.app.MissingForegroundServiceTypeException` (Android 14+ / API 34 & 35)**:
   - Apps targeting SDK 34+ require all foreground services to declare a specific type in `AndroidManifest.xml` and pass that type to `ForegroundInfo`.
   - Previously, WorkManager's `SystemForegroundService` did not declare `android:foregroundServiceType="dataSync"`, and `ForegroundInfo` used the 2-argument constructor with type `0`.
   - On Android 14+, starting the foreground service threw an uncatchable fatal exception in `SystemForegroundService`, killing the process.
   - **Resolution**: Merged `SystemForegroundService` with `android:foregroundServiceType="dataSync"` in `AndroidManifest.xml`, passed `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` to `ForegroundInfo` on API 29+, and wrapped promotion in `safeSetForeground()`.

2. **R8 Reflection Stripping on Release Builds**:
   - `DownloadWorker` was not preserved with `-keep` rules, causing R8 to strip or obfuscate the `(Context, WorkerParameters)` constructor.
   - **Resolution**: Added comprehensive `-keep` rules for all WorkManager workers in `proguard-rules.pro`.

3. **Centralized Filename Sanitization**:
   - Built `FileNameSanitizer` to normalize Unicode, strip path traversal (`..`), remove illegal OS characters (`\ / : * ? " < > |`), and handle Windows reserved names (`CON, PRN, AUX, NUL, COM1-9`).

4. **Duplicate Download Prevention**:
   - Integrated `enqueueUniqueWork("download_${downloadId}", ExistingWorkPolicy.KEEP)` and checked active database status before enqueueing to prevent concurrent stream corruption.

5. **Streaming I/O & Memory Safety**:
   - Downloads stream through a fixed 32 KB buffer directly into a `.part` `RandomAccessFile`. Byte arrays never buffer entire files in memory.

