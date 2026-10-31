# v1.4.3 replacement preparation (build 154) — 2026-10-31

- Compact Today preview accepted by user. Restored full Create task form as the
  default for new tasks; the earlier quick-add entry had hidden it behind More
  options. Existing avatar choices, scheduling, repeat, tags, reminders, notes,
  save callbacks and draft saver remain. Preview: verification/create-task-restored/.
- Removed the couple-overview collapse toggle. Both avatar/timezone cards are always
  visible on Today in shorter cards with consistent 20dp side margins.
- Today task cards share those margins and use compact, left-aligned contents.
  Completion, assignment colors and Poke controls remain. Duplicate name-filter
  pills removed; avatar/Together cards filter tasks and All tasks resets the view.
- Moved shared-space sync status and Refresh/Retry from the global page header into
  Us & settings > Shared space, including saving/pending/offline state.
- Full 183-test suite passed for build 154. Guard checks passed for replacement
  ordering, changed-release/tag cancellation, and ordinary create/upload behavior.
- User explicitly requested removal and replacement of old v1.4.3. The version
  name stays 1.4.3; internal Android code increases from 153 to 154. Existing 1.4.3
  users must download the replacement once because the old updater compares names.
- Workflow now has an explicit replace_existing manual flag; it builds, signs,
  verifies and saves the candidate APK before deleting the old release/repointing
  its tag. Publication pending. Previews: verification/today-layout-fix/ and
  verification/create-task-restored/. Evidence: verification/v143-replacement/.

# Published v1.4.3 — 2026-10-31

- Published release v1.4.2 points to ae9f669; build-script lint fix and signed APK
  publication succeeded. Current version 1.4.3 / 153 published from 770e61d.
- Pending source includes the avatar-only assignee circles and padded time button;
  their existing previews are in verification/assignee-preview.
- Chat unread badge now observes persisted markers, clears existing incoming IDs when
  the foreground conversation opens, and counts newly arriving messages afterward.
  Server Seen receipts still require actual foreground-visible messages.
- Added Us > Chat visibility > Show online status; opt-in starts Invisible. Chat
  header shows partner Online/Offline separately from the current user's visibility.
- Foreground heartbeat every 20 seconds; background/sign-out attempts remove this
  device's heartbeat. Interrupted sessions expire after 70 seconds. Invisible is
  account-wide and hides timestamps across devices; status is partner-scoped.
- Supabase migration v143_opt_in_chat_presence deployed: two RLS-protected tables,
  owner-controlled preference, session membership FK and SECURITY INVOKER RPC.
  Read-only permission/schema checks passed; security-advisor findings unchanged.
- Full synthetic-account presence SQL behavior test passed and rolled back. See
  verification/chat_presence_test.sql. Physical two-phone acceptance remains manual.
- 29 selected chat/UI tests passed; previews and evidence in verification/chat-visibility.
  Details and acceptance steps: verification/chat-visibility.md.
- Task detail changes show saving/success/failure, preserve note/move drafts on failure,
  and retry only the requested field changes while preserving newer partner edits.
- Explicit Update now downloads, verifies and opens Android's installer; passive
  checks never install. Android confirmation and first-use install permission remain.
- Task reminders and visible task push alerts follow assignment: Me only, partner
  only, or both for Together. Both members retain editing permissions. Silent sync
  reaches the former assignee to cancel cached reminders when connected.
- Supabase v143_assignment_scoped_task_notifications deployed. Synthetic rollback
  tests passed: both individual assignments, Together, reassignment cancellation,
  stale recipient rejection, silent sync, list independence, opt-out and completion.
- 54 selected tests and then the full 183-test suite passed. Signed release build,
  release lint, APK version/signature verification and publication succeeded.
- Release: https://github.com/krispi319/Pipo2doo-Mobible-App/releases/tag/v1.4.3
- Workflow: https://github.com/krispi319/Pipo2doo-Mobible-App/actions/runs/41208319985
- Release evidence and device acceptance: verification/v143.md.

# Earlier project state — v1.4.2 (2026-10-31)

- Working checkout: D:\Krisative\Coding\Pipo2doo App\Pipo2doo-Mobible-App.
- Android Kotlin / Jetpack Compose; version 1.4.2, version code 152.
- Both paired members may edit shared tasks/lists regardless of assignee; assignment
  labels/filtering and separate care/profile/note ownership remain intact.
- Recurring task editor offers Only this occurrence / This and future occurrences.
  Future scope applies changed title, assignee, list and tags to unfinished linked
  occurrences and future generation. Dates, times, repeat settings, reminders and
  private notes remain occurrence-only. Completed content/status and task IDs remain.
- Daily edits split the active template when needed; future completed dates are linked
  without rewriting their content so the generator does not duplicate those dates.
- Quick task sheet opens for ordinary new tasks; More options retains the draft and
  opens the existing full editor. Existing board/status-specific entry remains full.
- Today places tasks earlier; couple details and Locket expand without losing access.
  Existing tabs, theme, artwork and other screens retained.
- Shared-space banner reports saving/pending/offline and last successful server refresh;
  it does not claim the partner's phone received the update.
- Task/list delete dialogs wait for success, retain failures for retry, and explain the
  effect on both partners. List removal and task removal use one server transaction.
  This release does not add an Undo/recycle-bin feature.
- Supabase migration v142_atomic_task_edits deployed to existing Pipo2doo project.
  mutate_shared_tasks is SECURITY INVOKER and delegates writes to the existing
  permission/version RPC. Existing permission policies remain unchanged.
- See verification/v142.md for verification evidence, screenshots and phone checks.
- No signing, publishing, uninstall, data reset or unrelated redesign performed.

## Earlier project notes (v1.2.0)

- Android Kotlin / Jetpack Compose; Supabase Auth, database, storage, backend;
  Firebase Cloud Messaging only for push.
- Version name 1.2.0, internal version code 120; application ID unchanged.
- Existing cute UI, tasks, scheduling, reminders, tags, locket, chat, partner
  pairing, separate care ownership, and notification sounds retained.
- New AccountBackup.kt coordinates two owner-private backup sections: main/care.
  Versioned compare-and-swap writes prevent stale phone overwrites. Canonical
  JSON hashes detect local changes. Fresh installations restore before upload.
- Main restoration occurs after authenticated account activation and before
  shared-space snapshot import. Care restoration occurs within CareViewModel's
  mutation lock and is reloaded into its state before shared care refresh.
- Main backup preserves prior-space marker so pairing with a different partner
  still triggers existing date/reset/isolation rules. Care retains its marker.
- New account_backups table / save_account_backup SECURITY INVOKER RPC deployed
  to the existing project. Three owner-only policies. No anonymous access.
- Private photos are content-hashed and embedded only in the owner's backup.
- New Us > Account backup panel has per-section status and Back up now action.
- Existing database advisor warnings concern earlier membership RPCs and auth
  settings; no new warning identified for account_backups or its invoker RPC.
- Tested: SQL isolation/CAS, 19 core backup behavior checks, 3 care round trips,
  seven Kotlin files parsed. Full Android build/device upgrade tests outstanding.
- Release acceptance: build signed APK with existing key; update in place first;
  both backup sections saved; test fresh emulator restore using same account;
  then publish signed APK as v1.2.0.
- Phone data is NOT uploaded by this source patch; only the installed app can
  perform the first private backup. Never advise uninstall before that upload.
