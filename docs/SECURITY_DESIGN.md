# TFL security design (Phases 1–3)

How TFL protects its keys and data on the phone, what survives reinstalling the app or losing the
phone, how friends are added and verified, and how messages travel between nearby phones. It
describes what the code does as of Phase 3; update it whenever a phase changes the key hierarchy,
how keys are trusted, or what leaves the phone.

All cryptography lives in `:core:crypto` and uses libsodium (through Lazysodium) and the Android
Keystore only. Phase 3 adds the only networking so far: Google's Nearby Connections, phone to
phone, with no internet permission.

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
| `tfl.inbox` (Keystore) | the locked inbox (Phase 3) | what arrives while TFL is locked is already sealed to the identity key; this hides even how much arrived, and a wipe destroys it |
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

## Messaging over Nearby (Phase 3)

### Nearby Connections and Google Play services

- **Nearby Connections is part of Google Play services** (`play-services-nearby` 19.5.1), so TFL's
  offline messaging needs Play services on the phone. Nearby picks the radios: Bluetooth, Bluetooth
  Low Energy, and Wi-Fi (Wi-Fi Direct or a temporary hotspot).
- **TFL trusts nothing Nearby says or does about security.** It ignores Nearby's authentication
  tokens and endpoint info, and doesn't rely on its encryption. Nearby only moves opaque bytes; TFL's
  own handshake decides who is on the other end, and TFL's own encryption protects everything that
  crosses (both below).
- **Strategy P2P_CLUSTER, service id `app.tfl.link.v1`.** The service id is constant, so a phone
  scanning nearby can tell a TFL phone is around (and nothing more; see the next section).
- **The advertised name is 8 random characters,** from libsodium's random generator, replaced every
  15 minutes. It's never the display name or anything derived from a key.
- **Wi-Fi upgrades that would take the phone off its own Wi-Fi network are turned off:** TFL's
  frames are small enough for Bluetooth.
- **Still no INTERNET permission.** Nearby works radio to radio.

### What someone nearby with a Bluetooth scanner can learn

They can learn:
- **that a phone near them runs TFL,** from the constant service id in Nearby's advertisements;
- **a random name for it that changes every 15 minutes.** Within those 15 minutes they can tell
  it's the same phone; across a change, only radio fingerprinting might link the two;
- **while TFL is reachable, the phone's Bluetooth name is Nearby's advertisement:** random-looking
  letters that carry that random name (seen as `Ik4yMFNF…` on the Galaxy M51), shown in place of
  the phone's usual name to anyone listing Bluetooth devices. Nearby puts the usual name back when
  TFL stops, even when TFL is killed (checked on the Galaxy M20);
- **roughly how close it is, and when it comes and goes** (signal strength, presence);
- **that two TFL phones connected, when, and how much data moved.** Message sizes are padded to
  fixed steps, so the length of a text doesn't show.

They can't learn:
- **your name, your keys, or who your friends are.** None of it is broadcast. When two TFL phones
  connect, each proves it holds a secret that only you and one particular friend share; to anyone
  else, that proof is random-looking bytes that change every connection. A stranger, even one
  running a modified TFL, is disconnected without learning more than "a TFL phone that doesn't know
  me". A friend you blocked, or whose changed key you haven't verified, is treated exactly like a
  stranger.
- **what you say.** Each message is sealed to its recipient's key and signed by its sender, and the
  link between the two phones is encrypted again with keys only those two friends can derive.
- **who you talk to,** unless they watch both phones and match up the timing.
- **how to pose as a friend or replay messages.** Every message is signed and carries a unique id;
  one seen before, or dated too far off, is refused.

Also true: **Google Play services runs the radios,** so in principle Google's code sees the same
metadata as a scanner (which phones connect, when, how much). It never sees content.

### Linking with a friend

After Nearby connects two phones, they run TFL's handshake (`:core:crypto`, `link/LinkHandshake`).
The phone that asked to connect is the *initiator*.

```
pair_key(me, friend) = BLAKE2b-256("TFL-pair-key-v1\0" ‖ c2s ‖ s2c)
    where (c2s, s2c) come from crypto_kx on the two identity X25519 keys (the lower public key is the client)
    Only these two friends can compute it.

1. Both        Hello { version 1, nonce (32 random bytes), fresh X25519 public key }
               T1 = BLAKE2b-256("TFL-link-v1\0" ‖ initiator's hello ‖ responder's hello)
2. Initiator   Knock { 64 tags of 16 bytes, sorted }
               one tag per friend it may talk to: BLAKE2b-128(key = pair_key, "TFL-link-knock-v1\0" ‖ T1)
               the rest random, so the number of friends doesn't show
               T2 = BLAKE2b-256(T1 ‖ knock)
3. Responder   recomputes a tag for each of its own friends; if none matches, it disconnects, having
               sent nothing but its hello
               Answer { BLAKE2b-128(key = pair_key, "TFL-link-answer-v1\0" ‖ T2) }
   Initiator   finds which friend answered; if none, it gives up after 10 s, having sent only its
               hello and knock
               T3 = BLAKE2b-256(T2 ‖ answer)
4. Both        crypto_kx on the fresh keys (the initiator is the client), then one key per direction:
               BLAKE2b-256(key = pair_key, "TFL-link-key-v1\0" ‖ 1 or 2 ‖ T3 ‖ that direction's crypto_kx key)
               Every later frame goes through libsodium secretstream (XChaCha20-Poly1305).
5. Both        Auth { Ed25519 signature over "TFL-link-auth-v1\0" ‖ role (1 initiator, 2 responder) ‖ T3 },
               sent encrypted and checked against the friend's stored identity key
```

