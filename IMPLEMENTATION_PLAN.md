# SMS Mirror App — Implementation Plan

## Context

**Goal:** After installing an app on a phone, automatically watch for incoming SMS
and forward them (filtered by rules) to an email inbox, so they can be read on any
other device — including an iPhone — **with nothing installed on the receiving side**.

**Why email + on-device:** Email is universal (every phone already has it), needs no
install on the other device, and — done on-device — means there is no server to run,
no cloud account to secure, and no third party holding your messages.

### Hard platform constraint (shapes the whole design)

**iOS cannot read incoming SMS.** Apple exposes no API for it (only a limited "SMS
Filter" extension for spam classification, which cannot read or forward message
bodies). Therefore the **forwarding app is Android-only**. This is not a stack
choice — it is an OS restriction. The *receiving* device can be anything with email.

Because the source is Android-only, a cross-platform framework adds complexity
without benefit. **Recommended stack: native Kotlin + Jetpack Compose.** (Flutter is
possible via an SMS-reader plugin, but background reliability and permissions are
fiddlier — noted as an alternative only.)

### Decisions locked in with the user
- **Source phone:** Android only.
- **Delivery channel:** Email over SMTP.
- **Architecture:** On-device only, no backend.
- **Filtering:** Rule-based (not every SMS).
- **Account:** **send-to-self** — FROM and TO are the **same single account** (chosen
  for security/simplicity: one credential, no separate recipient to leak or mistype).
- **Forward payload:** SMS text + sender number + receiver number/SIM + timestamp.
- **Expiration:** none. True self-destructing email isn't possible over standard SMTP;
  the deletion-based alternative was considered and **intentionally skipped**.
- **History / retention:** **none kept.** A message lives on-device only while in
  flight (arrived → sent); on successful send its row (text included) is **deleted
  immediately**. Only **failed** messages are retained — and only until they succeed,
  because retry needs the body. No persistent history log, no sent-message store. The
  app keeps only non-sensitive **counters** (integers, no content).

### How sending works (no third party, no Gmail app)

The app is its **own SMTP client** — it connects directly to a mail server
(e.g. `smtp.gmail.com:465` over TLS) and sends silently in the background. It does
**not** launch the Gmail app (that would require a manual tap per message) and does
**not** use any third-party email API (which would put a middleman in the path).

**One account, used as both sender and recipient (send-to-self):** the app
authenticates against a single email account with an **app-specific password** and
sends the message to that same address. You then read it on your other device by
signing that account's inbox into that device's mail app. Only **one** credential is
stored, and there is no separate destination address to configure.

---

## Recommended secure design (summary)

The requested design is sound. Hardening on top:

1. **Never require your main email password.** Use a provider **app-specific password**
   (Gmail: "App passwords"; Outlook/Fastmail similar) or OAuth2. It is scoped and
   revocable, so a leaked device can't take over the whole account.
2. **Transport encryption always on:** SMTP over implicit TLS (port 465) or STARTTLS
   (port 587). Reject plaintext.
3. **Credentials at rest:** store SMTP secrets in the **Android Keystore** (hardware-
   backed where available), never in plain `SharedPreferences` or code.
4. **Least data:** forward only what rules match; don't keep message bodies in logs
   longer than needed (or store them encrypted / redacted).
5. **Optional true confidentiality:** email is only transport-encrypted, not
   end-to-end — your email provider can read it. For genuinely sensitive traffic (OTP
   codes), an optional **encrypt-body** mode (age/PGP public key of the recipient) is
   listed as a v2 enhancement.

> **Distribution note:** Google Play heavily restricts the SMS permission group — a
> forwarder like this will not pass Play review unless it's the default SMS handler or
> gets a special exception. For personal use, **sideload the signed APK** (build once,
> install directly). This avoids Play policy entirely. Flagged now so it isn't a
> surprise at ship time.

---

## Architecture

Event-driven, fully on-device:

```
Incoming SMS
   │  (android.provider.Telephony.SMS_RECEIVED broadcast)
   ▼
SmsReceiver (BroadcastReceiver, RECEIVE_SMS)
   │  parse sender + body + timestamp
   ▼
FilterEngine  ──▶ no match ──▶ drop
   │ match
   ▼
Outbox (Room DB)  ── durable queue, survives reboot / offline
   │
   ▼
ForwardWorker (WorkManager, network-constrained, auto-retry w/ backoff)
   │
   ▼
SmtpClient (Jakarta Mail, TLS)  ──▶ recipient inbox  ──▶ read on any device
```

**Why this shape:**
- `BroadcastReceiver` on `SMS_RECEIVED` catches new messages **without** being the
  default SMS app (needs only `RECEIVE_SMS`, not full SMS-handler status).
- Sending is decoupled into **WorkManager** so it survives Doze, offline periods, and
  process death, with automatic retry/backoff — a plain network call in the receiver
  would drop messages when there's no signal.
