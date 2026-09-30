# TFL security design (Phases 1–2)

How TFL protects its keys and data on the phone, what survives reinstalling the app or losing the
phone, and how friends are added and verified. It describes what the code does as of Phase 2;
update it whenever a phase changes the key hierarchy or how keys are trusted.

All cryptography lives in `:core:crypto` and uses libsodium (through Lazysodium) and the Android
Keystore only. There is no networking yet.

## Key hierarchy

```
      PIN (6 digits)                                   Fingerprint (optional; never while a duress PIN is set)
         │ Argon2id: 3 passes × 256 MiB, 16-byte salt           │ BiometricPrompt with a CryptoObject
         │ (128 MiB, or 2 passes × 64 MiB, on slow phones)       ▼
         ▼                                                 Keystore key tfl.bio (AES-256-GCM,
   PIN root (32 B) ──crypto_kdf "TFLpinvf"──► verifier     strong biometrics on every use,
         │ crypto_kdf "TFLpinkk"                           invalidated by a new fingerprint
         ▼                                                 enrolment or removing the screen lock)
   PIN key ──XChaCha20-Poly1305──►  UK  ◄──AES-GCM────────────────┘
                                  (unlock key, random 32 B, in memory only while unlocked)
                 ┌─────────────────┴───────────────────┐
                 ▼ XChaCha20-Poly1305                   ▼ XChaCha20-Poly1305
       Database key (random 32 B)            Master seed (random 32 B) ◄──► 24-word recovery phrase
                 │ SQLCipher raw key                    │ crypto_kdf (BLAKE2b), subkey id 1
                 ▼                                      ├─ "TFLsign_" ► Ed25519 identity key pair ─► fingerprint
       tfl-<random>.db                                  ├─ "TFLkex__" ► X25519 key-agreement key pair (from Phase 3)
       (identity public keys,                           └─ "TFLbkup_" ► backup key (from Phase 7)
        display name, settings)

 Duress PIN in decoy mode ─► the same pattern with its own salt, verifier and PIN key ─► UK′
                             ─► decoy database key + decoy seed (a separate, empty profile)

 lockstate.bin holds everything above as ciphertext: salts, verifiers, the wrapped UK and UK′, the
 wrapped database keys and seeds, the failed-attempt counter, the Argon2id cost and the disguise-code
 hash. The whole file is encrypted with Keystore key tfl.state (AES-256-GCM, no user authentication).
 Every Keystore key is created in StrongBox when the phone has one, otherwise in the TEE.
```

| Key | What it protects | Why it exists |
|---|---|---|
| `tfl.state` (Keystore) | the whole lock-state file | TFL's files copied off the phone are useless: PIN guessing only works on this phone's hardware |
| PIN root → verifier and PIN key | UK | the files plus the hardware key are not enough; the PIN is also needed |
| `tfl.bio` (Keystore) | UK, for fingerprint unlock | fast unlock; any change to enrolled fingerprints destroys it |
| UK (unlock key) | database key, master seed | one key for both PIN and fingerprint to unwrap, so changing the PIN only re-wraps UK |
| Database key | the SQLCipher database | random, never derived from the PIN |
| Master seed | every identity private key | the same phrase gives the same identity, which is what makes restoring work |

### Details

- **Encrypted blobs:** XChaCha20-Poly1305 with a random 24-byte nonce, stored as
  `version ‖ nonce ‖ ciphertext + tag`. The authenticated data is a purpose label (`tfl/v1/uk-pin`,
  `tfl/v1/dbkey`, `tfl/v1/seed`), so one blob can't be swapped for another.
- **Argon2id cost:** chosen once, at setup, by timing each tier on the phone and taking the costliest
  that finishes within 2 seconds. Measured: Galaxy M20 → 128 MiB (1.1 s), Galaxy M51 → 256 MiB (1.4 s).
- **Recovery phrase:** the 256-bit seed plus an 8-bit SHA-256 checksum, written as 24 words from the
  official BIP39 English word list. The words *are* the seed: it's not a crypto-wallet phrase (no
  PBKDF2 step).
- **Fingerprint:** BLAKE2b-128 over `"TFL-fingerprint-v1" ‖ Ed25519 public key`, shown as 32 hex
  characters in 8 groups.
- **Database:** SQLCipher with the raw 32-byte key (no passphrase derivation) and
  `cipher_memory_security` on. The key stays in memory only while the database is open and is wiped
  when TFL locks.
- **Calculator disguise code:** stored as a keyed BLAKE2b hash with a random key. It only opens the
  lock screen; the PIN is still needed.

### Unlocking

1. If a wait from earlier wrong PINs is still running, refuse without deriving anything.
2. Record the attempt as a failure before checking it, so killing the app mid-check can't erase it.
3. Always run both Argon2id derivations: the real PIN's, and the duress PIN's (or a dummy one when no
   duress PIN is set). Compare both verifiers in constant time. The time taken is the same whichever
   PIN matched, and whether or not a duress PIN exists.
