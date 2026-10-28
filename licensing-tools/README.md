# Smart GNSS — licensing tools (manufacturer side)

This folder is **yours**. It never ships inside the APK. It holds the private
key that signs activation codes and the script that issues them.

## How the activation system works

```
   YOU (factory)                              APP (on the instrument)
   ─────────────                              ───────────────────────
   private key  ──signs──►  activation code  ──►  public key verifies
   (secret)                 (offline, field-ready)     (baked into APK)
```

* The app contains **only the public key**, so it can *check* a code but can
  never *create* one. Only you can mint codes.
* Verification is **100% offline** — no server, no internet. Essential for field
  survey work.
* Each code can be bound to one unit, carry an expiry, and unlock specific
  features — so you control exactly what each customer gets.

## One-time setup

1. Install the dependency:
   ```
   pip install cryptography
   ```
2. Generate **your own** production keypair (do this once, keep the private key
   safe and backed up — if you lose it you can't issue codes for the installed
   public key):
   ```
   python smartgnss_license.py genkey --out keys/
   ```
3. Copy the printed public key into the app, replacing the sample value in
   `app/src/main/java/com/smartgnss/core/license/LicenseVerifier.kt`
   (`PUBLIC_KEY_SPKI_BASE64`). Rebuild the APK. **Do this before shipping** — the
   sample key in the project is public, so anyone could issue codes against it.

> `keys/` is in `.gitignore`. Never commit or distribute `smartgnss_private.pem`.

## Issuing codes

The customer reads their **device code** off the app's Activation screen
(`SG-XXXX-XXXX-XXXX-XXXX`) and sends it to you. You issue a matching code:

```
# Perpetual, all features, tied to that device
python smartgnss_license.py issue --key keys/smartgnss_private.pem \
    --to "Acme Survey / Unit 00123" \
    --bind DEVICE --bid SG-ABCD-EFGH-IJKL-MNOP \
    --features ALL

# 1-year RTK subscription tied to a receiver serial number instead
python smartgnss_license.py issue --key keys/smartgnss_private.pem \
    --to "Rental 7" --bind RECEIVER --bid SN20250012345 \
    --features BASE,RTK_NTRIP --days 365

# 30-day trial that runs on any device
python smartgnss_license.py issue --key keys/smartgnss_private.pem \
    --to "Trial" --bind ANY --features ALL --days 30
```

Give the resulting code to the customer (text, email, or print it as a QR for
them to scan). They paste/scan it on the Activation screen — done.

## Binding modes

| `--bind`   | Tied to                        | Use for                                |
|------------|--------------------------------|----------------------------------------|
| `DEVICE`   | the phone/tablet (ANDROID_ID)  | app sold per handheld                   |
| `RECEIVER` | the GNSS receiver serial       | license follows the instrument you make |
| `ANY`      | nothing (expiry only)          | trials, demos                           |

## Feature flags

`BASE`, `RTK_NTRIP`, `RAW_LOGGING`, `SURVEY_EXPORT`, `STATIC`, or `ALL`.
These map 1:1 to `Feature` in `License.kt`; add your own bits in both places.
