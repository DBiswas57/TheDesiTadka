# TheDesiMedia — Remote Configuration Engine & Security Model

## Overview
TheDesiMedia incorporates an enterprise-grade, cryptographically verified remote configuration system. It allows adding, updating, enabling, and disabling content providers across devices without requiring an APK recompilation or store update, while strictly preventing arbitrary remote code execution.

---

## 1. Core Security Invariant: Configuration is DATA Only
Under no circumstances does TheDesiMedia execute remote code.
The remote configuration payload contains **strictly structured metadata**:
- Provider identity and display metadata
- HTTPS base URLs
- Navigation routes and pagination formats
- Declarative CSS/HTML selector rules (CSS queries and attribute names)
- Capability and compliance declarations

**Blocked entirely**:
- No remote Kotlin or Java code
- No DEX files or classloaders
- No native `.so` binaries
- No arbitrary JavaScript or WASM execution
- No dynamic shell or script invocation

---

## 2. Cryptographic Ed25519 Signing Pipeline

```
 Remote Host / GitHub CDN
         ↓
  Signed Envelope JSON
  {
    "payload": "{...canonical JSON manifest...}",
    "signature": "<base64 Ed25519 64-byte signature>"
  }
         ↓
  NetworkClient.fetchString(remoteUrl)
         ↓
  Ed25519Verifier.verify(payload, signature, embeddedPublicKey)
         ↓ [Signature Valid]
  ConfigValidator.validate(manifest)
         ↓ [Schema, Version & SSRF Passed]
  ConfigCache.saveValidatedConfig(manifest, markAsLastKnownGood = true)
         ↓
  ProviderEngine.updateFromManifest(manifest)
```

### Key Security Attributes:
- **Algorithm**: Ed25519 (RFC 8032), 256-bit elliptic curve signature with SHA-512.
- **Embedded Public Key**: Hardcoded 32-byte public key in `Ed25519Verifier.kt`.
- **Private Key**: Kept exclusively in secure CI/CD secrets (e.g. GitHub Repository Secret `CONFIG_SIGNING_PRIVATE_KEY`). **Never** bundled inside the APK or Git repository.

---

## 3. Configuration Validation & Anti-SSRF Rules
Before any configuration is accepted into the runtime engine, `ConfigValidator` runs rigorous security audits:

1. **Rollback & Replay Prevention**:
   Incoming `configVersion` must be `>= currentVersion`. Rollbacks to older, potentially vulnerable configs are rejected.
2. **Expiration Enforcement**:
   Manifests include an `expiresAt` timestamp. Expired configurations are immediately rejected.
3. **Anti-SSRF Guardrails (`UrlSecurityValidator`)**:
   Every provider `baseUrl` must use `https://` and is checked against IP/domain blocklists:
   - `localhost`, `127.0.0.1`, `::1`
   - `0.0.0.0`, `169.254.169.254` (Cloud metadata endpoints)
   - RFC 1918 private subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
   - Carrier-grade NAT (`100.64.0.0/10`)
   - Link-local, multicast, and `.local`, `.internal`, `.lan`, `.home` domains.
4. **Selector Sanitization**:
   Selector strings are scanned for script injection attempts (`javascript:`, `<script`, `eval(`, `document.cookie`, `window.`, `__proto__`). Any match triggers an immediate configuration rejection.

---

## 4. Rollback & Last-Known-Good Fallback
The application preserves two persistence tiers:
- `current_manifest.json`: Active working manifest.
- `last_known_good_manifest.json`: Verified stable manifest.

If a remote configuration fails verification or crashes the engine, `ConfigRepository.rollbackToPrevious()` instantly restores the last known good configuration without disruption to the user experience.
