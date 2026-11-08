# Published v1.4.7 / 158 — 8 November 2026

- User authorized series-time fix, content-sized chat bubbles and v1.4.7 release.
  Bulk fixed times now propagate to linked open/planned tasks and future templates;
  All open can reapply an already corrected occurrence's title/time. History,
  private fields, unrelated tasks, layouts and assignee notifications retained.
- Chat body no longer fills its maximum width: content wraps naturally up to the
  existing 87% cap, with 12/8 dp padding. Replies, photos, reactions and labels
  preserved. Native synthetic render inspected; sizing/local/paired tests pass.
- All 288 local tests passed with zero failures/errors/skips; debug build passed.
- Server series_time_occupancy correction DEPLOYED. Five date fixtures pass;
  private scope/empty search_path/direct execute restrictions retained.
  Security advisor comparison unchanged. Existing generator backup saved locally.
- Optional full synthetic live time-edit transaction did not run: confirmation
  expired. Follow-up found zero fixture accounts. Physical two-device tests remain.
- Published from 4144fa7 via successful workflow 42514229565. CI tests, full
  release lint, assembly and original-key signing passed. Public APK downloaded;
  checksum, package/version, matching certificate and both feature markers verified.
  Release: https://github.com/krispi319/Pipo2doo-Mobible-App/releases/tag/v1.4.7
  Evidence: verification/v147/.

# Unreleased series due-time fix — 8 November 2026

- User clarified All open must change 19:30 to 19:29 across existing and planned
  linked tasks. v1.4.6 only recovered one-occurrence scheduling saves; it did not
  implement bulk time edits.
- Working source now allows due time in From here / All open. All open with no
  changed shared fields reapplies the selected title/time to mixed open copies.
  Each affected date uses the selected app clock; timestamps are recomputed.
  Switching Anytime/fixed-time mode remains occurrence-only for compatibility.
  Completed/cancelled times, private fields and unrelated same-name tasks stay.
- Daily template selection follows the intended new time/zone (including a DST
  gap safeguard); new local weekly/daily occurrences inherit the new time.
- Server generator date-occupancy edge found when a time edit changes the series
  timezone. backend/supabase/series_time_occupancy.sql is a narrow guarded patch.
  SQL date fixtures passed and patch compiled in BEGIN/ROLLBACK only; NOT DEPLOYED.
  Deploy and verify it before releasing this app change. No persistent DB/task
  data changes and no release/version bump/commit made for this task.
- Added eight data regressions and three real time-picker/save/retry UI tests.
  All 286 final tests passed (zero failures/errors/skips) and debug APK built.
  Five SQL date fixtures passed; guarded server patch compiled in rollback-only
  transaction and remains undeployed. No physical phone/two-device test.
  Evidence: verification/series-time-fix/. Published v1.4.6 remains unchanged.

# Published v1.4.6 / 157 hotfix — 7 November 2026

- Screenshot error traced to series-field validation; user confirmed editing
  date/time/repeat/reminders. Existing From here / All open scope intentionally
  supports shared title, assignee, list and tags only.
- TaskEditor now offers explicit Save this task confirmation for occurrence-only
  fields; Keep editing retains the draft/scope and failed saves allow retry.
  Occurrence submissions merge only edited fields into the latest displayed task.
- Series title edits no longer trim untouched private notes or normalize schedules
  before validating series intent, fixing false rejection from note whitespace.
- Main page layouts, completed/cancelled history and backend permissions unchanged.
- Added note-whitespace regression and three UI confirmation/keep/retry cases for
  date, reminder and repeat. All 275 local tests passed. CI run 42422505633
  passed tests, release lint, build and signing; published from ab5eeb6.
  Downloaded APK checksum, package/version and original signing certificate
  verified. Release: https://github.com/krispi319/Pipo2doo-Mobible-App/releases/tag/v1.4.6
  Evidence: verification/v146/. No physical device was connected.
- Local Gradle metadata resolution restored with temporary links to already cached
  artifacts; no repository dependency/build configuration changed.

# Published v1.4.5 / 156 — 7 November 2026

- Applied approved third love-day design, Celebration first, only in LoveCelebrationOptions.
  Live next-milestone card, three compact tabs, persistent draft/note, preview,
  reminder timing/advance/permission controls and guarded save/retry are retained.
- Full 271-test suite passed with zero failures/errors/skips; release APK assembled
  with version 1.4.5 / 156. Screenshot inspected. Local lint could not resolve remote dependencies even after refresh; full lint
  and tests now gate signed release publication in CI. No physical device connected.