4. Real PIN: unwrap UK, reset the counter, open the real database. Duress PIN: open the decoy
   profile, or wipe. Neither: report the failure and the next wait.

| Wrong PINs in a row | Wait before the next try |
|---|---|
| 1–4 | none |
| 5 | 30 seconds |
| 6 | 1 minute |
| 7 | 5 minutes |
| 8 | 15 minutes |
| 9 and more | 1 hour each |

Waits are measured on the phone's time since boot and its boot count: restarting the phone restarts
the wait, and changing the clock doesn't shorten it. An optional setting wipes TFL after 5, 10 or 20
wrong PINs in a row; the lock screen warns once 3 or fewer attempts remain.

### Locking and wiping

- **Auto-lock:** immediately (the default), or after 30 seconds, 1 minute or 5 minutes away from TFL;
  always immediately while the calculator disguise is on. Locking wipes UK from memory and closes the
  database. A cold start is always locked.
- **Wipe** ("Forgot PIN?", the duress PIN in wipe mode, wipe after N wrong PINs): close the database,
  **delete the Keystore keys first** so any copy of the files left on flash can never be decrypted,
  then delete TFL's files, reset the launcher icon and restart the app on a fresh Welcome screen.
- **Duress PIN, decoy mode:** opens a separate, empty profile with its own identity. Lock settings
  changed inside the decoy are stored in the decoy's own database and never reach the real lock
  state.

## What survives reinstalling or losing the phone

| Event | Survives | Lost |
|---|---|---|
| App update (same signing key) | everything | nothing |
| Uninstall and reinstall, or "Clear storage" | your identity, **if you have the recovery phrase** (same keys and fingerprint) | display name, PIN, duress PIN, fingerprint unlock, settings, friends and everything else stored locally: friends are paired again in person. The Keystore keys go with the app, and backups are off by design. From later phases: chats and the vault too, unless backed up to a friend (Phase 7) |
| Phone lost or stolen | your identity, restored on a new phone from the phrase | everything on the old phone |
| New fingerprint enrolled, or the phone's screen lock removed | PIN unlock and all data | fingerprint unlock (turn it on again after entering the PIN) |
| Forgot the PIN | your identity, through the phrase | all local data: "Forgot PIN?" wipes TFL, then you restore |
| Lost the phrase (phone still fine) | everything on this phone | any way to restore elsewhere. The phrase is shown only once, so the fix is to create a new identity |
| Duress wipe, wipe after N wrong PINs, or factory reset | your identity, through the phrase | all local data |

- **What a thief of the phone faces:** only encrypted files. They need your PIN, and can only guess
  it on that phone, against the escalating waits and the optional wipe.
- **Restoring gives the same identity on any phone.** Checked by hand: the same 24 words gave the same
  fingerprint on a Galaxy M20 and a Galaxy M51.
- **A lost phone's key can't be revoked yet.** If you think the old phone is compromised, create a new
  identity instead of restoring. Revocation arrives with remote wipe in Phase 10.

## Adding friends in person (Phase 2)

Friends are added only by scanning each other's QR codes, in person. A code holds public
information only, signed by the identity key; nothing in it can decrypt anything or sign as you.

### Pairing codes

```
QR text   = "TFL-PAIR1:" + base64url(SignedPairingCode)
SignedPairingCode { payload, signature }
  signature = Ed25519 identity key, detached, over "TFL-pairing-v1" ‖ 0x00 ‖ payload
              (the payload bytes exactly as scanned, never re-encoded; the label keeps a pairing
               signature from ever passing as any other TFL signature)
PairingPayload (protobuf)
  version 1 · display name · Ed25519 public key · X25519 public key · created at (ms)
  nonce (16 random bytes) · answers (16 bytes, or empty) · onion address (reserved, empty until Phase 4)
```

- **Checks on every scan,** in order; a refused code stores nothing and the screen says why:
  1. the TFL prefix, at most 1 KB, well formed (exact key and nonce sizes)
  2. version 1
  3. the signature is valid for the key inside the code, so the name, keys, time and answer can't
     have been changed
  4. made within 5 minutes of this phone's clock, either way
  5. a name of 1–32 characters with no invisible, control or direction-changing characters
  6. not this profile's own code
- **Your code changes every 60 seconds** (new time and nonce). Each is signed while TFL is unlocked:
  the seed is unwrapped, the identity key derived, the code signed, and both wiped straight after.
  The decoy profile's codes carry the decoy identity.
- **No secrets, ever.** A test checks that no seed or private-key byte appears in a code.

**What a photo of your code gives someone:** your display name and public keys, and the fact that
you use TFL. For 5 minutes after the code was made, they could add you on *their* phone, marked
unverified; that adds nothing to yours. They can't change the name or keys (the signature breaks),
can't use the photo after 5 minutes, and can't get between you and a friend unless that friend
scans the photographer's own code in person, under the photographer's name.

### Verified in person: three scans

