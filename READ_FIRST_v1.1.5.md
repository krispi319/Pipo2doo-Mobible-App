# Pipo2doo 1.1.5 — image sharing and Poke wiring candidate

Use this complete project instead of the earlier 1.1.4 ZIP. Open its `settings.gradle.kts` folder in Android Studio, sync, build and run on BOTH phones. Do not uninstall or clear app data. This is source code, not an APK; Android compilation/device delivery remains unverified in this environment because Gradle downloads are unavailable.

## Completed on the live backend

- Created the PRIVATE `pipo-shared-images` bucket: JPEG only, maximum 5 MB.
- Applied member-scoped SELECT/INSERT policies. The old `partner-images` bucket is unchanged.
- Applied `backend/supabase/poke_hardening.sql`: reject completed tasks at request/claim, rebind a device token to one account, and treat equal quiet-hour start/end as an empty interval.
- Ran `backend/supabase/image_poke_tests.sql` successfully. It tests member upload/read metadata, partner reads, outsider denial, Poke opt-in, device registration, request idempotency, 15-minute sender cooldown, opt-out cancellation, completed-task rejection, and previous core regression tests.
- These tests use temporary database users, fake device tokens and storage METADATA inside a rolled-back transaction. They do NOT send an FCM message or upload/download real image bytes.

## Android changes

- Connected Poke sending to the existing Supabase `partner-poke` Edge Function.
- Registered Firebase Messaging tokens using the signed-in SUPABASE identity. Firebase Authentication is no longer required for the Poke receiver.
- Registration occurs after the recipient enables Receive pokes. Server quiet-hour/consent preferences are synchronized; device deregistration runs on sign-out/unpair/opt-out.
- Notifications recheck the event recipient, active shared membership, expiry, task completion and assignment before displaying or opening the task.
- Deduplication and notification links are account-scoped.
- A request ID is reused after an ambiguous network failure to avoid duplicate sends.
- The task-detail Poke button now receives the selected task, including partner-assigned read-only tasks.
- Inherits 1.1.4 task/tag/list/reminder fixes and the classic scheduling layout.

## Phone checks — one at a time

1. Build and install 1.1.5 on both phones with Android Studio. Pair two different accounts into the same space.
2. On the receiving phone, allow Android notifications. In the app's Us/Settings screen enable **Receive [partner]'s pokes**. This creates the Partner pokes channel and registers that phone. Wait for the foreground sync to complete.
3. For the first test, use a time outside quiet hours, or temporarily disable quiet hours on the receiving phone.
4. On the sending phone open an unfinished Together task or a task assigned to the receiving partner; tap Poke once.
5. `provider_accepted` means Firebase accepted the message, not that the phone displayed it. Verify the actual notification on the receiving phone, then tap it and check the correct task opens.
6. Respect the 15-minute sender cooldown. Test opt-out and completed tasks separately.
7. For photos, choose a picture in a shared list and save it. Confirm the other phone can load it after sync.

## Firebase sender credential: configuration remains unverified

The existing deployed Edge Function expects a production secret named `FCM_SERVICE_ACCOUNT_JSON`, containing a service account JSON for Firebase project `pipo2doo-bfc83` with permission to send FCM messages. Its existence/value could not be inspected with the available tools. No credential was generated or installed during this change.

Check the NAME only in Supabase > your Pipo2doo project > Edge Functions > Secrets. If it already exists, do not create another key just for this check. Build/test the app and inspect any returned error.

If absent, configure it through the Firebase and Supabase dashboards. Keep its value in server secrets only; never put the service account JSON in Android assets, the ZIP, Git or chat. `google-services.json` is client configuration and is not a substitute for this server credential.

Official references:
- https://supabase.com/docs/guides/functions/secrets
- https://firebase.google.com/docs/cloud-messaging/send/v1-api
- https://firebase.google.com/docs/cloud-messaging/android/get-started

## Remaining limitations

- Full Android build, real picture byte transfer and end-to-end FCM delivery have NOT been tested here.
- The existing Edge Function was inspected, not redeployed. Its Firebase sender configuration and Google permissions remain unverified.
- Background data messages can be delayed or dropped by Android; force-stopping the app prevents normal delivery until reopened.
- Notification authorization is checked before display; concurrent consent/task changes can still race after the final check.
- Foreground sync polls every 6 seconds. Multi-record edits are not atomic; photo orphan cleanup remains unimplemented.
- Existing security advisor notices about privileged public RPCs and disabled leaked-password protection remain. See https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable.

Backend patch files are included as a record of work already applied. Do not rerun `poke_hardening.sql` blindly; it patches the definitions inspected on 2026-10-17.
