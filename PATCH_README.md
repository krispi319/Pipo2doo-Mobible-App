# Pipo2doo 1.1.3 repair patch — setup and verification

## Changes made to the Android source

- **Task saving:** the task editor now displays a spinner until the server confirms
  success; on failure, the user sees the error and their unsaved draft stays open.
- **Tags:** create and select a tag directly from the task editor. Tags are
  serialized as independent `shared_records` of `kind='tag'`, with stable UUIDs,
  rather than reconstructed from task tag names. Task payloads store tag IDs.
  An old name-based tag remains visible, and renaming it migrates references
  from old tag name to UUID.
- **Photos:** list photos and appreciation images use authenticated, private
  Supabase Storage uploads and reads with a 5-MB JPEG limit, membership-scoped
  object paths, and a local app-private input-file check. Profile photos upload
  when users opt to share a profile.
- **Cross-phone refresh:** foreground polling interval reduced from 15 to 6
  seconds. This is **not** Realtime; an authenticated websocket subscription
  and backend publication are still needed for true instant updates.
- **UI:** new tasks default to today without a notification. The advanced
  reminder/private note settings are collapsed by default.

## Before running the modified app

1. Back up your original project and Supabase data.
2. Confirm the Supabase project in Android assets points to **your** instance.
3. Review and execute `backend/supabase/photo_storage.sql` in the Supabase
   SQL editor. Do not expose the bucket publicly.
4. Check that `mutate_shared_record` and `shared_records` accept a new `tag`
   kind. **The server function/schema were absent from the upload** and
   cannot be patched or tested without their definitions. Keep space-member
   checks and RLS; do not disable them to get tag writes working.
5. Confirm `space_members` RLS lets each space member read the member rows
   for their space, and `shared_records` SELECT permissions cover the same
   shared space. RLS misconfiguration can cause absent partner updates.
6. Install a debug build with Android Studio, signing into two separate
   authenticated accounts that have joined the **same** shared space.

## Acceptance tests — use two phones/accounts

- On phone A create a tag with **no task**. Restart phone A and inspect both
  phones. Both should see the tag.
- Add that tag to a task. Rename the tag from the tag manager. Both devices
  should show the new name without losing the task assignment.
- Create and edit a task while phone B is on the task screen. Changes should
  appear after at most one successful polling interval plus network time
  (usually around 6 seconds, not guaranteed).
- Try editing while offline: task editor must stay open with draft intact,
  and show an actionable failure message.
- Choose a gallery picture in a shared list, save it, and verify that both
  accounts can view the photo and a third unpaired account cannot.
- Repeat with an appreciation picture and an opted-in profile picture.
- Confirm app launch, task create/edit, image download, and tag rename in
  both light and dark Android appearance.

## Important remaining limitations

- **No Android build was completed** in this environment: Gradle wrapper
  attempted to download its distribution and the network was unavailable.
  Compile and emulator/device tests are still required.
- **No live Supabase integration tests** or schema inspection were possible.
  Tag persistence depends on the existing RPC accepting `kind='tag'`;
  photo sharing depends on creating the Storage bucket and policies.
- **Not true realtime:** the 6-second refresh is a stopgap.
- **Multi-record saves aren't atomic:** partial remote updates can occur
  if a later operation fails; eventually move coordinated writes into a
  server-side transaction.
- **Task events/history, push/pokes, and shared profile avatar mapping**
  remain incomplete as in the original project.
- **Photo object deletion and orphan cleanup** are not implemented. Uploaded
  objects remain after removal from list/note records and should be cleaned
  up by a server-side job with verified reference/ownership checks.
- Do not publish the modified app or use real sensitive photos until you
  have tested server policies and completed two-account security testing.
