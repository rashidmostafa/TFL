---
name: Cryptographic Mesh Communicator
colors:
  surface: '#101419'
  surface-dim: '#101419'
  surface-bright: '#36393f'
  surface-container-lowest: '#0a0e13'
  surface-container-low: '#181c21'
  surface-container: '#1c2025'
  surface-container-high: '#262a30'
  surface-container-highest: '#31353b'
  on-surface: '#e0e2ea'
  on-surface-variant: '#bacac4'
  inverse-surface: '#e0e2ea'
  inverse-on-surface: '#2d3136'
  outline: '#85948f'
  outline-variant: '#3b4a46'
  surface-tint: '#37dec0'
  primary: '#51f0d1'
  on-primary: '#00382e'
  primary-container: '#22d3b6'
  on-primary-container: '#005649'
  inverse-primary: '#006b5b'
  secondary: '#7bd0ff'
  on-secondary: '#00354a'
  secondary-container: '#00a6e0'
  on-secondary-container: '#00374d'
  tertiary: '#e9ceff'
  on-tertiary: '#490080'
  tertiary-container: '#d6aaff'
  on-tertiary-container: '#6f00be'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#5efbdc'
  primary-fixed-dim: '#37dec0'
  on-primary-fixed: '#00201a'
  on-primary-fixed-variant: '#005144'
  secondary-fixed: '#c4e7ff'
  secondary-fixed-dim: '#7bd0ff'
  on-secondary-fixed: '#001e2c'
  on-secondary-fixed-variant: '#004c69'
  tertiary-fixed: '#f0dbff'
  tertiary-fixed-dim: '#ddb7ff'
  on-tertiary-fixed: '#2c0051'
  on-tertiary-fixed-variant: '#6900b3'
  background: '#101419'
  on-background: '#e0e2ea'
  surface-variant: '#31353b'
typography:
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.01em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.04em
  code-md:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.02em
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system embodies a cyber-physical, sovereign-security aesthetic engineered for zero-trust environments. Designed as an invitation-only, peer-to-peer, and Tor-routed Android application rooted in Material 3 principles, the interface communicates unflinching cryptographic resilience, technical precision, and quiet operational capability.

The visual narrative bridges military-grade hardware terminals with refined, low-cognitive-load modern utility. It eschews decorative noise in favor of functional signals: tactile containers, clear status signifiers, precise data density, and distinct transport telemetry indicators (Mesh, Tor, Direct P2P, Queued). The interface must evoke absolute control, privacy, and calm reliability under hostile network conditions or complete network blackouts.

## Colors

The system uses a calibrated dark-mode hierarchy to reduce OLED power draw and screen signature while maximizing readability under varying light conditions:

- **Canvas & Surfaces:**
  - `background`: `#0B0F14` (Deep obsidian tone, primary scaffold canvas)
  - `surface`: `#141A21` (Card, bottom sheet, and list panel background)
  - `surface-container`: `#1E2631` (Elevated cards, input wells, and active selections)
  - `outline`: `rgba(255, 255, 255, 0.08)` / `#2A3441` (Subtle boundary demarcation)
  - `outline-variant`: `rgba(255, 255, 255, 0.04)` (Nested element dividers)

- **Semantic & Transport Signaling:**
  - `primary` (`#22D3B6`): Key cryptographic actions, active encryption confirmation, verified sessions. Text on primary: `#042F2C`.
  - `secondary` / `mesh-blue` (`#38BDF8`): Ad-hoc mesh peers, Bluetooth LE / Wi-Fi Direct transports, discovery radars. Text on secondary: `#082F49`.
  - `tertiary` / `tor-purple` (`#A855F7`): Onion-routed tunnels, Tor circuit metrics, hidden service activity. Text on tertiary: `#3B0764`.
  - `status-success` (`#22C55E`): Verified handshakes, delivered payloads, active circuits.
  - `status-warning` (`#F5A524`): Unverified keys, transport degraded, high circuit latency.
  - `status-danger` (`#EF4444`): Key mismatch, bridge blocked, session revoked, emergency purge.

- **Typography & Content Contrast:**
  - `on-background` / `text-pure`: `#F8FAFC` (High-priority headers, chat titles, active text)
  - `on-surface-variant` / `text-muted`: `#94A3B8` (Metadata, unselected tabs, secondary metrics)
  - `text-dim`: `#64748B` (Inactive states, fingerprint dividers)

## Typography

The type hierarchy deploys three distinct typefaces to balance ergonomic readability with computational precision:

- **Display & Section Titles:** Set in `Plus Jakarta Sans` with compact vertical proportions and deliberate geometry, conveying clarity without appearing clinical.
- **Body & Conversations:** Driven by `Inter` for its tall x-height, neutral tone, and superior micro-legibility in both short chat bubbles and long-form encrypted notes.
- **Cryptographic & Telemetry Output:** Powered by `JetBrains Mono`. All public keys, session fingerprints, Tor node hashes, signal decibels (dBm), circuit hops, and packet timestamps must be rendered in this font to prevent ambiguous glyph reading (e.g., `0` vs `O`, `1` vs `l`).

## Layout & Spacing

The design system operates on an 8pt base grid (with a strict 4pt sub-grid for badges, chips, and tight key-value data matrices):