- Read-only backend check confirms cancellation validator, guard and enabled trigger.
  No schema/user-data changes. Live two-device delivery and encrypted backup
  round-trip testing remain outstanding; no zero-bug or measured speedup claim.
- Published from de01c37 via successful workflow 42403340342. CI tests, full lint,
  release build and signing passed. Downloaded public APK checksum and original
  certificate match verified; package/version 1.4.5 / 156 confirmed.
  Release: https://github.com/krispi319/Pipo2doo-Mobible-App/releases/tag/v1.4.5
  Evidence: verification/v145/release/. After explicit user approval, the live
  synthetic recurrence/cancellation transaction passed and rolled back. Follow-up
  query found zero synthetic test accounts. See live-recurrence-results.json;
  released APK, persistent production data and schema remain unchanged.

# Prepared v1.4.5 (build 156) — 2026-11-05

- Applied approved compact Us & settings header/account card in v1.4.5 / 156.
  New Us-only CompactSettingsProfiles uses live 108 dp photos with edit/lock and
  milestones action. Text gaps are 30% tighter; date/status/account/actions stay
  live and the two connection buttons remain equally sized. All source after
  this card, including shared SettingsOrbit, is unchanged. Full build and 270
  tests passed. Updated APK installed in place on emulator-5556 with hash match.
  Evidence: verification/v145/us-compact/. No public release.
- Applied approved larger avatars only to the Our milestones couple card in
  v1.4.5 / 156. Portrait frames are 108 dp (50% larger), adapting on narrow
  screens, with themed rings and a separated date footer. Live photos, own
  edit/partner lock, names, roles and computed days/date are preserved. Shared
  SettingsOrbit and other pages remain unchanged. Full build and 267 tests
  passed; updated APK installed in place on emulator-5556 and hash verified.
  Evidence: verification/v145/milestones-larger-avatars/. No public release.
- Applied approved Slim switcher to Period & Care in v1.4.5 / 156. A shared
  CareSlimHeader presents the compact title, name dropdown, Refresh and cycle
  summary for solo, unpaired cloud and paired views. Later cards/calendar/forms,
  reminders, care ownership/sharing rules and page spacing are unchanged.
  Full debug build and 264 tests passed; four Care UI checks cover local/cloud
  solo, both paired roles, private/offline partner guards, switching and long
  names. Solo/paired screenshots match byte for byte. Installed in place on
  emulator-5556, with APK hash verified and native header controls observed.
  Evidence: verification/v145/care-slim-switcher/. No public release.
- Solo/paired recheck (7 November): full debug build and 260 automated tests
  passed, including offline/signed-in/unpaired-space Cancel visibility, both
  paired roles, a solo UI save/reload/History flow, and ongoing-notification
  removal while retaining unrelated notifications. Only regression tests and
  verification notes changed; production layouts/permissions remain intact.
  Solo used isolated test data; the active paired account was not switched or
  modified. Current device reports a subsequent November 7 APK installation;
  earlier hash matching below records the previous in-place update. Evidence:
  verification/v145/task-cancellation/solo-paired-recheck-results.csv.
- Applied approved task cancellation (7 November): matching compact Poke/Cancel
  pills, occurrence/stop-series confirmation and optional reason. Cancellation
  retains actor/time/reason in shared history, excludes tasks from overdue,
  reminders and completion/streak credit. Progress heading is now History;
  existing completed row styling is retained with cancelled rows added beside it.
  The guarded shared write remains network-first and conflict checked.
  Backend cancellation validation is deployed; existing task/list permissions
  and notification assignment rules are unchanged. Wire status remains Done
  for older-client compatibility; v1.4.5 uses cancellation metadata to distinguish
  History entries. Both devices need the updated APK to show cancellation labels.
  Full Android regression suite: 256 passed; final dialog checks/build recorded
  in verification/v145/task-cancellation/. No GitHub release. Follow-up paired
  check: emulator-5556 was still on the November 5 APK despite showing 1.4.5.
  Updated it in place to the verified cancellation APK; native Today screenshot
  confirms Cancel beside Poke with the existing account/tasks retained. Installed
  and local APK SHA256 match (9606f4f8601635dc8e52b8ebd5f7d98482c865879f4c769f34a1087b95d10336).
  Earlier compact Us header/love-day layout previews remain unapplied.
