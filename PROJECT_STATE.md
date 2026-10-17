# Pipo2doo project state — 17 October 2026

Native Android Kotlin/Compose. Supabase handles pairing, shared records and image storage; Firebase is used only for FCM transport. Existing version label 1.1.6 is retained.

## This patch
- HomeScreen: floating chat shortcut, account/space-specific unread badge, chat deep links and correct routing ahead of Care.
- AppreciationScreen: existing note feed extended into Our chat with Message/thank-you choices, draft retention, stable retry IDs and newest-message scroll.
- PipoViewModel: result callback for message sends, account/partner guard and duplicate-ID protection.
- ChatReadState: device-local ID markers, no message bodies.
- PartnerSync: requests optional note push only after record save. Existing shared data remains network-first.
- PipoFirebase: fresh authorized note/sender/profile reads. Notification data from FCM is IDs only.
- PartnerPokeService: message channel, sender avatar and MessagingStyle, notification-to-chat routing; Poke and Locket gain avatar artwork. Account changes, sign-out/unpairing clear partner notifications and conversation shortcuts.
- Reminders: app logo large icon and monochrome two-cat small icon.
- Progress: original vector cat garden, selected range count and existing streak.
- Settings: partner message opt-in/channel labels and avatar-sharing explanation.

## Backend deployed
New partner-note v1 ACTIVE with JWT verification enabled; claim_note_notification is service-role-only, private ledger RLS enabled, no client table access. Existing partner-poke v6 and partner-locket v4 are unchanged. Credentials remain server-side. No real push was sent as part of this change.

## Validation and remaining work
Source syntax checks, focused JVM tests, SQL rollback regressions and mocked sender tests passed. Android compile, actual rendered UI and two-phone FCM delivery remain unverified. See START_HERE.txt for exact install and test steps.

## Previous work preserved
Separate owner/partner care data, explicit sharing, support notes, reminders, small dual-photo Locket, task/tag fixes and prior Today/Care UI patch. This ZIP does not replace Today/Care files or credentials/build settings.
