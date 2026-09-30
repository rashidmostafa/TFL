# TFL

Invite-only, end-to-end encrypted, offline-first Android messenger for a small group of friends.
See [CLAUDE.md](CLAUDE.md) for the architecture and security rules, and
[design/SCREEN_INDEX.md](design/SCREEN_INDEX.md) for the design reference.

**Status: Phase 2 (contacts and trust).** Onboarding creates or restores a real identity
(Ed25519/X25519 keys from a 24-word recovery phrase), everything local is stored in an encrypted
database, and the app is protected by a PIN lock with fingerprint unlock, a duress PIN, brute-force
delays, an optional wipe after failed attempts, and a calculator disguise. Friends are added in
person by scanning each other's QR codes, verified by three scans or by comparing safety numbers,
and a changed key is flagged until it's verified again. There is still no networking: no INTERNET
permission, no transport, and the chat, map, vault and tools tabs run on fake data.

## Setup

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin works; Gradle and AGP run on it, and the code targets Java 17 |
| Android SDK | platform 37, build-tools 37.0.0, platform-tools | Android Studio installs these, or use the command-line tools |
| Python 3 + fontTools | 3.10+ | Only needed to regenerate fonts and icons (`tools/fonts`) |

Point Gradle at the SDK in a `local.properties` file at the repo root (it is gitignored):

```properties
sdk.dir=/home/you/Android/Sdk
```

Gradle itself comes from the wrapper (`./gradlew`), pinned to 9.8.0 by checksum.

## Commands

```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk (installs as app.tfl.debug)
./gradlew assembleRelease               # R8-minified; signed only if keystore.properties exists
./gradlew installDebug                  # onto a connected phone (USB debugging on)

./gradlew test                          # all JVM tests: unit, Robolectric UI, and release-variant tests
./gradlew recordRoborazziDebug          # re-record golden screenshots (src/test/screenshots)
./gradlew verifyRoborazziDebug          # fail on any visual change against the goldens
./gradlew lint
./gradlew connectedDebugAndroidTest     # on-device tests; needs a phone or emulator (see below)
```

Debug builds add a **Developer** section at the bottom of Settings: a design-system catalog, an
"Allow screenshots" switch (off at every launch), an **Argon2id benchmark** that times the PIN key
derivation on the phone, and **Wipe now**. Release builds have none of these.

`connectedDebugAndroidTest` runs the checks that need real hardware: libsodium's known answers on
Android, the Keystore (and whether it is StrongBox or TEE), SQLCipher's on-disk encryption, and an
app smoke test. The smoke test wipes the debug app's TFL data first, and Gradle uninstalls the debug
app afterwards, so do manual testing after it.

## Modules

```
:app ─┬─ :feature:chats ──────┐
      ├─ :feature:map ────────┤
      ├─ :feature:vault ──────┼─ :core:designsystem ── :core:model (models, protobuf wire formats)
      ├─ :feature:tools ──────┤   :core:common (logging)
      ├─ :feature:settings ───┤
      ├─ :feature:contacts ───┤   (friends, pairing codes, safety numbers; CameraX + ZXing)
      └─ :feature:onboarding ─┘
            │
            └─ :core:session ─┬─ :core:crypto    (libsodium, Keystore, PIN vault, pairing codes — all crypto lives here)
               (lock gate)    └─ :core:database  (Room + SQLCipher)

Empty until its phase:   :core:transport
Tests only:              :core:testing (JVM libsodium, fake Keystore, plain Room files, SessionFixture)
```

Features never depend on each other; `:app` wires navigation between them. Build configuration lives
in `build-logic/` as convention plugins (`tfl.android.feature`, `tfl.hilt`, …), and every version is in
`gradle/libs.versions.toml`.

## Design system

- Colours come from the token table in `design/SCREEN_INDEX.md` (`TflTheme.colors`). The app is dark-only.
- Fonts (Inter, Plus Jakarta Sans, JetBrains Mono) and Material Symbols are bundled; nothing is downloaded at runtime.
- Icons are a subset of Material Symbols Outlined. To add one, append its name to
  `core/designsystem/material-symbols.txt` and run `python3 tools/fonts/build_fonts.py`, which
  regenerates the font files and `MaterialSymbols.kt`. The script pins every source file by commit and SHA-256.

## Security model (Phases 1–2)

