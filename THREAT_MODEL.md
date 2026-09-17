# StreamHub Threat Model (STRIDE Methodology)

## 1. Scope & System Boundaries

StreamHub executes on untrusted Android devices in diverse network environments (public Wi-Fi, cellular, home LANs) and retrieves remote configuration data from external distribution networks (CDNs).

```
                      [ External CDN / Server ]
                                  │
                  (Untrusted Internet / Public Wi-Fi)
                                  │
                                  ▼
     ┌─────────────────────────────────────────────────────────┐
     │                      Android Device                     │
     │                                                         │
     │   [ Network Layer ] ──> SecurityInterceptor (SSRF block)│
     │          │                                              │
     │          ▼                                              │
     │   [ Config Verifier ] ─> Ed25519 Cryptographic Check    │
     │          │                                              │
     │          ▼                                              │
     │   [ Provider Engine ] ─> Pure CSS Selectors (Anti-RCE)  │
     │          │                                              │
     │          ▼                                              │
     │   [ Media3 / UI ] ────> Authorized Streams Only         │
     └─────────────────────────────────────────────────────────┘
```

---

## 2. STRIDE Threat Analysis

### A. Spoofing (Identity Impersonation)
- **Threat**: Adversary intercepts network traffic or compromises CDN DNS to serve a forged configuration manifest containing malicious provider URLs.
- **Impact**: App could be tricked into displaying rogue content or directing requests to attacker-controlled servers.
- **Mitigation**:
  - All configurations require an **Ed25519 digital signature** over the canonical payload.
  - Verification uses an immutable 32-byte public key embedded in the compiled APK.
  - If signature verification fails, the payload is immediately dropped with `StreamHubError.SecurityError("SIGNATURE_INVALID")`.

### B. Tampering (Data Modification)
- **Threat**: An attacker modifies an existing signed manifest in transit or alters CSS selector strings to inject malicious payloads.
- **Impact**: Potential payload corruption or injection attacks.
- **Mitigation**:
  - Ed25519 verification operates across the exact byte stream of `payload`. Altering any single byte invalidates the signature.
  - `ConfigValidator` scans all selector strings with regex to reject any JavaScript injection vectors (`javascript:`, `<script>`, `eval(`, `exec(`).
  - Selectors are executed strictly through Jsoup's safe CSS evaluator, which does not execute scripts.

### C. Repudiation
- **Threat**: Inability to track the provenance of active configurations or distinguish between legitimate updates and corrupted states.
- **Impact**: Difficulty diagnosing configuration errors or rolling back broken changes.
- **Mitigation**:
  - Manifests carry monotonic `configVersion` numbers and `generatedAt` timestamps.
  - Three-tier atomic cache manages `active_manifest.json`, `previous_manifest.json`, and `last_known_good_manifest.json`.

### D. Information Disclosure
- **Threat**: Sensitive user tokens, session cookies, or temporary media signed URLs leak into system logcat or crash dumps.
- **Impact**: Account takeover or unauthorized media link sharing.
- **Mitigation**:
  - `StreamHubLogger` and `SafeLoggingInterceptor` automatically scrub Bearer tokens, cookies, passwords, and sensitive query parameters using regular expressions.
  - R8 strips all verbose/debug logging from production release builds (`-assumenosideeffects class android.util.Log { ... }`).

### E. Denial of Service (DoS)
- **Threat**: Attacker serves an excessively large JSON/HTML response (e.g. 500 MB) or points provider URLs to local network services (SSRF) to exhaust memory or flood internal networks.
- **Impact**: App crashes due to Out-Of-Memory (OOM), or device is used to port-scan local private networks.
- **Mitigation**:
  - Hard limit of 5 MB enforced on all network response bodies (`BoundedResponseBody`).
  - Network timeouts locked to 15 seconds.
  - `UrlSecurityValidator` inspects DNS resolutions and blocks all RFC 1918 subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), loopbacks (`127.0.0.1`), link-local (`169.254.0.0/16`), and cloud metadata services (`169.254.169.254`).

### F. Elevation of Privilege
- **Threat**: Remote configuration attempts to download and execute native code, DEX files, or arbitrary bytecode.
- **Impact**: Complete device compromise and remote code execution (RCE).
- **Mitigation**:
  - Remote configuration is **strictly data-driven JSON**.
  - No dynamic class loaders (`DexClassLoader`, `PathClassLoader`) or script engines exist in the application.
  - New provider strategies requiring imperative code changes are strictly gated behind official application updates through Google Play.