- **Room outbox** = the durable buffer between "SMS arrived" and "email sent."

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 26 (Android 8) — ~99% coverage |
| Async | Kotlin Coroutines |
| Background send | WorkManager (`CoroutineWorker`) |
| Persistence | Room (in-flight outbox + rules only — no history) |
| SMTP | Jakarta/JavaMail (`com.sun.mail:android-mail` + `android-activation`) |
| Secret storage | Android Keystore (via Tink `AesGcm`, or Keystore-wrapped). *Note: Jetpack Security `EncryptedSharedPreferences` is deprecated — use Tink or direct Keystore.* |
| Build | Gradle (Kotlin DSL), single `:app` module to start |

---

## UI / UX design — simple, modern, nice

**Design goal:** feel like a calm, trustworthy utility — not a busy dashboard. One
primary action (turn forwarding on/off), everything else one tap away. Built on
**Material 3 (Material You)** so it looks native and modern for free, with a
deliberate identity on top.

### Visual identity
- **Style base:** Material 3 — dynamic color, rounded corners, tonal surfaces,
  Material motion. Full **light + dark** support.
- **Accent — "signal green":** relay/active/go, nodding to Android's messaging
  heritage. `#12A55F` (light) / `#2ED47F` (dark). *Not* the generic purple-blue.
- **Neutrals:** green-biased near-black ink `#0F1613` on soft green-grey surfaces —
  picked, not default grey.
- **Semantic colors (separate from accent):** amber `#C77E00` = warning/queued,
  red `#D3454A` = failed. State is shown as a color + a **status chip**, never color
  alone (accessibility).
- **Type:** system UI sans for the interface; **monospace with `tabular-nums`** for
  all data — phone numbers, OTP codes, timestamps. This is subject-driven (telecom
  data lines up in columns) and gives the app a quiet technical character.
- **Iconography:** Material Symbols (rounded), used sparingly.

### Navigation & screens
Bottom navigation bar with **3 destinations** (no History tab — nothing is retained).
Each screen is one scrollable column of cards with generous spacing.

- **Home (primary):** a large **on/off master switch** as the hero, with a status line
  ("Forwarding active · watching SMS"). Below: the send-to-self account chip, a small
  **counter** row (forwarded today / failed — numbers only, no content), and a
  **"Needs attention"** section that appears only when messages are currently queued or
  failing, each with a **status chip** (Queued / Failed) and a *Retry* action. This
  section reflects the live in-flight outbox and empties itself as sends succeed. A
  subtle green glow/ring communicates the active state at a glance.
- **Setup:** grouped form — account email, app-password (masked, with reveal), SMTP
  host/port. A prominent **"Send test email"** button and inline result. A small
  reassurance line: *"Stored in the Android Keystore. Messages are never saved after
  they're sent."*
- **Rules:** a **"Forward everything"** master toggle at top; below, a list of rule
  cards (sender / keyword / regex / OTP-only), each with its own switch. Floating
  action button **"+ Add rule"** opens a simple sheet.

### Interaction & polish
- First-run **onboarding**: 3 short steps (grant SMS permission → enter account →
  send test), so the empty state is never confusing.
- Clear **empty states** (no rules yet, nothing needs attention) with a one-line prompt.
- **Permission & battery** prompts presented inline on Home as dismissible cards, not
  scary system dialogs out of nowhere.
- Copy is plain and action-labeled ("Turn on forwarding", toast "Test email sent").
- Respects system dark mode and dynamic color; large touch targets; TalkBack labels.

> A clickable HTML preview of these three screens can be produced on request (kept out
> of the build for now).

---

## Modules / files to create

New Android project (`com.example.smsmirror`). Key source files:

- `SmsMirrorApp.kt` — Application, WorkManager init, notification channel.
- `sms/SmsReceiver.kt` — `BroadcastReceiver` for `SMS_RECEIVED`; parses PDUs
  (`Telephony.Sms.Intents.getMessagesFromIntent`), joins multipart bodies, captures
  **sender number** (`SmsMessage.originatingAddress`) and the receiving **SIM
  subscription id** (`subscription` intent extra), enqueues to outbox, kicks
  WorkManager.
- `sms/SimInfo.kt` — resolves the receiving SIM's own number via
  `SubscriptionManager.getActiveSubscriptionInfo(subId).number` (needs
  `READ_PHONE_STATE`). Carriers often leave the number blank, so falls back to a label
  like `SIM <slot> · <carrierName>`.
- `filter/FilterEngine.kt` + `filter/Rule.kt` — evaluate rules against
  (sender, body). Rule types: sender allow/deny list, keyword/substring, regex,
  built-in "OTP-like" detector. Pure/testable, no Android deps.
- `data/Outbox.kt`, `data/OutboxEntity.kt`, `data/RuleEntity.kt`,
  `data/AppDatabase.kt` — Room DAO + entities for the **in-flight queue** and rules
  only. **No history/sent-message table.** A tiny `Counters` store (integers only) backs
  the Home stats. Outbox rows are the *only* place message text lives, and only while
  pending/failed.
