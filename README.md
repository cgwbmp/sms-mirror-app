# SMS Mirror

**Read your Android texts anywhere — on your iPhone, your laptop, your tablet.**
Your phone emails them to you. Nothing to install on the other device.

Keep an Android phone in a drawer for your bank's verification codes? Carry two phones?
Switched to an iPhone but left a number behind on the old one? SMS Mirror sends those
texts to your own email inbox, the moment they arrive.

[**Download the app (v1.0.0)**](https://github.com/cgwbmp/sms-mirror-app/raw/main/dist/sms-mirror-1.0.0.apk) · Android 8 or newer · 12 MB

---

## How it works, in three steps

1. **Install it** on the Android phone that gets the texts.
2. **Enter your email** and an app password from your email provider. Tap *Save and send
   test email* — you'll see a test message land in your inbox right away.
3. **Turn forwarding on.** Every text you chose to forward now arrives as an email.

That's it. The app sends the mail from your own email account, to that same account. You
read it wherever you already read email.

## What you get

- **Texts as email, within seconds.** Sender, message, time, and which SIM received it.
- **You pick what gets forwarded.** Everything, or only certain senders, only messages
  with certain words, or only one-time codes. You can also block a sender outright.
- **Nothing lost when you're offline.** Texts wait in a queue on the phone and go out as
  soon as there's signal again. Same after a restart.
- **It tells you when something breaks.** A failed message shows up under *Needs
  attention* on the home screen, with a retry button.
- **Locked behind your fingerprint.** Opening the app needs your phone's usual unlock.
- **Free, and the whole source code is here.** MIT licensed — fork it, change it, ship
  your own version.

## Your privacy

This is the part that matters, so plainly:

- **No servers of ours. No accounts. No sign-up.** The app talks straight to your email
  provider, the same way a mail app does. Nobody else is in the path.
- **Nothing is kept.** A message lives on the phone only while it's waiting to be sent.
  The second the email goes out, it's deleted. There is no history screen, no sent log —
  just a counter of how many messages went through.
- **Your password is encrypted** by the phone's hardware keystore.
- **Changing the forwarding address emails the old address first**, so nobody can quietly
  point your verification codes somewhere else.

One honest caveat: email is encrypted in transit, but your email provider can read what's
in your inbox — as it always could. If that's not acceptable for your texts, this app
isn't the right tool.

## Good to know

- **The sending phone must be Android.** Apple gives apps no way to read incoming texts,
  so this can't exist as an iPhone app. The *receiving* device can be anything with
  email.
- **You install it yourself**, from the file above — it isn't on Google Play. Google
  reserves text-message permissions for apps that replace your whole messaging app, so a
  forwarder like this can't be listed there.
- **You need an app password** from your email provider (Gmail calls it an *App
  Password*). Outlook and Proton Mail are the awkward ones — see
  [the docs](DOCS.md#mail-providers).
- **It's version 1.0.** It's been tested on real hardware and it works, but it's a young
  project. If it misses a message, please [tell us](#help-and-feedback).

Installing: open the downloaded `.apk` on your phone and allow your browser or file
manager to install unknown apps when Android asks. Android will warn you — that warning
is about the app coming from outside the Play Store, not about anything the app does.

## When something doesn't work

**Android won't let me install it.** Two different blocks look alike. If the message is
about *unknown apps* or *unknown sources*, the fix is a toggle: Android points you at the
right settings screen, and you allow whichever app you downloaded with — Chrome, Files,
whatever. If the message says the app was *blocked* or *scanned* and offers only a
*Cancel* button, that's Play Protect. Tap *More details* → *Install anyway*. If Play
Protect won't budge, install over USB from a computer instead:

```sh
adb install sms-mirror-1.0.0.apk
```

That needs *Developer options* → *USB debugging* on the phone, and Android's `adb` tool
on the computer. It takes the same route as any developer install and skips the scanner
prompt entirely. Play Protect may still show a warning afterwards; the app runs fine.

**The test email never arrives.** Almost always the password. Email providers won't
accept your normal account password from an app like this — you need an *app password*,
generated in your provider's security settings, usually 16 characters. If you're on
Outlook, Hotmail or Live, Microsoft is turning password sign-in off for personal
accounts and may refuse yours outright. Proton Mail can't work at all without their
desktop Bridge. See [the docs](DOCS.md#mail-providers).

Nothing is saved until the test email succeeds, so a failed test leaves your previous
working setup alone. That's deliberate.

**Texts arrive, but no email.** Work down this list:

- Is the master switch on Home actually on?
- If you're not using *forward everything*, does the message match one of your rules? No
  match means the text is dropped, by design.
- Do you have a **block** rule that catches it? Block rules beat everything, including
  *forward everything*.
- Expecting a one-time code to be caught automatically? The code detector needs both a
  4–8 digit number **and** a word like "code", "verification" or "PIN". A bare number
  with no explanation won't trip it — add a sender rule for that service instead.

**Emails come, but late — or dry up when the phone sits idle.** Android is putting the
app to sleep. Home shows a *battery* prompt when this is the likely cause; tap through it
and exempt the app. Some phones need more: Samsung, Xiaomi, OnePlus and Huawei run
their own battery managers on top of Android's, so also find SMS Mirror in your phone's
battery settings and set it to *Unrestricted* (or remove it from any "sleeping apps" list).

**Nothing forwards after a restart until I unlock the phone.** Expected. Android keeps
storage encrypted until the first unlock after boot, and the app can't read your saved
password before that. Texts that arrive in that window are picked up once you unlock.

**A message failed.** It shows under *Needs attention* on Home with the reason and a retry
button. A rejected sign-in stops immediately rather than retrying — retrying can't fix a
wrong password. Anything transient (no signal, server busy, timeout) stays queued and goes
out by itself, with the wait doubling after each attempt.

**Something else, or none of the above.** Please [open an issue](https://github.com/cgwbmp/sms-mirror-app/issues/new/choose)
— a case we haven't seen is worth knowing about.

## Help and feedback

- **Something broken?** [Open an issue](https://github.com/cgwbmp/sms-mirror-app/issues/new/choose)
  — there's a short form to fill in.
- **Idea or question?** Same place. Questions are welcome; there's no wrong one.
- **Want to help build it?** See [CONTRIBUTING.md](CONTRIBUTING.md). Small fixes are as
  welcome as big ones.

Please don't include the contents of your text messages, your email password, or full
phone numbers in a bug report — we never need them.

## For developers

- [DOCS.md](DOCS.md) — how it works inside, how to build it, how the rules and the
  security gates behave, how to cut a release.
- [CONTRIBUTING.md](CONTRIBUTING.md) — setup, tests, pull requests.
- [CHANGELOG.md](CHANGELOG.md) — what changed in each version.
- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) — the original design and why it's
  shaped this way.

Kotlin, Jetpack Compose, Room and WorkManager. Builds in Docker, so all you need on your
machine is Docker:

```sh
./dev.sh setup     # once
./dev.sh build     # debug APK
./dev.sh test      # unit tests
```

## License

[MIT](LICENSE). Do what you like with it — fork it, modify it, redistribute it, build a
paid version of it. Keep the copyright notice, and understand there's no warranty: this
app handles verification codes, and you're the one deciding to trust it with them.

