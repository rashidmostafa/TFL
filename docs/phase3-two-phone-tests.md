# Phase 3: two-phone test plan

Messaging over Nearby needs two real phones: Bluetooth and Wi-Fi Direct can't be tested on the JVM
or in an emulator. This plan is written for a **Galaxy M51 (Android 12)** and a **Galaxy M20
(Android 10)**, the two phones Android's rules differ most between. Each step says what to do and
what should happen. Anything that doesn't happen as written is a bug: note the step number.

## Before you start

1. Build and install the debug app on both phones: `./gradlew installDebug` with each phone
   connected (or `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`).
2. Onboard both phones with different names (for example **M51** and **M20**). Note both PINs.
3. On both: **Settings → Developer → Allow screenshots**, so screenshots and screen recordings
   work while testing (TFL blocks them otherwise). **Settings → Developer → Transport log** shows
   what the transport does; nothing in it is message content.
4. Keep both phones unlocked and within a few metres of each other unless a step says otherwise.
   Mobile data and Wi-Fi internet don't matter: TFL never uses the internet.
5. Samsung phones may put apps to sleep in the background. If messages stop arriving while TFL is
   locked, set Settings → Apps → TFL → Battery to **Unrestricted** and note it: it tells us whether
   the foreground service alone is enough on that phone.

## 1. Setting up Nearby

**M51 (Android 12)**
1. Open Chats. *Expected:* the status line says "Nearby needs setting up", with a **Set up Nearby**
   button.
2. Tap it. *Expected:* the first step explains that Android will ask for "Nearby devices" and
   precise location, and why.
3. Tap **Allow**, then **Don't allow** on Android's dialog. *Expected:* "Not allowed. Nearby can't
   find friends until you allow it." and **Allow** again.
4. Tap **Allow** and refuse again. *Expected:* "Android won't ask again" and **Open settings**.
5. Tap **Open settings** and allow *Nearby devices* and *Location* (choose **Precise**). Go back.
   *Expected:* the step shows "All set"; if Bluetooth is off, the Bluetooth step offers
   **Bluetooth settings**.
6. With everything done: "Nearby is ready". **Done** returns to Chats, which now says "0 friends
   nearby".
7. *Expected:* a silent notification, collapsed at the bottom of the shade: "TFL is running", with
   **Stop**. It never shows on the lock screen.

**M20 (Android 10)**
1. Open Chats → **Set up Nearby**. *Expected:* the step asks for Location, and says TFL uses it
   only to find friends' phones.
2. Allow Location ("while using the app" is enough).
3. Switch Location off in quick settings. *Expected:* a **Turn on Location** step appears (Android
   10 finds Bluetooth devices only with Location on). Switch it on: the step shows "All set".
4. *Expected:* "Nearby is ready", and the running notification as on the M51.

## 2. Pairing

1. Pair the phones in person, as in Phase 2: **Add friend → My QR** on one, **Scan** on the other,
   three scans in all, until both show the other as **Verified**.
2. Wait up to a minute. *Expected:* both Chats screens say "1 friend nearby"; on the friend's
   profile and in the conversation, "Nearby now". Transport log: "Linked with friend #…".

## 3. Messages both ways

1. On the M51, open the M20's profile → **Message**, and send "hello from M51".
   *Expected:* the bubble shows a clock (queued) for an instant, then ✓ (sent), then ✓✓
   (delivered), and a **Nearby** chip. On the M20: a "New message" notification (unless its chat is
   open), the message in the chat, with a Nearby chip.
2. Reply from the M20. *Expected:* the same, the other way.
3. Long-press a message → **Info**. *Expected:* Written, Sent and Delivered times (to the second),
   "Travelled by Nearby", a message ID, and "No forward secrecy yet".
4. Send a long message (a few paragraphs) and a single emoji. *Expected:* both arrive intact.

## 4. Out of range, then back

1. On the M20, turn on airplane mode (Bluetooth and Wi-Fi off). *Expected within a minute:* the
   M51 shows "Not in range" in the conversation header.
2. Send three messages from the M51. *Expected:* each stays **Queued** with the clock; the composer
   says "Waits until you meet".
3. On the M20, turn airplane mode off. *Expected within about 1½ minutes* (the M51 looks every
   minute while something waits): all three arrive on the M20, in order, and turn ✓✓ on the M51.

## 5. No internet at all

1. On both phones: airplane mode on, then Bluetooth and Wi-Fi back on by hand (no SIM data, no
   Wi-Fi network joined).
2. Chat both ways. *Expected:* works exactly as in step 3.

## 6. Replies, reactions, edits and deletes

1. On the M20, long-press an M51 message → **Reply**, and send a reply. *Expected on the M51:* the
   reply quotes the original.
2. On the M51, long-press that reply → react 👍. *Expected on the M20:* 👍 under its message. Tap
   👍 again on the M51: it's removed on both.
3. On the M51, long-press one of its own recent messages → **Edit** ("… minutes left"), change it,
   save. *Expected on both:* the new text, marked "edited".
4. Try **Edit** on a message from the M20. *Expected:* not offered.
5. On the M51, long-press one of its messages → **Delete for everyone** → confirm. *Expected on
   both:* "Message deleted".
6. On the M20, **Delete for me** on any message. *Expected:* gone on the M20 only.

## 7. Disappearing messages