- **The responder reveals nothing first:** it answers only after the initiator has shown it shares
  a pair secret with one of the responder's friends. The initiator's tags change every connection.
- **Proving the identity key too:** knowing the pair secret (the X25519 key) isn't enough; step 5
  needs the friend's Ed25519 identity key.
- **The stream refuses a changed, dropped, replayed or reordered frame;** the link is then dropped
  and the phones connect again. Recorded handshakes are useless: both fresh nonces are in T1.
- **Who's left out:** blocked friends and friends with an unverified changed key aren't in the list
  a phone knocks or answers with, so they get the stranger's treatment.
- **Limits:** 64 friends per knock, 10 seconds per step, 8 connections at once. A name that failed a
  handshake isn't asked again until it changes (up to 15 minutes); a connection someone else asks
  for always gets a handshake, which tells a stranger nothing.

### The envelope

Every message, receipt, reaction, edit, delete and timer change is an envelope, sealed on the
sender's phone (`:core:crypto`, `envelope/EnvelopeCodec`):

```
Envelope (protobuf)   version 1 · msg_id (16 random bytes) · created_at · ttl (30 days)
                      hop_count (0; relaying arrives in Phase 6) · type SEALED
                      recipient_hint (2 bytes of BLAKE2b-128("TFL-recipient-hint-v1\0" ‖ their Ed25519 key))
                      payload = crypto_box_seal( sodium_pad(SignedInner, bucket), their X25519 key )
SignedInner           inner · signature = Ed25519(sender, "TFL-envelope-v1\0" ‖ inner)
Inner                 version · sender key id · recipient key id (BLAKE2b-128("TFL-key-id-v1\0" ‖ Ed25519 key))
                      msg_id · created_at (both equal to the envelope's)
                      text {text, reply_to, disappearing timer} | receipt {msg_ids} | reaction {target, emoji}
                      | edit {target, text, edit number} | delete {target} | timer {seconds}
Size buckets          256 B · 1 KB · 4 KB · 16 KB · 64 KB (a text is at most 4,000 characters: 16 KB)
```

- **Opening, in order;** any failure drops it without an answer: structure and version, the hint,
  unsealing with this phone's X25519 key, the padding, the recipient key id, the sender (a friend
  this phone may talk to), the signature, the signed id and time against the envelope's, the clock
  window, and the content's limits. Then the duplicate check (below).
- **The recipient is inside the signature,** so a friend's signed message sealed again to someone
  else is refused.
- **ttl, hop count and hint are outside the signature,** for relays in Phase 6; changing them
  changes nothing a recipient reads.
- **No forward secrecy yet.** Envelopes are sealed to long-term keys: someone who records them and
  later gets the recipient's identity secret key could open them. Message info says "No forward
  secrecy yet"; the envelope is versioned so a Double Ratchet can replace this.

### Duplicates, replays and clocks

- **Accepted only if written between 30 days ago and 10 minutes ahead** of this phone's clock.
- **Every accepted id is remembered for 30 days** (table `seen_messages`, loaded into memory while
  the transport runs). A duplicate is acknowledged again, so the sender stops sending it, but never
  applied twice. Receipts aren't remembered: applying one twice changes nothing.
- **The order of a conversation is this phone's:** when a message was written here, or when it
  arrived. A friend's clock running fast or slow can't reorder it; their signed time shows in
  message info.

### Delivery

