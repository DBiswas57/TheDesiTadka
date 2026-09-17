# StreamHub Remote Configuration & Operations Guide

## 1. Architecture Overview

StreamHub fetches its provider manifest from a remote HTTPS CDN distribution point. The application implements an offline-first caching mechanism:
- If the CDN is temporarily unreachable, the app continues functioning seamlessly using its locally cached `active_manifest.json`.
- If an update fails signature verification, schema validation, or domain security checks, the app automatically preserves `last_known_good_manifest.json` and drops the corrupted payload.

```
+-------------------------------------------------------------+
|                     Deployment Pipeline                     |
|                                                             |
|  1. Author Manifest (sample-manifest.json)                  |
|  2. Validate: python validate_config.py -i manifest.json    |
|  3. Sign:     python sign_config.py -i manifest.json -k key |
|  4. Deploy:   Upload signed-manifest.json to HTTPS CDN      |
+-------------------------------------------------------------+
```

---

## 2. Configuration Schema Reference

### Top-Level Manifest Fields

| Field | Type | Description |
|---|---|---|
| `schemaVersion` | Integer | Protocol schema version (currently `1`). |
| `configVersion` | Integer | Monotonically increasing sequence number. Older versions are rejected to prevent replay/downgrade attacks. |
| `generatedAt` | Long | Unix timestamp in milliseconds when the config was compiled. |
| `expiresAt` | Long | Unix timestamp in milliseconds after which clients will flag the config for mandatory refresh. |
| `minimumAppVersion` | Integer | Minimum required `versionCode`. If the client is older, it can prompt the user for an update. |
| `recommendedAppVersion` | Integer | Recommended `versionCode` for optimal compatibility. |
| `forceUpdate` | Boolean | Remote kill switch to block playback until the app is updated. |
| `maintenanceMode` | Boolean | Remote maintenance indicator displaying a friendly maintenance screen. |
| `providers` | Array | List of declared `ProviderConfig` objects. |

---

## 3. Cryptographic Signing Workflow

### Step 1: Generate Deployment Keypair
For production deployments, generate a secure 32-byte Ed25519 keypair:
```bash
python config-tools/sign_config.py --input config-tools/sample-manifest.json --output config-tools/signed-manifest.json --gen-key
```
This produces:
- `config-tools/keys/ed25519_private.pem` (KEEP STRICTLY CONFIDENTIAL!)
- Prints the Base64 SubjectPublicKeyInfo DER public key.

> **CRITICAL SECURITY REQUIREMENT**:
> Never commit `ed25519_private.pem` to Git or package it inside an Android APK. Store it exclusively in CI/CD secrets (e.g. GitHub Secrets, HashiCorp Vault, AWS Secrets Manager).

### Step 2: Validate Manifest Schema & Security
Run the linting utility before signing:
```bash
python config-tools/validate_config.py --input config-tools/sample-manifest.json
```
The validator checks for:
- JSON syntax and schema conformance.
- Expiration timestamps.
- Prohibited IP addresses (RFC1918 subnets, `127.0.0.1`, AWS/GCP metadata `169.254.169.254`).
- Dangerous script/eval patterns inside CSS selectors.

### Step 3: Produce Canonical Signed Envelope
```bash
python config-tools/sign_config.py \
    --input config-tools/sample-manifest.json \
    --output config-tools/signed-manifest.json \
    --key config-tools/keys/ed25519_private.pem \
    --key-id "streamhub-prod-2026-v1"
```

The output `signed-manifest.json` contains:
```json
{
  "payload": "{\"configVersion\":100,...}",
  "signature": "Ab5aEG8ss0+CZSOJW9sw...",
  "keyId": "streamhub-prod-2026-v1"
}
```

---

## 4. Emergency Operations & Kill Switches

### Disabling a Problematic Provider
If a third-party site experiences an outage or technical failure, update its entry in the manifest:
```json
{
  "id": "fry99",
  "name": "Fry99 Video Portal",
  "enabled": false
}
```
Re-sign and deploy. StreamHub clients will immediately hide the provider from the home feed and provider directory without requiring an app store update.

### Emergency Maintenance Mode
If backend infrastructure requires maintenance, set:
```json
{
  "maintenanceMode": true
}
```
The app will display a clean maintenance screen and prevent unnecessary network calls.
