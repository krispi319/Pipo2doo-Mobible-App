**Superseded by READ_FIRST_v1.1.5.md. The photo backend is now enabled; Poke client wiring is added in 1.1.5.**

# Pipo2doo 1.1.4 — repair candidate

This is the full native Android source project, based on your latest saved v1.1.3 classic scheduling version. It is not an APK and is not yet certified for release.

## What changed

- Manage Tags waits for server confirmation on create, rename and delete. Failed writes keep your input and show the error.
- List editor also waits for confirmation. Failed saves keep the draft; retry uses the same list ID. Navigation occurs only after success.
- Anytime reminder preview, preset labels, notification scheduler and reminder ordering now use your configured default reminder time.
- Custom reminder controls no longer initialize with a truncated whole-day amount; existing offsets remain intact until you change the controls.
- Rotating/recreating the task editor retains assignment identity and safely serializes tag IDs containing commas. Older saved draft format has a fallback reader.
- Deleting a repeat-task ancestor preserves future occurrences and reparents them. Shared updates write surviving children before deleting the ancestor.
- Undo preserves an edited or started next occurrence, instead of deleting it.
- Saving a recurring task directly in Done status now creates its next occurrence.
- Android versionName is 1.1.4 and versionCode is 114.
- Classic scheduling, visible Gentle reminder card, private notes, repeat chips and reminder presets remain present.

## Live backend work completed on 2026-10-17

- Resumed your existing Supabase project; verified ACTIVE_HEALTHY after restoration.
- Existing tables/functions returned after restore. They were preserved, not replaced.
- Applied `backend/supabase/validation_patch.sql`: shared field allowlists, task schedule validation, case-insensitive tag uniqueness and protection against deleting a parent with live children.
- Executed `backend/supabase/regression_tests.sql` successfully against the live database using temporary users/data inside a transaction that rolls back.
- Verified no temporary test Auth accounts remained.

## Verified vs. unverified

| Check | Result |
|---|---|
| Supabase pairing, invitation cooldown and single use | Passed rollback database tests |
| Tag create, rename, delete; stable task tag references | Passed rollback database tests |
| Mutation idempotency and stale-version rejection | Passed rollback database tests |
| Assigned task protection, outsider reads/writes, direct table write denial | Passed rollback database tests |
| Reparent before deletion; private field rejection; unpair access revocation | Passed rollback database tests |
| Legacy Firebase JavaScript syntax | `node --check` passed; this is not the active shared-data backend |
| Full Android build and 15 new Kotlin regression tests | BLOCKED: Gradle distribution download fails with Network is unreachable |
| Android UI rendering and physical notification delivery | Not run; no Android SDK/emulator available here |
| Firebase Node unit suite | Not run: firebase-functions dependency unavailable locally |
| Photo uploads/downloads | Blocked by missing app bucket and pending approval |

The SQL tests exercise database roles and JWT claims. They do not replace two-device Auth/REST/UI tests or concurrent-request load testing.

## Photo setup: prepared, NOT applied

The Android app requests the private bucket `pipo-shared-images`; your backend currently has `partner-images`. These have different policies and path conventions.

`backend/supabase/photo_storage.sql` prepares the app's private 5-MB JPEG bucket and member-scoped read/upload policies. Automatic approval review rejected applying this change pending your approval of this exact storage-policy action. Do not treat the photo feature as repaired yet. Keep the original bucket and its policies.

## Build and check on your PC

1. Keep your installed app and existing project; do not uninstall or clear app data.
2. Extract this ZIP into a separate folder. In Android Studio choose **File > Open**, then select the folder containing `settings.gradle.kts`.
3. Allow Gradle Sync to download dependencies. Use the project's required Gradle JDK/toolchain when Android Studio prompts.
4. In the Gradle tool window run `:app:assembleDebug`, then `:app:testDebugUnitTest` (or run the two new test classes from the editor).
5. Run the debug app on your emulator/phone. Use the same application ID and signing key for an update; if Android reports a signing mismatch, do not uninstall without a data backup.
6. Add Shopping in Manage Tags, close/reopen and restart the app. Rename it and check that task assignment remains. Delete it and verify it stays deleted.
7. Turn off the network and attempt a tag/list/task save. The editor should stay open with an error and your input intact.
8. Set the default reminder time to 12:30; create an Anytime task with On time reminder. The preview should say 12:30, subject to quiet hours and Android alarm delays.
9. Complete a daily task, edit/start its next occurrence, then reopen the previous one. The future occurrence must remain.
10. With two separate accounts in one space, check tasks/tags/lists on both devices and confirm unrelated accounts cannot read them.

## Still incomplete

- Partner Poke push transport is explicitly unavailable in the source. It has not been implemented or verified in this repair.
- Foreground shared refresh still polls every 6 seconds. It is not a Realtime subscription.
- Multi-record cloud operations are not one atomic transaction; interrupted operations can partially save. Review the refreshed state after a failure.
- Shared task event history is not serialized by the existing server adapter. Repeat undo therefore conservatively preserves children whose untouched state cannot be proven after refresh.
- Photo orphan cleanup remains unimplemented.
- The existing security advisor still flags authenticated-callable SECURITY DEFINER RPCs, deny-by-default private tables without policies, and disabled leaked-password protection. Passing these focused tests is not a complete security audit. Reference: https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable and https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection.

The older PATCH_README.md, CLASSIC_SCHEDULING_README.md and backend/README.md describe previous releases. This file is the current verification record. The SQL setup scripts are included for review/reproducibility; validation is already applied to the linked project, while photo setup is pending.
