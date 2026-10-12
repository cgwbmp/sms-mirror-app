# SMS Mirror — technical documentation

Everything beyond "install it and use it". For the short version see the
[README](README.md); for the original design and the reasoning behind it, see
[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

- [Status](#status)
- [How it works](#how-it-works)
- [Filtering precedence](#filtering-precedence)
- [App lock](#app-lock)
- [Saving settings](#saving-settings)
- [Mail providers](#mail-providers)
- [Build](#build)
- [Install and verify](#install-and-verify)
- [Releasing](#releasing)
- [Distribution](#distribution)

## Status

**v1.0.1** — fixes permission prompts on Android 17. v1.0.0 was verified on a Pixel 6 (Android 17, API 37): an incoming SMS arrives as
email, the outbox row is deleted on success, and only the counter persists. Rules add,
list and delete against a live Room v2 migration. 21 unit tests pass.

Still unverified: offline queueing (needs an airplane-mode run) and reboot recovery.

Working:

- incoming SMS → durable Room outbox → WorkManager → SMTP over TLS → your own inbox
- send-to-self: one account is both sender and recipient, one app password
- app password encrypted under an Android Keystore key
- rows deleted the moment a send succeeds — no history, no sent log, counters only
- rule-based filtering: sender allow/block, keyword, regex, OTP heuristic, with a
  "forward everything" master switch
- SMTP autoconfig: the email domain picks the server, so host and port are shown but not
  editable for a known provider; unknown domains fall back to editable fields prefilled
  with `smtp.<domain>`
- changing the forwarding address is gated on notifying the outgoing address first
- Home (master switch, permissions, counters, needs-attention queue),
  Rules, and Setup screens
- failure notifications, switchable off in Setup — the failed message still shows under
  "Needs attention" either way

Not yet: OAuth2 instead of app passwords, end-to-end body encryption, alternative
delivery channels. `targetSdk` is 35 while the test device runs API 37 — worth raising
before relying on this daily, since background-execution rules tighten each release.

## How it works

```
Incoming SMS
   │  (android.provider.Telephony.SMS_RECEIVED broadcast)
   ▼
SmsReceiver           parse sender + body + timestamp
   │
   ▼
FilterEngine  ──▶ no match ──▶ drop
   │ match
   ▼
Outbox (Room)         durable queue — survives offline, reboot, process death
   │
   ▼
ForwardWorker         WorkManager: network-constrained, retry with backoff
   │
   ▼
SmtpClient (TLS)  ──▶ your inbox  ──▶ read on any device
```

Sending is decoupled from receiving on purpose: a plain network call inside the
broadcast receiver would drop messages whenever there is no signal. The Room outbox is
the durable buffer between "SMS arrived" and "email sent", and it is the only place a
message body is ever stored — until the send succeeds, at which point the row is
deleted.

Source layout, under
[app/src/main/java/com/example/smsmirror/](app/src/main/java/com/example/smsmirror/):

| Package | What lives there |
|---|---|
| `sms/` | `SmsReceiver` (SMS_RECEIVED broadcast, multipart reassembly), `SimInfo` |
| `filter/` | `FilterEngine`, `Rule` — pure Kotlin, no Android deps, fully unit-tested |
| `data/` | Room database, outbox and rule entities/DAOs |
| `work/` | `ForwardWorker` — dequeue, send, delete on success, retry on failure |
| `mail/` | `SmtpClient`, `SmtpConfig`, `MailProvider`, `EmailFormatter` |
| `security/` | `SecureStore` (Keystore), `AppLock`, `LockPolicy` |
| `ui/` | Compose screens: Home, Rules, Setup, Lock + `MainViewModel` |
| `core/`, `boot/` | DI container, settings, notifications, boot re-arm |

## Filtering precedence

1. a matching **block** rule — wins even over "forward everything", or it wouldn't be
   a blocklist
2. **forward everything**
3. any matching allow rule (sender / keyword / regex / OTP)
4. otherwise drop

Phone numbers match across formats: `+15551234567`, `5551234567` and `+1-555-123-4567`
are the same phone, compared by digits with suffix matching and a 4-digit floor (so a
rule of `7` can't forward every number ending in 7).

The OTP heuristic needs **both** a standalone 4–8 digit run **and** an auth-ish word
("code", "verification", "PIN"). That keeps "See you at 1900" out, at the cost of
missing a bare-code message with no explanatory word.

> A regex with catastrophic backtracking would run on the SMS receive path and could
> block the receiver. It's your own pattern on your own phone, so there's no match
> timeout; add one before using complex patterns.

## App lock

Opening the app requires the phone's biometric or screen lock. This closes the hole the
address-change gate can't: *Forget saved password* followed by a fresh setup would
otherwise redirect forwarding without notifying anyone. It also covers Rules — a block rule
on your bank's sender silently kills those forwards — and the master switch on Home.

- re-locks after **2 minutes** in the background, not immediately: setup means leaving the
  app to copy a 16-character app password, and an instant re-lock would make that painful.
  [LockPolicy](app/src/main/java/com/example/smsmirror/security/LockPolicy.kt) measures with
  `SystemClock.elapsedRealtime()`, not wall-clock, so moving the device clock can't stretch
  the window
- only applies once credentials exist — before setup there's nothing to protect
- disabled automatically, whatever the setting says, if the phone has no screen lock, so
  the app can never lock you out of itself
- `FLAG_SECURE` keeps the account address and queued sender numbers out of screenshots and
  the recent-apps thumbnail. Note this makes `adb screencap` return black; `uiautomator
  dump` still works for verification

> **`SecureStore`'s Keystore key must stay non-authentication-bound.** `ForwardWorker`
> decrypts the SMTP password and sends while the phone is locked and in Doze — that is the
> product. Binding the key to user authentication looks like obvious hardening and would
> break forwarding entirely. **This lock gates the UI only; messages keep forwarding while
> the app is locked.**

## Saving settings

Nothing is persisted until it has been proven to work. "Save and send test email" runs, in
[MainViewModel.saveAndSendTest](app/src/main/java/com/example/smsmirror/ui/MainViewModel.kt):

1. **validate** — a rejected form leaves the working configuration untouched
2. **test** the new credentials by sending the test email with them
3. **notify** the outgoing address, if the destination is changing
4. **save** — only once every step above has succeeded

Step 2 preceding step 4 is the point: otherwise one mistyped character in an app password
overwrites a working one, and forwarding then fails silently on the next message. Step 3
following step 2 is also deliberate — warning the old address before the new account is
proven would cry wolf every time you fat-fingered your own password.

Because this app forwards one-time passcodes, quietly repointing it at another inbox would
hand over every future code, so a change of destination has the extra step 3: the outgoing
address is emailed with its own credentials, naming both addresses and the time. A new
address also requires its own app password, since silently reusing the previous account's
would save a configuration that cannot sign in.

Offline, a change is refused rather than deferred — so cutting the network doesn't buy a
silent redirect, it just blocks the change.

**Its limits, stated plainly.** *Forget saved password* clears the credentials without
notifying anyone — behind a confirmation dialog, since it also switches forwarding off
and the password can't be read back out of the Keystore to re-enter. It is the
deliberate escape hatch for an account that is no longer reachable — otherwise its address could never be changed again. Clearing also switches
forwarding off, so it can't redirect messages by itself, but combined with a fresh setup it
does route around the gate. Nor does any of this survive someone clearing the app's data
from Android settings. The gate defends against a few seconds on an unlocked phone and
against your own mistakes; it is not a defence against a determined attacker holding the
device.

## Mail providers

[MailProvider.kt](app/src/main/java/com/example/smsmirror/mail/MailProvider.kt) maps a
domain to its SMTP server and to whether that provider still permits password sign-in.
Setup warns about the exceptions but never blocks them — provider policy varies by
account, so the test email is the real authority:

- **Outlook / Hotmail / Live** — Microsoft has been retiring password sign-in for SMTP on
  personal accounts in favour of OAuth. Flagged as *may refuse the sign-in*.
- **Proton Mail** — no reachable SMTP server at all; it needs the desktop-only Bridge.
  Flagged as unusable, with no host prefilled.

These are **best-known defaults, not guarantees**. Because the fields are read-only for
known providers, correcting a changed setting means editing that one table rather than
the UI.

## Build

The toolchain lives in Docker, so nothing but Docker is needed on the host.

```sh
./dev.sh setup     # image + ./gradlew + debug keystore — once, ~10 min
./dev.sh build     # assemble app/build/outputs/apk/debug/app-debug.apk
./dev.sh test      # JVM unit tests
```

The debug signing key lives in `.keystore/` (gitignored) rather than in the container's
home directory, which `--rm` would discard — otherwise every build signs with a new key
and `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

On Apple Silicon the image is `linux/amd64` — Google ships no arm64-Linux `aapt2`/`d8`.
Enable *Docker Desktop → Settings → General → "Use Rosetta for x86/amd64 emulation"*
first, or builds will be slow.

Android Studio can also open this project directly and build on the host; the two don't
conflict. Studio writes a `local.properties` pointing at the host SDK — the container
ignores it and falls back to the SDK in the image, printing one harmless warning.

The emulator has to run on the host either way: Docker Desktop on macOS has no KVM, so
no container can run it. Create an AVD in Studio's Device Manager if you want one.

## Install and verify

`adb` runs on the **host** — Docker Desktop on macOS reaches neither USB devices nor
the emulator.

```sh
./dev.sh install                              # adb install the debug APK
./dev.sh sms +15551234567 "Your code is 123"  # simulate an SMS (emulator only)
./dev.sh logs                                 # tail the relevant logcat tags
```

Order to check things in:

1. **Setup → Save and send test email.** Proves credentials and TLS before any SMS
   logic is involved. Gmail needs an *App Password*, not the account password.
2. **Home → grant SMS permission → turn forwarding on.**
3. Send an SMS (or `./dev.sh sms`) and confirm it arrives as email.
4. **Offline:** enable airplane mode, send an SMS — it shows as *Queued* on Home.
   Turn airplane mode off; WorkManager flushes it.
5. **Retention:** after a successful send the outbox row is gone. Only the counter moves.

## Releasing

Versions live in one place, [app/build.gradle.kts](app/build.gradle.kts):
`versionName` is what users see, `versionCode` is the integer Android compares on
update and **must increase on every published build**. Changes are recorded in
[CHANGELOG.md](CHANGELOG.md).

The release APK is signed with a key that is **not** in the repo. Create it once:

```sh
./dev.sh keystore release      # writes .keystore/release.keystore + keystore.properties
```

Both files are gitignored. **Back them up.** Android identifies an app by its signing
key: lose it and no future build can install as an update over an existing one — users
would have to uninstall first, losing their settings.

Then, for each release:

```sh
./dev.sh test                  # unit tests must pass
./dev.sh dist                  # assembleRelease + copy to dist/sms-mirror-<version>.apk
```

`dist/` is the one place `.gitignore` lets an APK through, so the built artifact is
committed alongside the tag:

```sh
git commit -am "Release 1.0.0"
git tag -a v1.0.0 -m "SMS Mirror 1.0.0"
```

Without `keystore.properties`, `assembleRelease` still runs and emits an **unsigned**
APK — convenient for a fresh clone, useless for installing. `dist` verifies the
signature itself and fails if it's missing; to check by hand:

```sh
docker compose run --rm android \
  /opt/android-sdk/build-tools/35.0.0/apksigner verify --verbose dist/sms-mirror-1.0.0.apk
```

Expect *Verified using v2 scheme: true*. v1 (JAR signing) is deliberately absent —
`minSdk` 26 means AGP skips it — so looking for `META-INF/*.RSA` inside the APK will
mislead you.

## Distribution

Sideload only. Google Play restricts the SMS permission group to default SMS handlers,
so this won't pass review — build the signed APK and install it directly.