- **Margins & Gutters:** Mobile layouts enforce a uniform `16px` (`margin`) outer screen margin and `16px` (`gutter`) column separation.
- **Vertical Rhythm:** Conversation threads maintain `8px` inter-bubble gaps within continuous sender streams and `16px` between alternating participants. Section breaks in settings and tool diagnostics adhere to `24px` (`space-xl`).
- **Tactile Padding:** Interactive surfaces require explicit touch targets of at least `48x48dp`, with standard button and field padding defined at `12px` vertical by `16px` horizontal.
- **Adaptive Reflow:** On foldable or dual-pane tablet viewports, the layout converts to an asymmetrical split-pane system: a fixed `360dp` left navigation/thread panel paired with a fluid right workspace pane for ongoing communications, topological node maps, and cryptographic audit logs.

## Elevation & Depth

Visual hierarchy does not use diffuse drop shadows, which dilute the cyber-physical aesthetic and increase GPU load. Depth is established through structural luminance stratification and tactile containment:

1. **Base Layer (0dp):** Canvas floor `#0B0F14`.
2. **Surface Layer (1dp):** `#141A21` paired with a crisp `1px` solid border of `rgba(255, 255, 255, 0.08)` (or `#2A3441`). Used for pinned chat items, message bubble backings, and diagnostic stat panels.
3. **Elevated Containers & Sheets (2dp - 3dp):** `#1E2631` with an active `1px` border of `rgba(255, 255, 255, 0.12)`. Applied to floating interaction bars, contextual action sheets, and modal bottom sheets.
4. **Transport Emissive Accents:** In lieu of standard cast shadows, critical telemetry elements (active Tor circuit nodes, mesh hops, panic triggers) utilize a subtle inner border or a 4px soft edge bloom matching the specific transport color (`#22D3B6` for primary, `#38BDF8` for mesh, `#A855F7` for Tor) at `20%` opacity.

## Shapes

The shape system blends structured hardware cards with human-friendly touch targets:

- **Cards & Data Modules:** Standardized to `16px` border radius (`rounded-lg`), reinforcing containment while maintaining an architectural rhythm.
- **Bottom Sheets & Floating Panels:** Set to `24px` top corner radius (`rounded-xl`) to anchor modal dialogs smoothly to Android 3 navigation gestures.
- **Chips, Pills, & Action Pills:** Full stadium radius (`9999px`) for transport markers (Nearby, Mesh, Tor, Queued), status indicators, and quick-filter tabs.
- **Key Matrix Containers:** Monospace key fingerprints and hex digests use a tighter `8px` (`rounded`) interior radius to frame cryptographic strings within card bodies.

## Components

### Buttons
- **Primary:** Stadium (`9999px`) or `12px` rounded container filled with `#22D3B6`, text in `#042F2C` (JetBrains Mono or Inter SemiBold). High contrast, instantly recognized.
- **Secondary / Ghost:** Transparent background with a `1px` border in `#2A3441`, text in `#F8FAFC`. Active state transitions border to `#22D3B6`.
- **Destructive:** Container `#EF4444` at 15% opacity with an `#EF4444` border and `#EF4444` label for session wiping, emergency lockouts, and key purges.

### Chips & Transport Indicators
- **Form Factor:** `9999px` stadium pill, height `24px` to `28px`, padding `2px 10px`.
- **Mesh Chip:** Surface `#141A21`, border `#38BDF8` (50%), text `#38BDF8`, leading icon with dynamic radio wave motif.
- **Tor Chip:** Surface `#141A21`, border `#A855F7` (50%), text `#A855F7`, leading onion icon.
- **Queued / Offline Chip:** Surface `#141A21`, border `#F5A524` (50%), text `#F5A524`, leading clock/buffer glyph.
- **Verified E2EE Chip:** `#22D3B6` background (12%), border `#22D3B6` (60%), text `#22D3B6`, leading lock-shield glyph.

### Chat Bubbles & Lists
- **Incoming Messages:** Background `#141A21`, border `1px solid rgba(255, 255, 255, 0.08)`, text `#F8FAFC`. Radius: `16px` on three corners, `4px` on top-left.
- **Outgoing Messages:** Background `#1E2631`, border `1px solid rgba(34, 211, 182, 0.25)`, text `#F8FAFC`. Radius: `16px` on three corners, `4px` on top-right. Monospace delivery metadata (time + checkmarks) tucked in bottom-right.
- **Thread List Items:** Inset rows over `#0B0F14` canvas, active state shifting to `#141A21`. Left avatar displays contact icon with real-time transport ring (Teal = E2EE Active, Blue = Mesh Route, Purple = Onion Route).

### Inputs & Key Fields
- **Chat Input Bar:** Elevated `#141A21` pill or `16px` rounded container with `1px` translucent border. Integrated toggle for message expiration countdown and transport mode selector.
- **Fingerprint Verification Panels:** Dual-column monospace blocks set inside `#1E2631` with `#94A3B8` labels, segmented into 4-character blocks for visual hash comparison.

### Material 3 Navigation Bar
- Grounded at bottom with `#0B0F14` background and `1px` top border `rgba(255, 255, 255, 0.08)`.
- **5 Tabs:** Chats, Map (Mesh topology), Vault (Encrypted file locker), Tools (Network diagnostics & relays), Settings (Keys & Identity).
- **Active Indicator:** Compact stadium pill pill container (`#1E2631`) wrapping the active icon, with primary `#22D3B6` icon tint and text label. Unselected items remain `#94A3B8`.