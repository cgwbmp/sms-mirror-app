# Changelog

All notable changes to SMS Mirror are recorded here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project follows
[semantic versioning](https://semver.org/spec/v2.0.0.html).

`versionCode` is the integer Android compares when installing an update, and is listed
alongside each release.

## [Unreleased]

## [1.0.0] — 2026-08-13 (versionCode 1)

First release. Verified on a Pixel 6 (Android 17, API 37); 21 unit tests pass.

### Added

- Forwarding: incoming SMS → durable Room outbox → WorkManager → SMTP over TLS → your
  own inbox. Multipart messages are reassembled before sending.
- Send-to-self: one account is both sender and recipient, so a single app password is
  stored.
- Rule-based filtering — sender allow/block, keyword, regex and an OTP heuristic — with a
  "forward everything" master switch. Block rules win over everything else.
- Phone-number matching across formats, by digits, with a 4-digit floor so a short rule
  can't match every number.
- SMTP autoconfig from the email domain: host and port are filled in and locked for a
  known provider; unknown domains get editable fields prefilled with `smtp.<domain>`.
  Outlook and Proton Mail are flagged as likely to refuse password sign-in.
- Setup saves only after the credentials have been proven by a live test email, so a
  typo can't overwrite a working configuration.
- Changing the forwarding address first emails the outgoing address, naming both
  addresses and the time. Refused while offline rather than deferred.
- App password encrypted under an Android Keystore key, deliberately not bound to user
  authentication so forwarding continues while the phone is locked.
- "Forget saved password" asks for confirmation first, naming both consequences: it
  switches forwarding off, and the password can't be shown again.
- App lock on the phone's biometric or screen lock, re-locking after 2 minutes in the
  background, with `FLAG_SECURE` to keep addresses and sender numbers out of screenshots
  and the recents thumbnail. Disabled automatically if the phone has no screen lock.
- Home screen: master switch, permission and Doze-exemption prompts, counters, and a
  "Needs attention" list of queued or failed messages with retry.
- Notification on a failed forward, switchable off in Setup.
- Reboot recovery via `BootReceiver`.
- No retention: an outbox row is deleted the moment its send succeeds. No history, no
  sent log — only integer counters persist.
- Docker-based build (`./dev.sh`), so only Docker is needed on the host.
- MIT licence.

### Known limitations

- Offline queueing and reboot recovery are implemented but not yet verified on hardware.
- `targetSdk` is 35 while the test device runs API 37.
- Regex rules run on the SMS receive path with no match timeout.
- Sideload only — Google Play restricts the SMS permission group to default SMS handlers.

[Unreleased]: https://github.com/cgwbmp/sms-mirror-app/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/cgwbmp/sms-mirror-app/releases/tag/v1.0.0