- Applied the approved Together progress strip to production: height 32 dp
  (previously 48), horizontal/vertical padding 10/4 dp, 14 dp heart and compact
  label. Counts, colours, chevron and tap filter unchanged. Existing Today/locket
  UI check passed; debug build installed on emulator-5554 with data preserved
  and live screenshot verified. Evidence: verification/v145/together-strip.png,
  together-strip-emulator.png and together-strip-ui-result.xml.
- Task-reminder settings now load concurrently with the shared snapshot instead
  of afterward. Existing fallback, cancellation, membership checks and reminder
  rules retained. Fourteen targeted startup/reminder checks passed and debug APK
  built. Updated emulator-5554 with install -r, preserving app data, and verified
  a fresh launch reaches Today with existing account/tasks/photos. No matched
  before/after timing or speedup claimed. See verification/v145/parallel-reminders.md.
  No GitHub release.
- Applied compact Our Locket to the production Today screen below avatars/
  timezones/progress, above tasks, with photos visible by default and one collapse
  header. Gallery/share/caption/heart callbacks retained; upload times remain in
  detail views. Backup card now has tighter spacing, equal 36 dp buttons and a
  header info icon for View status. Four targeted UI checks passed; evidence:
  verification/v145/locket-backup-ui-results.csv, locket-top.png, backup-compact.png.
  Task cards, avatar/timezone layouts and other preview-only changes untouched.

- Approved startup trial: first authenticated snapshot opens Today with current
  tasks/profiles before loading message/locket history independently. Exact-count
  paging avoids redundant final empty requests and handles server row caps. No
  SDK initialization or persistent partner-data cache change. Phone timing remains
  unmeasured. Source rollback checkpoint preserves the earlier uncommitted v1.4.5
  work: verification/v145/startup-backup-rollback/README.md.
- Added Account backup → Restore latest, with replacement confirmation and status
  feedback. Downloads saved private sections without uploading first; validates
  account/workspace and payloads, serializes edits and attempts local rollback on
  failure. Shared tasks/lists/profiles/date and shared Care history stay current.
  Latest-only backup; no historical version selector. No real user backup was
  restored during tests. See verification/v145/startup-and-restore.md.

- Final full unit/Compose suite: 236 passed, no failures/errors/skips. Latest
  evidence: verification/v145/startup-restore-test-results.csv. Earlier love-day
  verification included
  real save/retry and preview-return draft retention, once-only automatic card,
  leap-day/DST/quiet-time calculations, notification deduplication and channel/
  account/workspace isolation. Evidence: verification/v145/full-test-results.csv.
  Actual app renders: verification/v145/love-*.png and settings-*.png. Real phone
  delivery/permission flows remain a manual acceptance step. No release.
- User approved the love-day celebration preview for v1.4.5. Added upcoming
  100-day/yearly/custom milestones, personal reminder timing/advance notice,
  cat/hearts/glow effects, sound/haptics, literal optional note, save/retry and
  celebration-day display. Day-zero count retained; leap-day anniversaries use
  Feb 28. Counter opens milestones; existing Celebrations card has an Open entry.
  Custom day input expands inline. Preview/Back preserves unsaved selections and
  note; successful saving clears the old draft. Milestone photo editing uses the
  existing picker; the celebration card is read-only. Main avatars/date unchanged.
- Personal settings persist in the existing account-owned preferences/backup
  JSON; the shared together date remains the single source of milestone dates.
  No schema or partner-message change. Local preferences serialize with the
  shared sync lock to avoid being overwritten by in-flight shared mutations.
- Love reminders have independent channels, quiet-hour adjustment, deduplication,
  account/workspace-scoped cache and boot/time-change rescheduling. Notification
  taps open the milestone view; in-app celebration appears once on an eligible
  milestone date. No exact delivery promise or foreground service.
- User approved the final folder-only Settings preview as the first v1.4.5
  change. Shared-space sync status, profile/device preferences, timezones,
  gentle reminders, chat visibility, notifications and Period & care now live
  inside one Settings folder. Existing controls and callbacks are retained.
- Existing avatar/name/days-together/date card, theme and remaining main-page
  cards are preserved. Connection details and Check setup have equal dimensions.
  Main/folder scroll positions are separate; Android Back and the visible Back
  button return to the main Settings page. Name draft state remains outside
  the folder content so navigation does not discard it.
- No Lists redesign or unrelated-page layout change is included. Existing task
  celebration controls are retained. Version prepared as 1.4.5 / 156; no commit,
  push or release requested for these changes.
- Eight targeted UI checks passed: folder contents/care access, equal button
  sizes, name draft/save after hardware Back, visible Back, chat visibility,
  unchanged Today clocks and full Create task saving. Evidence and actual app
  renders: verification/v145/; scope notes: verification/v145.md.

