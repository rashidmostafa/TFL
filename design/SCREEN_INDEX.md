# TFL — Design Reference Index

58 Stitch screens, grouped by build phase. Each folder has `screen.png` (visual target) and `code.html` (Tailwind source for exact spacing/colors). Rebuild in Jetpack Compose — never WebView.

## Source of truth for design tokens
`DESIGN.md` has two conflicting color sets. **Use the values in the prose sections (Colors / Components), NOT the YAML frontmatter.**

| Token | Value | Use |
|---|---|---|
| background | `#0B0F14` | Canvas |
| surface | `#141A21` | Cards, sheets, incoming bubbles |
| surfaceContainer | `#1E2631` | Elevated cards, inputs, outgoing bubbles |
| outline | `#2A3441` (or white 8%) | 1px borders |
| outlineVariant | white 4% | Nested dividers |
| primary | `#22D3B6` / onPrimary `#042F2C` | E2EE, verified, primary actions, Nearby |
| mesh (secondary) | `#38BDF8` / on `#082F49` | Mesh / BLE / Wi-Fi Direct |
| tor (tertiary) | `#A855F7` / on `#3B0764` | Tor |
| success | `#22C55E` | Delivered, verified handshake |
| warning | `#F5A524` | Queued, unverified key, degraded |
| danger | `#EF4444` | Key mismatch, wipe, SOS |
| textPrimary | `#F8FAFC` | |
| textMuted | `#94A3B8` | |
| textDim | `#64748B` | |

Typography: Plus Jakarta Sans (headlines), Inter (body), JetBrains Mono (keys, fingerprints, hops, telemetry). Sizes/weights from DESIGN.md frontmatter `typography` block are valid.
Shapes: cards 16dp, sheets 24dp top, chips full pill, key blocks 8dp, bubbles 16dp with 4dp on the sender-side top corner.
Elevation: no drop shadows — luminance layers + 1px borders + 20% transport-color glow for critical elements.
Icons: Material Symbols Outlined (bundle the font or use equivalent vector drawables; no runtime font download).
Logo: `assets/tfl_logo.svg` → convert to a VectorDrawable + adaptive launcher icon.

## Stitch hallucinations — IGNORE these
TFL uses **no extra hardware**. Transports are only: Nearby (BLE + Wi-Fi Direct via Nearby Connections), Mesh (multi-hop over phones), Tor, Queued.
- **LoRa / 915 MHz / 868 MHz / SF7 / SF11 / antenna / SWR / dBm RF carrier** — appears in 20 screens. Remove every functional element; replace mock text with generic content.
  - Functional LoRa UI to DROP: `07-settings/network_settings` ("External LoRa Transceiver"), `04-location-safety/sos_emergency_beacon` (long-range LoRa option), `07-settings/bandwidth_mode` ("2-Hop LoRa Mesh" path → use "Mesh · 2 hops"), `04-location-safety/map` header badge, `02-chats/push_to_talk_walkie_talkie` channel label, `06-tools/collaborative_whiteboard` node badge, `04-location-safety/dead_man_s_switch_check_in` escalation option ("LoRa Mesh Relay Flood" → "Mesh flood"), `07-settings/settings` row subtitle, `07-settings/mesh_app_update` changelog.
- **Fake version strings / product names** ("SafeMesh v2.4.9", "SSS-POLYNOMIAL v2.4", etc.) — ignore; app name is **TFL**.
- **Mock data** (names, Berlin coordinates, storm bulletins, fake hashes, "Sector 7") — use only as fake preview data, never hardcode.
- **Crypto labels in mock text** ("ChaCha20-Poly1305", "AES", etc.) — the real stack is in CLAUDE.md; labels shown in UI must match what the code actually uses.
- **Remote images** (`lh3.googleusercontent.com`) — placeholders only. The app makes no network requests except through Tor; use local placeholder avatars/thumbnails.
- `unused/chats_alt_lora` — alternate Chats screen built around a LoRa radio panel. **Do not implement.** `02-chats/chats_home` is canonical.

## Known gaps
- `04-location-safety/sos_emergency_alert_received` — PNG failed to export from Stitch; use `code.html` as the reference (or re-export the PNG).

## Screens by phase

| Folder | Screens | Build phase |
|---|---|---|
| 01-onboarding | welcome, identity_creation, app_lock_setup, duress_pin_setup, recovery_key, permissions, network_setup, stealth_mode_calculator | 1 (network_setup wiring in 4) |
| 02-chats | chats_home, 1_1_chat_screen, message_long_press_menu, disappearing_messages_settings, scheduled_messages | 3 |
| 02-chats | group_chat_poll, create_poll_sheet, broadcast_channel, push_to_talk_walkie_talkie | 6 |
| 02-chats | offline_voice_call | 10 |
| 03-contacts | add_friend, friend_added, contact_profile, key_change_warning | 2 |
| 03-contacts | group_join_approval | 6 |
| 03-contacts | remote_wipe_request | 10 |
| 04-location-safety | all 8 screens | 8 |
| 05-vault | vault_unlock, vault_home, encrypted_gallery, media_viewer, time_locked_file, hidden_albums, encrypted_notes, passwords_locker | 5 |
| 05-vault | shared_albums, vault_backup_to_a_friend | 7 |
| 05-vault | shamir_secret_splitting | 10 |
| 06-tools | all 7 screens | 9 |
| 07-settings | settings, security_settings, network_settings | shell in 0, wired progressively |
| 07-settings | mesh_health_dashboard, bandwidth_mode, in_chat_translation, mesh_app_update, panic_wipe_confirmation | 10 |
