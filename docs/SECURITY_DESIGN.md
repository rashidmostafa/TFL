# TFL security design (Phase 1)

How TFL protects its keys and data on the phone, and what survives reinstalling the app or losing
the phone. It describes what the code does as of Phase 1; update it whenever a phase changes the
key hierarchy.

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
| Uninstall and reinstall, or "Clear storage" | your identity, **if you have the recovery phrase** (same keys and fingerprint) | display name, PIN, duress PIN, fingerprint unlock, settings and everything else stored locally. The Keystore keys go with the app, and backups are off by design. From later phases: chats, contacts and the vault too, unless backed up to a friend (Phase 7) |
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

## Known limits (Phase 1)

- **The 6-digit PIN is strong only because of the hardware key.** Someone able to run code inside a
  rooted, unlocked phone's Keystore could try all 10⁶ PINs in hours to days.
- **The recovery phrase words exist as strings in memory** while the phrase screen is shown, until
  garbage collection. PINs never do: they're typed on TFL's own keypad into byte arrays.
- **The decoy profile hides your data from a quick look,** but a forensic examination of the phone
  could still find that a second profile exists.
- **Not yet:** key revocation or rotation, forward secrecy (the message envelope is versioned so a
  Double Ratchet can be added), and enforcement of the saved "lock when face down" and panic-trigger
  settings.
