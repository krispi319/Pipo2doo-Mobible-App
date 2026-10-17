# Pipo2doo v1.1.3 — Classic scheduling layout

This is a small UI-only follow-up to the v1.1.3 repair patch.

## What changed
- The `Gentle reminder` card and all reminder presets are always visible in the
  New Task / Edit Task screen, as they were in v1.1.2.
- The `Private note` section is also always visible.
- Removed the `Reminder & private note` expandable row.
- Left the due-date/time selector, repeat chips (Once, Daily, Weekly), and the
  complete custom reminder selector unchanged.
- Left v1.1.3 tag persistence, inline tag creation, photo code, shared sync, and
  task save-result callback unchanged.

**Defaults are still from v1.1.3:** new tasks start Today, Anytime, with
Gentle reminder OFF. Tap `Anytime` to pick a time and toggle Gentle reminder ON
to display the preset reminder choices. This avoids silently adding reminders
to new tasks.

## Getting started (Windows / Android Studio)
1. Back up your current v1.1.3 project.
2. Extract this ZIP to its own folder (do not merge it into v1.1.2).
3. In Android Studio open the folder containing `settings.gradle.kts`.
4. Wait for Gradle Sync. Build > Make Project.
5. Select your running Android Emulator, then Run `app`.
6. Open New Task and confirm you can see `Gentle reminder` without
   expanding a separate section.

**One-file alternative:** In your existing v1.1.3 project, replace ONLY
`app/src/main/java/ai/mwm/client/ui/screens/TaskEditor.kt`
with the version in this ZIP (make a backup first). Then Sync / Build / Run.

## Acceptance checks
1. New Task shows When (Today/Tomorrow/Pick date), due time, and repeat choices.
2. Gentle reminder is visible without an extra tap.
3. Turn reminder ON. Verify all presets and the Custom time controls appear.
4. Check that the Private note section is shown.
5. Create a `Shopping` tag, close and reopen the app, and check that it remains.
6. Save a task with a tag, due time, repeat, and reminder; reopen to verify.

## Known limitations
- Source-only validation completed, but a full Android build and emulator test
  were not possible in this offline environment. Build and test on your PC.
- The shared tag feature still requires the existing Supabase backend to support
  `tag` records. See PATCH_README.md. Real-time sync and cloud photo features
  also depend on backend configuration.
- This zip does not change Pipo2doo's existing Gradle versionName (currently 1.0).
