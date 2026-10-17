# Pipo2doo project state — v1.2.0

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