```
        Alice's phone                                  Bob's phone
 1.  shows A0 ───────────── Bob scans it ────────────► saves Alice (unverified), shows B1 = "answers A0"
 2.  scans B1 ◄──────────── Alice scans it ─────────── shows B1
     B1 answers A0: Bob is verified in person; shows A1 = "answers B1"
 3.  shows A1 ───────────── Bob scans it ────────────► A1 answers B1, which answered A0:
                                                       Alice is verified in person, on both phones
```

- **The rule.** A phone marks a friend "verified in person" only when it scanned their code *and*
  that code answers a code this phone showed in the last 5 minutes. Both scans happened, between
  these two phones, within the window. The answer is inside the friend's signed code, so nobody
  else can add or forge it.
- **The answer** is `crypto_generichash` (BLAKE2b, 16 bytes) of `"TFL-pairing-answer-v1" ‖ 0x00 ‖
  payload` of the code answered. A phone remembers the codes it showed in memory only, per profile,
  timed by time since boot, so setting the clock can't stretch the 5 minutes.
- **Why three scans:** QR codes travel one way, so the phone whose code was scanned first learns it
  was scanned back only from an answer. If the third scan never happens, that phone keeps the
  friend unverified, and the screens say whose turn it is.
- **Scanning a friend again** refreshes their name and can verify them, but never lowers their status.
- **Same name, different key:** "You already have Elena, with a different key. Same person?" Yes
  records a key change (below); no adds a second friend.

### Safety numbers

```
digest = crypto_generichash(60 bytes, "TFL-safety-number-v1" ‖ 0x00 ‖ lower key ‖ higher key)
         (the two Ed25519 identity public keys, sorted, so both phones compute the same bytes)
number = each 5 bytes of the digest, mod 100,000: 12 groups of 5 digits (about 199 bits)
code   = "TFL-SN1:" + base64(protobuf { version 1, digest })
```

- Both phones show the same number and code exactly when each holds the other's real key.
- **Scanning the friend's matching code** verifies them ("by safety-number code"). A mismatch is a
  red warning and changes nothing. **Mark as verified** after reading the groups aloud, behind a
  confirmation ("only if every group matches").
- The X25519 keys aren't in the number: each is bound to its Ed25519 key by the signed pairing code.

### Key changes

- When a known friend presents a different identity key (in Phase 2: "same person" when pairing,
  or the debug simulation; later, the transports), the old key goes to their key history, the new
  one becomes current, and the friend becomes **unverified** and flagged.
- **Sending rule,** enforced by every transport from Phase 3 and tested now:
  `canSend = !blocked && !(keyChanged && !verified)`.
- The warning shows both keys, with when and how each arrived, and offers **Verify again** (safety
  numbers, or pairing in person), **Block** and **Not now**. There is deliberately no "trust the new
  key anyway": verifying again is the only way back.

### Storage and the camera

- **Where friends live:** tables `contacts` (public keys, verification with how and when, first
  seen, last paired, key-changed time, nickname, blocked) and `contact_keys` (previous keys) in the
  SQLCipher database, schema version 2, migrated automatically from version 1. Each profile has its
  own; the decoy's are separate.
- **Deleting a friend** removes their keys and key history. `PRAGMA secure_delete = ON` overwrites
  deleted rows inside the encrypted file instead of leaving them in free pages.
- **CAMERA** is asked for only when Scan opens, after saying what it's for. Refusing leaves My QR
  working, and phones without a camera can still install TFL.
- **Camera frames are read in memory and dropped.** ZXing decodes the frame's brightness plane on a
  background thread, and nothing is saved or sent. The preview is drawn inside TFL's window, so
  FLAG_SECURE covers it too.
- **Every code shown is read back first,** at three sizes. ZXing can't read about 1 in 40 codes this
  dense as drawn, so a failing code is redrawn with another QR mask (same data, different pattern).

## Known limits

- **The 6-digit PIN is strong only because of the hardware key.** Someone able to run code inside a
  rooted, unlocked phone's Keystore could try all 10⁶ PINs in hours to days.
- **The recovery phrase words exist as strings in memory** while the phrase screen is shown, until
  garbage collection. PINs never do: they're typed on TFL's own keypad into byte arrays.
- **The decoy profile hides your data from a quick look,** but a forensic examination of the phone
  could still find that a second profile exists.
- **Not yet:** key revocation or rotation, forward secrecy (the message envelope is versioned so a
  Double Ratchet can be added), and enforcement of the saved "lock when face down" and panic-trigger
  settings.
- **A live relay could fake "in person".** Someone relaying codes live between two people who aren't
  together (for example over two video calls) could make both phones say "verified in person". The
  keys exchanged would still be each other's real keys, so the relay learns nothing; only "in
  person" would be untrue.
- **Clocks more than 5 minutes apart** can't pair until one is corrected; the message says which way.
- **Lookalike names aren't blocked.** Invisible and direction-changing characters are refused, but
  letters from other scripts that look alike (a Cyrillic "а" for a Latin "a") are allowed. The key
  fingerprint and safety number identify a friend, not the name.
- **Debug builds only:** fake friends, key-change simulation and a test friend's QR code, for testing
  with one phone. Release builds contain none of them, which a release test checks.
