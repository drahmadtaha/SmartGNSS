# Smart GNSS

An Android app to control GNSS receivers, built to ship with your instruments.

**Connections:** USB-serial · Bluetooth classic (SPP) · Bluetooth Low Energy
(Nordic UART) · Wi-Fi / TCP.

**Positioning & geodesy:**
- Live NMEA solution (fix quality, sats, DOPs, correction age)
- Coordinate systems: UTM zones, **Palestine Grid 1923**, **Israeli TM (ITM)** —
  add your own belts in `core/geo/CoordinateSystem.kt`
- Datum transforms (7-parameter Helmert) and Transverse Mercator projection
- **ENH output** (grid East/North + ellipsoidal and orthometric height)
- **Geoid models**: none / constant / loadable **GTX grid** (EGM2008 or local)
- **Sky plot** of satellites by azimuth/elevation/SNR

**RTK:** NTRIP client streaming RTCM to the receiver, with **mountpoint
discovery** (source-table browsing) and VRS GGA uplink.

**Field work:** survey point recording + CSV export, and **stake-out**
navigation to a target grid point (ΔE/ΔN, distance, bearing, direction arrow).

**Licensing:** an **activation-code system that you alone control** — offline,
signed, device/receiver-bound, with feature tiers and expiry.

*Roadmap (not yet built): CAD / line stake-out, coordinate-system manager UI,
raw-observation (RINEX) logging, background scanning for BLE/Wi-Fi receivers.*

> This is a clean, from-scratch project — your product, your code, your keys.
> It does not contain or depend on any other vendor's app.

---

## What's inside

```
SmartGNSS/
├─ app/                         Android app (Kotlin + Jetpack Compose)
│  └─ src/main/java/com/smartgnss/
│     ├─ MainActivity.kt        activation gate + bottom-nav scaffold
│     ├─ core/transport/        USB-serial & Bluetooth links to the receiver
│     ├─ core/nmea/             NMEA 0183 parser (GGA/GSA/GSV/RMC/VTG)
│     ├─ core/ntrip/            NTRIP v1 client → RTCM to the receiver
│     ├─ core/license/          ← YOUR activation system (offline, signed)
│     ├─ core/survey/           point recording + CSV export
│     └─ ui/screens/            Status, Connect, Corrections, Survey, License
└─ licensing-tools/             ← runs on YOUR computer, never ships
   ├─ smartgnss_license.py      generate keys + issue activation codes
   └─ README.md                 full licensing workflow
```

## The activation system (your main requirement)

You control who can run the app and what they get, **without any server**:

1. You generate a keypair once. The **private key stays with you**; the
   **public key is compiled into the app**.
2. Each customer reads their **device code** off the app's *License* screen and
   sends it to you.
3. You run `smartgnss_license.py issue …` to mint a signed **activation code** —
   bound to that device (or to a receiver serial), with an **expiry** and
   **feature set** you choose.
4. The customer pastes the code once. The app verifies it **offline** against the
   public key, every launch. No internet, works in the field.

Because the app holds only the public key, **nobody but you can create a valid
code** — not even someone holding the full source. See
[`licensing-tools/README.md`](licensing-tools/README.md) for the full workflow.

> ⚠️ Before shipping, replace the sample key. Run `genkey`, paste your public key
> into `LicenseVerifier.PUBLIC_KEY_SPKI_BASE64`, and rebuild. The sample key in
> this project is public.

Feature flags (`BASE`, `RTK_NTRIP`, `RAW_LOGGING`, `SURVEY_EXPORT`, `STATIC`)
let you sell tiers — e.g. a base license plus a paid RTK upgrade. Gating is
already wired: the Corrections screen and CSV export check the license.

## Building

Requires **Android Studio** (Koala or newer) with the Android SDK.

1. Open the `SmartGNSS/` folder in Android Studio.
2. Let it sync Gradle (it fetches dependencies and generates the Gradle wrapper).
   - From a terminal instead: `gradle wrapper` then `./gradlew assembleDebug`.
3. Run on a device (an emulator has no USB/Bluetooth receiver, but the app and
   activation flow still run).

The release APK is what you install on / ship with your instruments:
`./gradlew assembleRelease` (sign it with your own keystore).

### Get an APK without installing Android Studio (GitHub builds it for you)

A ready CI workflow is included at `.github/workflows/build.yml`. It compiles the
APK on GitHub's servers — you install nothing locally.

1. Create a new GitHub repository and push this project to it:
   ```
   git init && git add . && git commit -m "Smart GNSS"
   git branch -M main
   git remote add origin https://github.com/<you>/SmartGNSS.git
   git push -u origin main
   ```
2. On GitHub open the **Actions** tab → the **Build APK** run.
3. When it finishes (a few minutes), download the **SmartGNSS-debug-apk**
   artifact. That's your installable `app-debug.apk`.

You can re-run it any time from Actions → *Build APK* → **Run workflow**.

> Note: this project was authored without a local Android SDK, so it has not been
> compiled here — the licensing crypto was verified independently, but expect to
> do a first build + run in Android Studio and resolve any environment-specific
> SDK/version nudges Studio suggests.

## Connecting to a receiver

- **USB:** plug the receiver/data cable in; it appears on the *Connect* screen
  (FTDI / CP210x / CH340 / PL2303 cables are recognised — add your own chip's
  VID/PID in `res/xml/usb_device_filter.xml`). Default baud is 115200
  (`UsbSerialTransport`).
- **Bluetooth:** pair the receiver in Android Settings first, then pick it on the
  *Connect* screen (classic SPP).

The receiver must output standard NMEA. To send it configuration commands, use
`ReceiverViewModel.sendCommand(...)` (wire a console screen to it if you want one).

## RTK via NTRIP

On the *Corrections* screen, enter your caster host/port/mountpoint/credentials
and press start. The app streams RTCM3 to the receiver and pushes the receiver's
GGA back up (VRS). Watch the *Status* screen go to **RTK Fixed**.

## Roadmap ideas (not yet built)

- QR scanning for activation codes and receiver device codes
- RINEX / raw observation logging for post-processing (ties into your Bernese /
  RTKLIB workflows)
- Coordinate-system / projection transforms for cadastral output
- Receiver config console UI and firmware-command presets

---

Built as a starting point you own outright. The licensing module is the piece to
treat carefully: keep your private key backed up and secret.
