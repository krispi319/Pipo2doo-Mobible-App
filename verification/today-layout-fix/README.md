# Today compact layout preview

Native Compose preview: today-compact.png (360dp phone width, sample data).

- Consistent 20dp side margins across header, profiles, Together and task cards.
- Shorter equal-height profile cards; avatars, names and clock timezones stay visible.
- Slim Together summary, with tasks directly underneath.
- Removed duplicate name-filter pills. Tap a profile/Together card to filter;
  All tasks beside the section heading clears the selection.
- Task contents aligned left with existing completion/Poke controls retained.
- Shared-space status stays inside Us & settings, away from main-page headers.

The screenshot uses sample profiles and a partner timezone that has not been set.
The existing profile-photo and timezone data paths are unchanged.

Six existing Compose UI checks passed; per-class counts are in test-results.csv.
Included in the corrected v1.4.3 replacement APK, Android build 154.
