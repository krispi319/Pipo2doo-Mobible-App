# v1.4.6 / 157 — task edit save hotfix

The reported footer text comes from the series-field validation guard. The user
confirmed changing a date/time/repeat/reminder. The editor previously allowed
these changes under a series scope, then rejected Save with no recovery action.

TaskEditor now checks that combination before submitting and offers explicit
Save this task confirmation. Keep editing retains the draft and scope. Once
confirmed, only the selected occurrence is saved; failed saves remain retryable.
The existing editor layout, shared permissions and series scope rules remain.
Occurrence submission also preserves newer untouched displayed task fields.

A separate false rejection was found in the same path: withTaskEdit trimmed
untouched private notes before the series guard compared them. Series intent is
now validated without normalizing occurrence-only fields. Title normalization
and existing shared-field propagation remain; private notes are not propagated.

Validation:

- All 275 local tests passed, zero failures/errors/skips; test-results.csv.
- New UI tests cover date, repeat and reminder edits, no submission before
  confirmation, Keep editing, failed-save retry and unchanged other tasks.
- Note-whitespace regression covers From here and All open.
- Existing Create task, series editing/deletion, completed/cancelled history,
  solo/paired permissions, reminder assignment and backup tests passed.
- No phone/emulator connected; physical-device testing remains unverified.

Local cached-artifact resolution used a temporary Maven repository linked to
existing cached artifacts. No source dependency/build configuration was changed.
No schema or user task data was modified.

Published release: https://github.com/krispi319/Pipo2doo-Mobible-App/releases/tag/v1.4.6
Source/tag commit: ab5eeb6eb787e0e509399ece6ba22261d0ed2f11.
GitHub run 42422505633 passed tests, full release lint, assembly and signing.
Downloaded APK signature verifies, certificate matches v1.4.5, SHA256 matches
the checksum, and package/version 1.4.6 / 157 are confirmed. See
published-artifact.json. Physical-device upgrade testing remains unverified.
