# StreamHub Security Architecture & Policy

## 1. Security Philosophy: Defense in Depth

StreamHub operates under a zero-trust model regarding external inputs and remote configurations. While remote configurations drive provider capabilities dynamically, the application treats all incoming network data as potentially untrusted until verified through layered cryptographic, operational, and network controls.

```
       [ Remote Config Payload ]
                  │
                  ▼
   1. TLS 1.3 Strict HTTPS
                  │
                  ▼
   2. Ed25519 Cryptographic Verification (32-byte public key)
                  │
                  ▼
   3. Schema & Syntax Validation (No JS, eval, or executable tags)
                  │
                  ▼
   4. Monotonic Versioning & Expiration Checks
                  │
                  ▼
   5. Network Security Layer: SSRF & IP Range Validation
                  │
                  ▼
   6. Atomic Multi-Tier Persistence (active, previous, known-good)
                  │
                  ▼
   7. Redacting Logging (Tokens, cookies, and secrets masked)
```

---

## 2. Cryptographic Configuration Verification

### Key Management
- **Algorithm**: Ed25519 (Edwards-curve Digital Signature Algorithm, RFC 8032).
- **Public Key**: Embedded inside `Ed25519Verifier.kt` as a Base64-encoded SubjectPublicKeyInfo DER.
- **Private Key**: Kept exclusively in air-gapped CI/CD deployment infrastructure or hardware security modules (HSMs). **Never committed to the repository or included in the APK binary.**

### Verification Workflow
Before parsing JSON into business models:
1. The remote manifest envelope is read as `SignedPayload`:
   ```json
   {
     "payload": "{\"schemaVersion\":1,...}",
     "signature": "Ab5aEG8ss0+CZSOJW9sw...",
     "keyId": "streamhub-v1"
   }
   ```
2. The exact UTF-8 byte representation of `payload` is verified against the 64-byte Ed25519 signature using `Ed25519Verifier.verify()`.
3. If the signature is invalid or modified by even a single bit, the configuration is rejected with `StreamHubError.SecurityError("SIGNATURE_INVALID")` and discarded immediately.

---

## 3. Server-Side Request Forgery (SSRF) Defense

Because providers are configured dynamically with `baseUrl` endpoints, a malicious or compromised configuration could attempt to turn the Android device into a proxy to probe internal private subnets or local loopbacks.

To prevent this, `UrlSecurityValidator` and `SecurityInterceptor` validate every outbound HTTP request and redirect against an extensive blocklist:

| Target Category | Subnet / Range | Mitigation |
|---|---|---|
| **Loopback** | `127.0.0.0/8`, `::1` | Blocked |
| **Class A RFC 1918** | `10.0.0.0/8` | Blocked |
| **Class B RFC 1918** | `172.16.0.0/12` | Blocked |
| **Class C RFC 1918** | `192.168.0.0/16` | Blocked |
| **Link-Local** | `169.254.0.0/16`, `fe80::/10` | Blocked |
| **Cloud Metadata Services** | `169.254.169.254`, `metadata.google.internal` | Blocked |
| **Anycast / Broadcast** | `0.0.0.0`, `255.255.255.255` | Blocked |
| **Cleartext HTTP** | `http://` | Blocked (HTTPS strictly required) |

Any attempt to request a prohibited IP throws an `IOException("SSRF Security Violation: Target IP ... is private")`.

---

## 4. Safe Selector DSL (Anti-RCE)

StreamHub explicitly forbids remote execution of arbitrary code:
- **No DEX Loading**: The app will never download `.dex`, `.jar`, or `.apk` files at runtime.
- **No JavaScript Runtime**: The application does not bundle or expose V8, QuickJS, or WebView JavaScript interfaces to execute remote logic.
- **No Eval / Dynamic Scripting**: Selectors are strictly CSS selector tokens evaluated by Jsoup (e.g. `article.post`, `h2.entry-title a`).

`ConfigValidator` checks all CSS selectors against forbidden injection patterns:
```kotlin
val FORBIDDEN_PATTERNS = listOf(
    Regex("""javascript\s*:""", RegexOption.IGNORE_CASE),
    Regex("""<script""", RegexOption.IGNORE_CASE),
    Regex("""eval\s*\(""", RegexOption.IGNORE_CASE),
    Regex("""function\s*\(""", RegexOption.IGNORE_CASE),
    Regex("""exec\s*\(""", RegexOption.IGNORE_CASE),
    Regex("""__proto__""", RegexOption.IGNORE_CASE),
    Regex("""constructor""", RegexOption.IGNORE_CASE)
)
```

---

## 5. Privacy & Sensitive Data Redaction

Logcat logs are intercepted by `StreamHubLogger` and `SafeLoggingInterceptor` to ensure user privacy and token hygiene. The logger scrubs:
- `Authorization: Bearer <token>`
- `Cookie: ...` and `Set-Cookie: ...`
- Query parameters containing `key`, `token`, `secret`, `signature`, `password`
- Temporary signed streaming URLs containing expiring security credentials

---

## 6. ProGuard & R8 Obfuscation

In release builds, R8 minification and resource shrinking are enabled:
- Dead code is stripped from dependencies (Media3, Room, BouncyCastle).
- Internal class, method, and field names are obfuscated.
- Release APK contains zero debug logs (`-assumenosideeffects class android.util.Log { ... }`).
