#!/usr/bin/env python3
"""
Smart GNSS — activation code issuer (manufacturer side).

You run this on YOUR computer to mint activation codes for the app you ship
with your instruments. It signs each license with your private key; the app
verifies it with the matching public key baked into the APK. No server needed —
codes work fully offline in the field.

The code format is identical to what the Android app expects:
    Base32( [2-byte payload length][payload JSON][ECDSA-P256 signature] )

Requires:  pip install cryptography

-------------------------------------------------------------------------------
Usage
-------------------------------------------------------------------------------
Generate your production keypair (ONCE — keep the private key secret):
    python smartgnss_license.py genkey --out keys/

Print the public key to paste into the app (LicenseVerifier.PUBLIC_KEY_SPKI_BASE64):
    python smartgnss_license.py pubkey --key keys/smartgnss_private.pem

Issue a perpetual, full-feature code bound to a device the customer reported:
    python smartgnss_license.py issue \
        --key keys/smartgnss_private.pem \
        --to "Customer / Unit 00123" \
        --bind DEVICE --bid SG-ABCD-EFGH-IJKL-MNOP \
        --features ALL

Issue a 1-year RTK-only subscription bound to a receiver serial:
    python smartgnss_license.py issue --key keys/smartgnss_private.pem \
        --to "Rental #7" --bind RECEIVER --bid SN20250012345 \
        --features BASE,RTK_NTRIP --days 365

Issue an unbound trial (any device), 30 days:
    python smartgnss_license.py issue --key keys/smartgnss_private.pem \
        --to "Trial" --bind ANY --features ALL --days 30
"""
import argparse
import base64
import json
import os
import sys
import time

try:
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import serialization, hashes
except ImportError:
    sys.exit("Missing dependency. Run:  pip install cryptography")

# --- Base32 (RFC 4648, no padding) — must match Base32.kt in the app ---------
_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"


def b32encode(data: bytes) -> str:
    out, buffer, bits = [], 0, 0
    for byte in data:
        buffer = (buffer << 8) | byte
        bits += 8
        while bits >= 5:
            bits -= 5
            out.append(_ALPHABET[(buffer >> bits) & 0x1F])
    if bits:
        out.append(_ALPHABET[(buffer << (5 - bits)) & 0x1F])
    return "".join(out)


# --- Feature flags — must match Feature in License.kt ------------------------
FEATURES = {
    "BASE": 1 << 0,
    "RTK_NTRIP": 1 << 1,
    "RAW_LOGGING": 1 << 2,
    "SURVEY_EXPORT": 1 << 3,
    "STATIC": 1 << 4,
    "ALL": -1,
}


def parse_features(spec: str) -> int:
    spec = spec.strip().upper()
    if spec == "ALL":
        return -1
    mask = 0
    for name in spec.split(","):
        name = name.strip()
        if name not in FEATURES:
            sys.exit(f"Unknown feature '{name}'. Known: {', '.join(FEATURES)}")
        mask |= FEATURES[name]
    return mask


def new_license_id() -> str:
    return "SG-" + base64.b32encode(os.urandom(5)).decode().rstrip("=")


def cmd_genkey(args):
    os.makedirs(args.out, exist_ok=True)
    priv = ec.generate_private_key(ec.SECP256R1())
    priv_path = os.path.join(args.out, "smartgnss_private.pem")
    pub_path = os.path.join(args.out, "smartgnss_public.pem")
    with open(priv_path, "wb") as f:
        f.write(priv.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ))
    with open(pub_path, "wb") as f:
        f.write(priv.public_key().public_bytes(
            serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        ))
    os.chmod(priv_path, 0o600)
    print(f"Private key: {priv_path}  (KEEP SECRET — never ship this)")
    print(f"Public key:  {pub_path}")
    print("\nPaste this into the app (LicenseVerifier.PUBLIC_KEY_SPKI_BASE64):\n")
    print(_pub_spki_b64(priv.public_key()))


def _pub_spki_b64(pubkey) -> str:
    der = pubkey.public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return base64.b64encode(der).decode()


def cmd_pubkey(args):
    priv = _load_key(args.key)
    print(_pub_spki_b64(priv.public_key()))


def _load_key(path):
    with open(path, "rb") as f:
        return serialization.load_pem_private_key(f.read(), password=None)


def cmd_issue(args):
    priv = _load_key(args.key)
    bind = args.bind.upper()
    if bind not in ("DEVICE", "RECEIVER", "ANY"):
        sys.exit("--bind must be DEVICE, RECEIVER, or ANY")
    if bind != "ANY" and not args.bid:
        sys.exit(f"--bind {bind} requires --bid (the device code or receiver serial)")

    exp_days = 0
    if args.days and args.days > 0:
        exp_days = int((time.time() + args.days * 86400) // 86400)

    payload = {
        "v": 1,
        "lid": args.lid or new_license_id(),
        "to": args.to,
        "bind": bind,
        "bid": (args.bid or "").replace(" ", ""),
        "feat": parse_features(args.features),
        "exp": exp_days,
    }
    # Compact, key-order-stable JSON — the app verifies over these exact bytes.
    payload_bytes = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    signature = priv.sign(payload_bytes, ec.ECDSA(hashes.SHA256()))

    frame = len(payload_bytes).to_bytes(2, "big") + payload_bytes + signature
    code = b32encode(frame)
    grouped = "-".join(code[i:i + 5] for i in range(0, len(code), 5))

    print("Activation code")
    print("  license id :", payload["lid"])
    print("  issued to  :", payload["to"])
    print("  binding    :", bind, payload["bid"] or "(any device)")
    print("  features   :", args.features.upper())
    print("  expires    :", "perpetual" if exp_days == 0
          else time.strftime("%Y-%m-%d", time.gmtime(exp_days * 86400)))
    print()
    print(grouped)

    if args.out:
        with open(args.out, "w") as f:
            f.write(grouped + "\n")
        print(f"\nSaved to {args.out}")


def main():
    p = argparse.ArgumentParser(description="Smart GNSS activation code issuer")
    sub = p.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("genkey", help="generate a new production keypair")
    g.add_argument("--out", default="keys", help="output folder")
    g.set_defaults(func=cmd_genkey)

    pk = sub.add_parser("pubkey", help="print public key SPKI base64 for the app")
    pk.add_argument("--key", required=True)
    pk.set_defaults(func=cmd_pubkey)

    i = sub.add_parser("issue", help="issue an activation code")
    i.add_argument("--key", required=True, help="your private key PEM")
    i.add_argument("--to", default="", help="customer / unit label (recorded in code)")
    i.add_argument("--bind", default="DEVICE", help="DEVICE | RECEIVER | ANY")
    i.add_argument("--bid", default="", help="device code (SG-...) or receiver serial")
    i.add_argument("--features", default="ALL", help="e.g. BASE,RTK_NTRIP or ALL")
    i.add_argument("--days", type=int, default=0, help="validity in days (0 = perpetual)")
    i.add_argument("--lid", default="", help="override license id (otherwise random)")
    i.add_argument("--out", default="", help="also write the code to this file")
    i.set_defaults(func=cmd_issue)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