# Published v1.4.4 (build 155) — 2026-11-01

- Published release ID 430493215 from 75b4dbca59eae883fb4736fb64fc914d600f73d7.
  Signed build workflow 41295097160 succeeded. Public APK downloaded independently:
  version 1.4.4 / 155, original application ID, checksum and signature verified;
  signing certificate matches the downloaded v1.4.3 APK. Evidence:
  verification/v144/published-artifact.json. Full final suite: 201 passed,
  no failures/errors/skips; verification/v144/release-test-results.csv.

- User requested publication of v1.4.4 and rejected the Lists redesign. Existing
  Lists source is unchanged. Preview-only Lists/editor concept tests and concept
  artifacts remain local and are excluded from the release commit. Only the
  agreed recurring-series fixes and approved edit/delete adjustments are included.

- User approved the title-first recurring editor and clearer delete action on
  2026-11-01. Applied title-first ordering, reduced helper copy, selected-pill
  checkmarks, optional private-note expansion and red Delete styling. Reminder
  options still expand when enabled; existing notes start expanded. Full Create
  task form, other pages and task mutation behavior are preserved. Original
  concept images remain in verification/v144/. Other-page recommendations are
  discussion only and require agreement before implementation.
- Approved visual refinement verified with nine passing UI checks covering full
  Create task saving, Today avatar/timezone visibility, series selection/save,
  delete cancellation/failure/retry and avatar previews. Current renders:
  verification/v144/approved-editor.png and approved-delete.png; results:
  verification/v144/approved-ui-test-results.csv.
- User requested less text and compact presentation. Edit scope now uses one
  three-option row (This task / From here / All open), a one-line summary, and
  optional info help. Delete uses the same row, a count card and history note.
  Cream theme retained. Nine UI checks passed; compact previews in verification/v144/.
- Follow-up sizing: smaller scope labels/padding, recurring-only 56dp avatars,
  12dp section gaps, and a tighter delete dialog with 16dp padding. Create task
  keeps its existing sizing. Nine UI checks passed; latest previews use -tight.png.
- Latest scope-button redesign: three content-width pills with roughly 70% less
  visible area, readable labels and separate 48dp tap targets. No full-width bar.
  Nine UI checks passed. Latest previews use -mini.png in verification/v144/.
- User requested editing/deleting the entire linked recurring series, including
  already generated and planned tasks. User chose to keep completed history.
- Added All unfinished occurrences for title/assignee/list/tag edits, alongside
  Only this occurrence and This and future occurrences. Includes earlier overdue
  copies and updates the active template so later generated tasks inherit changes.
- Saved series/parent links identify membership; matching titles never merge tasks.
  Linked Once occurrences retain series controls. Future edits retain family links.
- Delete dialog now offers one occurrence, this/future, or Entire series, with a
  reviewed count and confirmation. Bulk deletion keeps completed content/status,
  stops generation, and keeps failures open for retry. Single-template deletion
  promotes another occurrence so the remainder continues normally.
- Added SECURITY INVOKER mutate_recurring_tasks, delegating every write to existing
  task/version RPCs. Checks the complete linked ID set under the generator's lock;
  changed sets/conflicts fail atomically. Existing permissions and tables unchanged.
- Supabase v144_guarded_recurring_task_operations deployed. Synthetic rollback
  tests passed, including actual authenticated-role access, paired edits, isolation,
  conflict rollback, concurrent generation, history, no regeneration and alerts.
- Full 200-test unit/Compose suite passed, followed by the final recurrence-only
  regression rerun. Includes already-corrected title sync, partner field changes,
  original timezone/DST and template assignee preservation.
  UI previews and evidence: verification/v144/;
  acceptance guide: verification/v144.md. Existing Today/Create task layouts retained.
- Published version 1.4.4 / 155 with APK/checksum and user-facing release notes.
  No live user-task deletion/rename performed. Unrelated IDE changes preserved.

# Published corrected v1.4.3 (build 154) — 2026-10-31

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
  its tag. Workflow 41218115156 succeeded. Old release 429975230 was removed;
  replacement 430040957 and tag v1.4.3 point to a1f159a. Downloaded APK independently
  verified as 1.4.3 / 154 with a valid signature and matching checksum.
  Previews: verification/today-layout-fix/ and
  verification/create-task-restored/. Evidence: verification/v143-replacement/.

# Earlier v1.4.3 build 153 (superseded) — 2026-10-31

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
