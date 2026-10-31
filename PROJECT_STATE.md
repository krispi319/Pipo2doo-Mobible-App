# Pipo2doo project state — v1.4.2 (2026-10-31)

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