The full design, with what each key protects and the unlock and wipe steps, is in
[docs/SECURITY_DESIGN.md](docs/SECURITY_DESIGN.md).

```
PIN ──Argon2id (256 MiB, or 128/64 on slow phones)──► verifier + PIN key ──► unlock key (random)
Fingerprint ──Keystore key (biometric, per use)────────────────────────────► unlock key
unlock key ──► database key (random) ──► SQLCipher database (identity public keys, settings)
unlock key ──► master seed ◄──► 24-word recovery phrase
master seed ──crypto_kdf──► Ed25519 identity key (fingerprint) · X25519 key · backup key
lockstate.bin (all of the wrapped keys above) ──► encrypted with a Keystore key (StrongBox or TEE)
```

- **Same phrase, same identity.** Restoring the phrase on a new phone gives the same keys and
  fingerprint. Nothing else is restored: PIN, settings and (later) messages stay on the old phone.
- **Uninstalling, "Clear storage", "Forgot PIN?", a duress wipe and wipe-after-N** all delete
  everything local. Only the recovery phrase brings the identity back.
- **A new fingerprint enrolment** turns fingerprint unlock off (the PIN still works).
- **The duress PIN** opens an empty decoy profile or wipes TFL, and takes exactly as long to check as
  the real PIN. Fingerprint unlock is unavailable while a duress PIN is set.
- **The recovery phrase uses the BIP39 English word list** but isn't a crypto-wallet phrase: the 24
  words encode the 32-byte seed directly.
- **Pairing codes** carry your name and public keys, signed with your identity key, and expire after
  5 minutes; your screen shows a new one every minute. A friend is "verified in person" when both
  phones scanned each other within 5 minutes (three scans), or after comparing **safety numbers**
  (60 digits from both identity keys, identical on both phones).
- **A changed key** moves the old one to the friend's key history and marks them unverified: sending
  to them stays paused (from Phase 3) until the new key is verified.

## Testing pairing on phones

One phone and a laptop cover every scanning path (debug builds); the laptop screen plays the
friend's phone. Turn on **Settings → Developer → Allow screenshots**, open **Settings → Developer →
Test friend QR**, copy the phone's screen to the laptop and open it there, then scan it with the
phone:

```bash
adb exec-out screencap -p > friend.png
```

- **They scan you first:** open **Add friend → My QR**, then Test friend QR with "Answer my latest
  code" on. Scanning it from **Add friend → Scan** verifies Test friend in person.
- **You scan first:** with the switch off, scanning adds Test friend unverified and your answer
  appears. Switch it on (it now answers your answer) and scan again: both are verified.
- **Safety numbers:** Test friend QR → **Safety-number code**, scanned from Test friend's profile →
  Compare safety numbers → Scan theirs. It matches; **Wrong key** shows the mismatch warning.
- **Same person, new key:** the test friend gets a new key whenever TFL restarts, so pairing again
  after a restart asks whether it's the same person.

Without aiming the camera at all, each Scan tab in debug builds has a field that takes a code's text
(`adb shell input text '…'`), so a code read from a screenshot can be fed in over adb.

With two phones, each shows **My QR** and scans the other's, three scans in all, as the screens ask.
**Settings → Developer → Add fake friends** fills the list with one friend in every state, and each
friend's profile has **Simulate key change**.

## Dependencies

`gradle/verification-metadata.xml` pins the SHA-256 of every dependency and plugin, so Gradle refuses
anything unexpected. After an approved dependency change, regenerate it:

```bash
./gradlew --no-configuration-cache --write-verification-metadata sha256 \
    assembleDebug assembleRelease test lint assembleDebugAndroidTest
```

Review the diff before committing it.

## Release signing

Release builds are signed only when a `keystore.properties` file exists at the repo root (gitignored).
They never fall back to the debug key. Create the key once:

```bash
keytool -genkeypair -v -keystore tfl-release.jks -alias tfl -keyalg RSA -keysize 4096 -validity 36500
```

```properties
# keystore.properties
storeFile=tfl-release.jks
storePassword=…
keyAlias=tfl
keyPassword=…
```

**Back up the keystore and its passwords somewhere safe and offline.** Android only installs an
update signed with the same key, so if the key is lost, friends have to uninstall TFL (losing their
data) to move to a new one.
