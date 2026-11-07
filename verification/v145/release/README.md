# Published v1.4.5 / 156

Approved third love-day design, Celebration first, applied in the existing options
page. Other pages retain their previously approved v1.4.5 changes. Draft state,
custom-day validation, reminder/advance timing, permission action, effects,
personal note, preview and save/retry remain connected to the existing logic.

- Local full regression suite: 271 tests, zero failures/errors/skips.
- Native Robolectric celebration render inspected: celebration-first.png.
- GitHub run 42403340342: tests, full release lint, assembly and signing passed.
- Public APK downloaded; SHA256 matches the published checksum. Signature verifies
  and certificate matches v1.4.4. Package unchanged; version 1.4.5 / 156 confirmed.
- Source/tag commit: de01c37d97005c541a0502abcc324a97779f163b.
- Release: https://github.com/krispi319/Pipo2doo-Mobible-App/releases/tag/v1.4.5

Local lint could not resolve Maven artifacts, so the successful Linux CI lint is
the release lint evidence. No checks were disabled in the published CI build.
Backend read-only checks confirmed the cancellation validator, guard and enabled
trigger. After explicit user approval, the additional live synthetic recurrence
transaction passed and rolled back: paired cancellation, audit fields, old-client
protection, version conflict, shared read, daily-root re-anchoring, stopping series,
outsider isolation and notification invalidation. Follow-up query found zero
synthetic cancellation-test accounts remaining. See live-recurrence-results.json.
No persistent production data/schema or released APK was changed.

No emulator/phone was connected. Physical two-device notification/recurrence
delivery and encrypted account-backup restore round trips remain unverified.
No zero-bug guarantee or measured startup speedup is claimed.

Generated build files, local previews, screenshots and unrelated files were
excluded from the release source commit. Existing working files were preserved.