1. On the M51, open the conversation's timer (the timer button in the header) → **5 minutes** →
   **Set timer to 5 minutes**. *Expected on both:* a line "… set disappearing messages to 5
   minutes", and the header shows the timer.
2. Send a message from each phone. Note the times.
3. *Expected:* each phone erases its sent message 5 minutes after sending, and its received message
   5 minutes after it arrived. Nothing is left in the chat, the chats list or message info.
4. Turn the timer off from the M20. *Expected on both:* "… turned off disappearing messages".

## 8. A scheduled message to a locked phone

1. On the M20: **Settings → Network & transports → Stay reachable in the background** is on.
2. On the M51, type a message, tap the clock beside Send, and pick a time 3 minutes ahead.
   *Expected:* "Scheduled"; the message isn't in the chat; the header's scheduled button shows 1.
3. Open the scheduled list. *Expected:* the message with its time; **Change**, **Send now** and
   **Delete** work (try Change, then leave it scheduled).
4. Lock the M20 (power button) and leave it.
5. At the scheduled time: *Expected:* the message appears in the M51's chat, turns ✓✓ within a
   minute, and the M20's lock screen shows only "New message" (no name, no text).
6. Unlock the M20. *Expected:* the message is there.

## 9. Locked, without staying reachable

1. On the M20, turn **Stay reachable in the background** off, then lock it. *Expected:* the running
   notification disappears.
2. Send from the M51. *Expected:* stays **Queued**.
3. Unlock the M20. *Expected:* the notification returns, the message arrives, the M51 shows ✓✓.
4. Turn the setting back on.

## 10. Stop from the notification

1. Lock the M20, pull down the shade, and tap **Stop** on "TFL is running". *Expected:* the
   notification disappears, and the phone's Bluetooth name (Settings → Connections → Bluetooth) is
   its usual one again: while Nearby advertises, it shows Nearby's random-looking name instead.
2. Send from the M51. *Expected:* stays **Queued** (the M20's keys are wiped until it's unlocked).
3. Unlock the M20. *Expected:* Nearby runs again and the message arrives.

## 11. Blocking

1. On the M20, open the M51's profile → **Block**. *Expected:* the conversation's composer says
   "You blocked M51…" with **Unblock**; the Chats row shows "Blocked".
2. Send from the M51. *Expected:* stays **Queued**; nothing reaches the M20, and no link is made.
   Transport logs: blocking closes the live link ("friend #… is no longer allowed" on the M20);
   when the M51 tries again, the M20 refuses it ("handshake failed: UNKNOWN_PEER") and the M51 only
   sees the connection close ("Endpoint closed: disconnected").
3. Unblock on the M20. *Expected within a minute or two:* the queued messages arrive.

## 12. A new key (reinstall)

1. Put the M20 in airplane mode and send a message from the M51. *Expected:* **Queued**.
2. On the M20, uninstall TFL, reinstall it, onboard a **new** identity (don't restore) **with the
   same name, M20** (the M51 asks "Same person as M20?" only for a code with a name it already
   has), set up Nearby, and turn airplane mode off. *Expected:* the M51's message stays
   **Queued**: it's sealed to the old key, and the new M20 is a stranger to the M51.
3. Pair again (three scans). *Expected on the M51:* "Same person?" / key change warning; the M20's
   row shows **ALERT** and **Resolve key**, and the composer says "M20's key changed. Verify it
   before sending anything."
4. Verify (the three scans, or safety numbers). *Expected:* the M51's queued message is sealed again
   for the new key and arrives on the new M20 (which never saw the old conversation).

## 13. The decoy

1. On the M20: **Settings → Security → Duress PIN**, decoy mode. Lock.
2. Unlock with the duress PIN. *Expected:* the decoy's empty Chats; the running notification is
   still there (the decoy runs its own transport).
3. Send from the M51. *Expected:* **Queued**: the decoy isn't the M51's friend.
4. Lock, unlock with the real PIN. *Expected:* the message arrives; nothing from it is in the decoy.

## 14. Notifications and the disguise

1. **Settings → Notifications → Show sender name**: off (default). Receive a message with the chat
   closed. *Expected:* "New message" only.
2. Turn it on and receive another. *Expected:* the friend's name and "New message", never the text.
   On a secure lock screen (PIN, pattern or password), this alert doesn't show at all, even if the
   phone is set to show all notification content there.
3. Turn on the calculator disguise. *Expected:* the running notification and new-message alerts
   say "Calculator" with a calculator icon. (Android still shows the app's installed name, TFL,
   in the notification header; no app can change that.)

## 15. Battery saver

1. On the M51, **Settings → Network & transports → Battery saver** on.
2. Watch **Settings → Developer → Transport log** for 15 minutes. *Expected:* "Discovering for
   20 s" about every 10 minutes (instead of 30 s every 1 to 5 minutes).
3. Messages to an already linked friend still go at once.

## 16. One more phone as a stranger (optional)

If a third phone is at hand: install TFL on it and onboard, but don't pair. *Expected:* in the
transport logs the connection is made and dropped ("handshake failed" on one side, "disconnected"
on the other), nothing is delivered either way, and neither Chats counts it as nearby.

## Afterwards

- `adb logcat -d | grep -i tfl` must contain no message text, names or keys (debug builds log
  events only; release builds log nothing).
- On each phone, Settings → Apps → TFL → Permissions lists only what step 1 granted (plus
  notifications on Android 13+).
