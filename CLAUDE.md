# TFL — Private Offline-First Messenger

## What this is
Invite-only, end-to-end encrypted Android app for a small friend group.
- OFFLINE: Bluetooth / Wi-Fi Direct via Google Nearby Connections, multi-hop mesh relay, store-and-forward.
- ONLINE: Tor onion services (each phone hosts its own). NO central server.
- Encrypted vault (photos, videos, files, notes, passwords) + shared group albums synced peer-to-peer.
- Identity = keypair. No accounts, no phone numbers. Friends added only via in-person QR scan.
- Zero budget: free, open-source dependencies only. No Firebase, no FCM, no analytics, no paid APIs.
- No extra hardware. There is NO LoRa / external radio support — ignore any such UI in the designs.

## Design reference — read design/SCREEN_INDEX.md first
- Screens: design/screens/<NN-group>/<screen>/screen.png + code.html (Stitch export).
- design/SCREEN_INDEX.md defines the authoritative color tokens, the phase each screen belongs to,
  and the list of Stitch hallucinations to ignore (LoRa, fake versions, mock data, remote images).
- design/DESIGN.md: use the prose sections for colors; the YAML frontmatter colors are wrong.
  Frontmatter typography/spacing/radius values are valid.
- code.html is a VISUAL REFERENCE ONLY. Rebuild in Jetpack Compose. No WebViews.
- All tokens and shared components live in :core:designsystem and are reused everywhere.
- Fonts (Plus Jakarta Sans, Inter, JetBrains Mono) and Material Symbols are bundled as resources — no runtime downloads.
- Logo: design/assets/tfl_logo.svg → VectorDrawable + adaptive launcher icon.

## Tech stack
- Kotlin, Jetpack Compose, Material 3, single-activity, Navigation Compose
- MVVM + UDF (ViewModel + StateFlow), Hilt, Coroutines/Flow
- Room + SQLCipher; DB passphrase wrapped by an Android Keystore key
- Lazysodium (libsodium): Ed25519, X25519, crypto_box/sealed boxes, XChaCha20-Poly1305 secretstream, Argon2id
- Protocol Buffers (protobuf-kotlin-lite) wire format
- Google Nearby Connections (P2P_CLUSTER) offline transport
- Guardian Project tor-android + jtorctl; embedded Ktor server behind the onion service; outbound via Tor SOCKS
- Media3 ExoPlayer (custom DataSource for streaming decryption); CameraX + ML Kit barcode scanning for QR
- osmdroid (offline tiles); Automerge (Java bindings) for CRDT notes/lists; ML Kit on-device translation
- WorkManager + foreground service for background transport
- minSdk 26, targetSdk latest stable. Android only.

## Module layout
:app
:core:designsystem   — theme, tokens, TransportChip (Nearby/Mesh/Tor/Queued), SecureBadge, bubbles, sheets, etc.
:core:model          — domain models + protobuf definitions
:core:crypto         — ALL cryptography lives here and nowhere else
:core:database       — Room + SQLCipher, DAOs, repositories
:core:transport      — Transport interface, NearbyTransport, TorTransport, MeshRouter, Outbox, Dedup
:core:common         — utilities, dispatchers, Result types
:feature:onboarding, :feature:chats, :feature:contacts, :feature:map,
:feature:vault, :feature:tools, :feature:settings

## Core design: the Envelope
Every message, file chunk, sync op, and control command is an Envelope sealed on the sender's device:
- version, msg_id (16 random bytes), created_at, ttl, hop_count, type
- recipient_hint (short hash, not the full key), payload = signed-then-encrypted ciphertext
- Payload padded to size buckets (256 B, 1 KB, 4 KB, 16 KB, 64 KB)
Transports move opaque Envelopes only. Routing priority: Nearby direct > Mesh relay > Tor > Outbox (queue + retry).
Dedup by msg_id across all transports. Relays forward envelopes they cannot read.
Envelope is versioned so a Double Ratchet (forward secrecy) can be added later; v1 = libsodium boxes + signatures.

## Security rules (non-negotiable)
- Never implement crypto primitives — only libsodium APIs.
- Never log plaintext, keys, passphrases, or decrypted content. Logging is a no-op in release.
- Never write decrypted content to disk (no temp files, no cache). Stream-decrypt into memory/UI.
- Private keys stored encrypted, wrapped by an Android Keystore key. Vault master key = Argon2id(vault passphrase).
- FLAG_SECURE on chat, vault, and key screens. Auto-lock on background/timeout. allowBackup=false.
- Verify signatures before processing ANY envelope, sync op, wipe command, or APK update.
- No network except through Tor. No SDKs that phone home. No remote images.
- UI crypto labels must match what the code actually uses (don't copy Stitch's mock labels).
- Every crypto function has unit tests, including tamper and wrong-key cases.

## Workflow rules
- One phase at a time. Start each phase in plan mode; wait for approval before implementing.
- Ask before adding any dependency not listed above.
- Keep the app building and runnable after every step. Tests alongside code.
- After each phase: summarize what was built, what is stubbed, and how to test on real devices.
- BLE / Wi-Fi Direct features need real phones — say when manual device testing is required.
