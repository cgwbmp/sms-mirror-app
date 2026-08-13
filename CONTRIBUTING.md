# Contributing to SMS Mirror

Bug reports, questions and patches are all welcome. This is a small project — you don't
need to ask permission before opening an issue or a pull request.

- [Reporting a bug](#reporting-a-bug)
- [Suggesting a feature](#suggesting-a-feature)
- [Asking a question](#asking-a-question)
- [Reporting a security problem](#reporting-a-security-problem)
- [Setting up to build](#setting-up-to-build)
- [Making a change](#making-a-change)
- [Opening a pull request](#opening-a-pull-request)

## Never paste these into an issue

This app handles text messages and email credentials, and issues are public. Before you
post, take out:

- **message bodies** — especially anything with a one-time code in it
- **email passwords or app passwords** — always, in any form
- **full phone numbers** — mask them: `+1555…4567`
- **raw logcat dumps** — they can contain all of the above. Paste only the lines that
  matter, and read them first.

A redacted report is more useful than no report. We can always ask for more.

## Reporting a bug

[Open a bug report.](https://github.com/cgwbmp/sms-mirror-app/issues/new?template=bug_report.yml)
The form asks for what we actually need:

- what you expected, and what happened instead
- app version (Setup screen, bottom) and Android version
- phone model — background behaviour differs a lot between manufacturers
- your email provider (Gmail, Fastmail, …), never the address itself
- steps to reproduce, if you know them

If a message failed to forward, `./dev.sh logs` (or `adb logcat -s SmsReceiver:V
ForwardWorker:V`) shows the relevant tags. Read before pasting — see the warning above.

**Before filing:** check whether the test email in *Setup → Save and send test email*
still works. It separates a mail-provider problem from an app problem, and it is the
first thing we'd ask.

## Suggesting a feature

[Open a feature request.](https://github.com/cgwbmp/sms-mirror-app/issues/new?template=feature_request.yml)
Describe the situation you're in, not only the feature — the underlying need often has a
simpler answer than the one that comes to mind first.

Some things are deliberately out of scope, and a proposal that changes them needs to
argue the trade-off rather than assume it:

- **No message history.** Sent messages are deleted, on purpose. A history screen is a
  copy of your texts sitting on the phone.
- **No backend, no third-party email API.** The app talks to your mail server directly.
- **Nothing that binds the SMTP key to user authentication.** Forwarding has to work
  while the phone is locked — [DOCS.md → App lock](DOCS.md#app-lock) explains why this
  particular hardening would break the product.
- **Not a Play Store app.** Google restricts SMS permissions to default messaging apps.

Already planned, so no need to file them: OAuth2 instead of app passwords, end-to-end
encrypted bodies, delivery channels other than email.

## Asking a question

Use [issues](https://github.com/cgwbmp/sms-mirror-app/issues) for those too. Setup
questions ("does this work with Fastmail?") are fair game, and the answers usually end up
in the docs.

## Reporting a security problem

If you've found something that exposes messages or credentials, please **don't** open a
public issue. Use GitHub's
[private vulnerability reporting](https://github.com/cgwbmp/sms-mirror-app/security/advisories/new)
instead, and give it a few days before disclosing.

[DOCS.md](DOCS.md) states the limits of the existing protections plainly — the app lock,
the address-change gate and what they don't cover. Something already documented there as
a known limit isn't a vulnerability, though an argument that it's worse than stated is
welcome.

## Setting up to build

You need **Docker**, and nothing else — the JDK, the Android SDK and Gradle all live in
the image.

```sh
./dev.sh setup     # image + wrapper + debug key — once, around 10 minutes
./dev.sh build     # debug APK
./dev.sh test      # JVM unit tests
./dev.sh install   # adb install onto a device or emulator (host adb)
```

Android Studio can open the project and build on the host instead; the two don't
conflict. On Apple Silicon, turn on *Docker Desktop → Settings → General → "Use Rosetta
for x86/amd64 emulation"* first — the image is `linux/amd64` because Google ships no
arm64-Linux build tools.

[DOCS.md → Build](DOCS.md#build) has the details, including why the debug keystore lives
in the repo directory and how to verify a change on a real phone.

## Making a change

- **Kotlin official style** (`kotlin.code.style=official`), lines around 100 columns,
  matching what's already in the file you're editing.
- **Comments explain why, not what.** The existing ones exist because the reason was
  non-obvious — a constraint, a platform quirk, an ordering that looks arbitrary and
  isn't. Add that kind; skip the kind that restates the code.
- **Test the pure logic.** `filter/`, `mail/EmailFormatter`, `mail/MailProvider` and
  `security/LockPolicy` have no Android dependencies and are covered by JVM tests in
  [app/src/test/](app/src/test/). A change to matching rules, email formatting or the
  lock timer should come with a test. `./dev.sh test` must pass.
- **Touching the Room schema means a migration.** The database holds a live outbox;
  destructive migration would drop messages that haven't been sent yet.
- **Anything on the SMS receive path stays fast and non-blocking.** It runs inside a
  broadcast receiver.
- **Don't commit** `local.properties`, `.keystore/`, `keystore.properties`, or an APK
  outside `dist/`.

Keep the change and the docs together: if you change behaviour that
[README.md](README.md) or [DOCS.md](DOCS.md) describes, update them in the same commit.

## Opening a pull request

- Branch off `main`, one topic per pull request.
- Say what you changed and how you checked it. "Tested on a Pixel 6, Android 15" is worth
  more than a description of the diff.
- Note anything you couldn't verify — an emulator can't prove Doze behaviour, and that's
  fine to say.
- Add a line to the *Unreleased* section of [CHANGELOG.md](CHANGELOG.md) for anything a
  user would notice.
- Version bumps and release builds are done by the maintainer — see
  [DOCS.md → Releasing](DOCS.md#releasing). Please leave `versionCode` and `versionName`
  alone in a pull request.

Review is friendly and mostly about the two things this app can't get wrong: not losing a
message, and not leaking one.

## License

The project is [MIT licensed](LICENSE), and anything you contribute is contributed under
those same terms. There's no CLA to sign.

You're equally free to skip all of this and fork it — that's what the licence is for. A
fork that solves your problem differently is a perfectly good outcome.
