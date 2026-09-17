#!/usr/bin/env python3
"""
StreamHub Secure Remote Configuration Signer
============================================
Signs StreamHub remote configuration manifests using Ed25519 (RFC 8032).
Ensures canonical serialization so that signature verification on Android
is 100% deterministic and tamper-evident.

Usage:
  python sign_config.py --input sample-manifest.json --output signed-manifest.json [--key private.pem] [--gen-key]
"""

import argparse
import base64
import json
import os
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric import ed25519
    from cryptography.hazmat.primitives.serialization import (
        Encoding,
        PrivateFormat,
        PublicFormat,
        NoEncryption,
        load_pem_private_key,
    )
except ImportError:
    print("Error: 'cryptography' library is required. Install via: pip install cryptography", file=sys.stderr)
    sys.exit(1)


def canonicalize_json(data) -> str:
    """Produces deterministic canonical JSON (sorted keys, compact separators, UTF-8)."""
    return json.dumps(data, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def generate_keypair(key_dir: Path):
    """Generates a new Ed25519 keypair and saves it locally."""
    private_key = ed25519.Ed25519PrivateKey.generate()
    public_key = private_key.public_key()

    priv_pem = private_key.private_bytes(
        encoding=Encoding.PEM,
        format=PrivateFormat.PKCS8,
        encryption_algorithm=NoEncryption()
    )
    pub_der = public_key.public_bytes(
        encoding=Encoding.DER,
        format=PublicFormat.SubjectPublicKeyInfo
    )
    pub_b64 = base64.b64encode(pub_der).decode("ascii")

    key_dir.mkdir(parents=True, exist_ok=True)
    priv_file = key_dir / "ed25519_private.pem"
    with open(priv_file, "wb") as f:
        f.write(priv_pem)

    print(f"[+] Generated new Ed25519 private key: {priv_file}")
    print(f"[+] Public Key (Base64 DER): {pub_b64}")
    print("    -> Store this public key in Ed25519Verifier.kt or remote config verifier.")
    print("    -> WARNING: Keep ed25519_private.pem strictly SECRET. NEVER commit to Git.")
    return private_key, pub_b64


def load_or_create_key(key_path_str: str | None, gen_key: bool):
    """Loads an existing private key or generates a new one."""
    if gen_key or not key_path_str:
        key_path = Path(key_path_str) if key_path_str else Path(__file__).parent / "keys" / "ed25519_private.pem"
        if not key_path.exists():
            priv_key, _ = generate_keypair(key_path.parent)
            return priv_key
        else:
            with open(key_path, "rb") as f:
                return load_pem_private_key(f.read(), password=None)
    else:
        with open(key_path_str, "rb") as f:
            return load_pem_private_key(f.read(), password=None)


def sign_manifest(manifest_dict: dict, private_key: ed25519.Ed25519PrivateKey, key_id: str = "primary-v1") -> dict:
    """Canonicalizes and signs the manifest, wrapping into SignedPayload."""
    canonical_payload = canonicalize_json(manifest_dict)
    payload_bytes = canonical_payload.encode("utf-8")

    signature_bytes = private_key.sign(payload_bytes)
    signature_b64 = base64.b64encode(signature_bytes).decode("ascii")

    return {
        "payload": canonical_payload,
        "signature": signature_b64,
        "keyId": key_id
    }


def main():
    parser = argparse.ArgumentParser(description="StreamHub Configuration Signer")
    parser.add_argument("--input", "-i", required=True, help="Path to raw JSON manifest")
    parser.add_argument("--output", "-o", required=True, help="Path to output signed envelope JSON")
    parser.add_argument("--key", "-k", help="Path to Ed25519 PEM private key")
    parser.add_argument("--gen-key", action="store_true", help="Generate a fresh keypair")
    parser.add_argument("--key-id", default="streamhub-v1", help="Identifier for the signing key")

    args = parser.parse_args()

    # 1. Load input manifest
    with open(args.input, "r", encoding="utf-8") as f:
        manifest_data = json.load(f)

    # 2. Acquire private key
    private_key = load_or_create_key(args.key, args.gen_key)

    # 3. Sign
    signed_envelope = sign_manifest(manifest_data, private_key, args.key_id)

    # 4. Save output
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(signed_envelope, f, indent=2)

    print(f"[+] Signed configuration successfully written to: {output_path}")
    print(f"    Payload size: {len(signed_envelope['payload'])} bytes")
    print(f"    Signature: {signed_envelope['signature'][:20]}...")


if __name__ == "__main__":
    main()