- `mail/SmtpClient.kt` — builds `Session` with TLS props, sends `MimeMessage`.
  Subject e.g. `SMS from <sender>`, body = **sender number + receiver number/SIM +
  timestamp + message text**. Recipient = the configured account itself (send-to-self).
- `mail/SmtpConfig.kt` + `security/SecureStore.kt` — host/port + **single account
  email** (used as both FROM and TO) + Keystore-backed app-password storage;
  read/write helpers.
- `work/ForwardWorker.kt` — dequeues pending outbox rows, calls `SmtpClient`; on
  success **deletes the row** (increments the sent counter) so no content is retained;
  on transient failure keeps the row and returns `Result.retry()` (retry needs the
  body).
- `boot/BootReceiver.kt` — `RECEIVE_BOOT_COMPLETED` to re-arm after reboot.
- **UI (Compose):**
  - `ui/HomeScreen.kt` — master on/off toggle, status, permission prompts, battery-
    optimization-exemption request, counters, and a live **"Needs attention"** list of
    queued/failed items with Retry.
  - `ui/SetupScreen.kt` — SMTP host/port + **single account email + app-password**
    (used as both sender and recipient) + "Send test email" button.
  - `ui/RulesScreen.kt` — add/edit/delete forwarding rules; "forward all" shortcut.
- `AndroidManifest.xml` — permissions + receiver registration (below).

### Permissions (Manifest)
- `RECEIVE_SMS` — the core one (new incoming SMS).
- `READ_PHONE_STATE` — resolve the receiving SIM's number/carrier (may return blank;
  slot/carrier used as fallback).
- `INTERNET` — send email.
- `POST_NOTIFICATIONS` (Android 13+) — foreground/status notifications.
- `RECEIVE_BOOT_COMPLETED` — re-register after reboot.
- `FOREGROUND_SERVICE` (only if a persistent service is added later; WorkManager
  covers MVP).
- Optional `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — reliability on aggressive OEMs.
- `READ_SMS` is **not** required (we only need *incoming*, via the broadcast).

---

## Build roadmap (phased)

**MVP (get one SMS to email):**
1. Scaffold project, permissions, runtime-permission request flow.
2. `SmsReceiver` → parse → log to Room outbox.
3. `SmtpClient` + `SetupScreen` + "Send test email" (proves credentials/TLS work).
4. `ForwardWorker` wired: outbox → email. "Forward all" (no rules yet).
5. `HomeScreen` on/off toggle + status.

**v1 (usable daily):**
6. `FilterEngine` + `RulesScreen` (sender/keyword/regex/OTP rules).
7. Retry/backoff + offline queue verified; delete-on-send + "Needs attention" list.
8. Keystore-backed credential storage; `BootReceiver`; battery-exemption prompt.
9. Notification when a forward fails.

**v2 (enhancements, optional):**
10. OAuth2 for Gmail (drop app-password requirement).
11. Optional end-to-end body encryption (age/PGP recipient key).
12. Alternative channels behind the same outbox (Telegram bot / ntfy) if email proves
    too slow for OTPs.

---

## Verification (end-to-end)

- **Build/install:** `./gradlew assembleDebug` then
  `adb install app/build/outputs/apk/debug/app-debug.apk` on a device or emulator.
- **SMTP config test:** enter app-password creds, tap "Send test email," confirm it
  arrives. (Validates TLS + credentials before any SMS logic.)
- **Simulate an incoming SMS on the emulator:**
  `adb emu sms send +15551234567 "Your code is 123456"` → confirm it lands in the
  inbox and that the outbox row is **deleted after a successful send** (no content left
  in the DB; only the counter increments).
- **Filtering:** add a rule (e.g. sender allowlist / OTP-only), send a matching and a
  non-matching SMS, confirm only the match is forwarded.
- **Offline resilience:** enable airplane mode, send an SMS (queued), disable airplane
  mode, confirm WorkManager flushes the queue and the email arrives.
- **Reboot resilience:** reboot device, send an SMS, confirm it still forwards
  (BootReceiver re-armed).
- **Unit tests:** `FilterEngine` rule matching (keyword/regex/OTP/allowlist) as pure
  JVM tests — no device needed.

---

## Open items / assumptions
- Assumes **personal use via sideloaded APK** (not Play Store distribution).
- Assumes the user's email provider supports app-specific passwords or basic SMTP+TLS.
- Multipart (long) SMS are reassembled before forwarding.
- No MMS/attachments in scope. Each forward carries **SMS text + sender number +
  receiver number/SIM + timestamp**.
- Receiver phone number depends on carrier provisioning; when unavailable it degrades
  to `SIM <slot> · <carrier>`.
- **Sending = direct SMTP from the app** (not the Gmail app, not a third-party API).
- **Send-to-self:** FROM = TO = one configured account; a single app-password stored.
- **No email expiration** (standard SMTP can't self-destruct; deletion-based purge was
  considered and skipped).
- **No local history:** message text is stored only in the in-flight outbox (until a
  successful send, or until a failed send finally succeeds), then deleted. No sent-log,
  no History screen; only integer counters persist. 3-tab bottom nav.