- **Sealed when sent, queued in the `outbox` table** (sealed: this phone can't read it back), with
  the key id it was sealed to.
- **Queued → Sent → Delivered:** *sent* when Nearby reports the frame left, *delivered* when the
  friend's receipt arrives. Receipts are sealed and signed like messages, end to end, and only count
  for messages sent to that friend. There are no read receipts.
- **Retries:** a friend who links gets everything waiting at once; otherwise a message is sent again
  after at least 60 seconds without a receipt, backing off from 10 seconds, doubling, to 30 minutes.
  After 30 days it's given up and shows "Not delivered".
- **A friend's key change** holds their queue. Once the new key is verified, queued texts and timer
  changes are sealed again to it (a new install never saw the originals, so queued reactions, edits
  and deletes are dropped).
- **Scheduled messages are sealed when scheduled,** for their time, and wait in the outbox. WorkManager
  wakes the outbox then; its own database (not encrypted by TFL) records only when the next one is due.

### Replies, reactions, edits, deletes and disappearing messages

- **Only the author can edit** (within 15 minutes, by the author's clock) **or delete for everyone;**
  the receiving phone checks the author and the conversation, and a later edit number wins.
- **Delete for everyone is best effort:** a phone that never connects again keeps its copy.
- **Disappearing messages** carry their timer inside the signed text, so both phones enforce it:
  on the sender's, from sending; on the recipient's, from arrival. When it runs out the row is
  deleted from the encrypted database (`secure_delete` overwrites it), not hidden. A message whose
  time ran out while TFL was locked is never stored.
- **Copying a message** marks the clipboard entry sensitive (Android 13+ hides it from the preview).

### While TFL is locked

- **At unlock, the session hands the transport the identity's signing and key-agreement keys**
  (never the seed or the backup key). They're held in memory only (`TransportKeyring`).
- **With "Stay reachable in the background" on,** they stay through the lock, and so does a
  foreground service (type connectedDevice) with a silent notification: "TFL is running", or the
  calculator's name and icon while disguised, with a **Stop** action.
- **The keys are wiped** when TFL locks with that setting off; when Stop is tapped while locked;
  when TFL is wiped (including by the duress PIN); and when another profile unlocks: after the
  duress PIN opens the decoy, only the decoy's keys are held. A reboot clears memory: nothing runs
  until the next unlock.
- **While locked,** the transport works from the friends and queue it had at the last unlock. What
  arrives is checked in memory (plaintext never leaves memory), acknowledged, and kept **still
  sealed** in `inbox.bin`, each record encrypted again with the Keystore key `tfl.inbox`. At the next
  unlock it's filed into the database and erased. Records carry their identity's key id, so the
  real profile's wait for the real profile.
- **New-message notifications** say "New message", or the friend's name if "Show sender name" is
  on; never the content. A secure lock screen shows "New message" at most. An alert that names the
  friend is marked secret, so Android leaves it off a secure lock screen altogether: marked private
  with a public version, it would be shown in full on phones set to show all notification content
  there, and an app can't change that through its notification channels (Android resets the
  channel's lock-screen setting an app asks for). The running notification is secret too. A phone
  with no secure screen lock has no lock-screen privacy at all: Android shows notifications to
  whoever holds it. While disguised they're the calculator's. Android still shows the app's
  installed name (TFL) in every notification's header; no app can change that.

### Permissions (asked when Nearby is set up, after saying why)

| Android | Asked for | |
|---|---|---|
| 8–9 (API 26–28) | approximate location, and Location switched on | |
| 10–11 (API 29–30) | precise location, and Location switched on | Galaxy M20 |
| 12 (API 31) | Bluetooth scan (marked "never for location"), advertise, connect; precise location | Galaxy M51 |
| 12L (API 32) | Bluetooth scan, advertise, connect | |
| 13–16 (API 33–36) | the Bluetooth three, Nearby Wi-Fi devices ("never for location"), notifications | |
| 17 (API 37) | also local network | |

A refusal says what won't work; once Android stops asking, TFL points to system settings. Installed
without asking: Bluetooth and Wi-Fi state, the foreground service, and WorkManager's own (wake lock,
boot completed to reschedule its job, network state, which reads connectivity and isn't internet).
Everything else the libraries merge in is removed, and a test lists the exact set.

### Storage (schema 3)

New tables `conversations`, `messages`, `reactions`, `outbox` and `seen_messages` in each profile's
SQLCipher database, added by an automatic, tested migration from version 2. Message text exists in
plaintext only inside the encrypted database and on screen. The decoy's chats are its own.

### Debug builds only

A transport log (events only: links, handshakes, frame sizes, retries; never content or keys),
radio faults (lose the next frames, hold every frame back), fake conversations, and a simulated
nearby friend: the Test friend on an in-memory radio, running the real handshake, envelopes and
receipts. Release builds contain none of them, which release tests check.

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
- **Nearby's metadata is visible to Google Play services** (which phones connect, when, how much)
  and, over the air, to anyone scanning nearby (see "What someone nearby with a Bluetooth scanner
  can learn"). Never the content.
- **While locked with "Stay reachable" on, the transport keys are in memory.** Malware with root on
  the phone could use them. Stopping the service (or turning the setting off) removes them.
- **An author can backdate an edit:** the 15-minute edit window is judged by the author's own
  (signed) clock, as it has to be for messages that arrive late.
- **Out-of-order arrival isn't handled yet:** an edit, reaction or delete for a message that hasn't
  arrived is dropped. Direct links deliver in order; relays (Phase 6) will need to hold them.
- **WorkManager's database** records when the next scheduled message is due (nothing about it).
- **A live relay could fake "in person".** Someone relaying codes live between two people who aren't
  together (for example over two video calls) could make both phones say "verified in person". The
  keys exchanged would still be each other's real keys, so the relay learns nothing; only "in
  person" would be untrue.
- **Clocks more than 5 minutes apart** can't pair until one is corrected; the message says which way.
- **Lookalike names aren't blocked.** Invisible and direction-changing characters are refused, but
  letters from other scripts that look alike (a Cyrillic "а" for a Latin "a") are allowed. The key
  fingerprint and safety number identify a friend, not the name.
- **Debug builds only:** fake friends, key-change simulation, a test friend's QR code, fake
  conversations, radio faults, the transport log and the simulated nearby friend, for testing with
  one phone. Release builds contain none of them, which release tests check.
