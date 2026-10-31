# Chat badge and presence — 2026-10-31

## Changes

The home badge previously read a non-observable local set and only reread it on
navigation/shared-note changes. It could also retain a large unread history because
only on-screen messages were acknowledged. It now observes preference changes and
acknowledges existing incoming message IDs when the foreground chat opens, as the
user requested. Newly arriving messages count again. IDs remain scoped by account
and couple. Sending another message or waiting for the next server poll is unnecessary.

This conversation acknowledgement is separate from server Seen receipts: those
still require actual foreground-visible messages, with overlays closed.

Us > Chat visibility > Show online status controls account-wide opt-in visibility.
Default is Invisible. Chat shows partner Online/Offline and the user's own status.
Invisible partners appear Offline; the UI does not reveal their hidden activity.
Messages and Seen receipts continue normally when Invisible.

Active/connected sessions heartbeat every 20 seconds. Background/sign-out attempts
remove their own session. Multiple devices are supported; another active device can
keep the account Online. Unexpected exits expire after 70 seconds. Partner display
may lag until its next presence poll. No last-seen time is shown.

Client-side account/space guards and a server expected-user parameter prevent a
delayed visibility request from applying to a different account after switching.
Presence errors have an independent retry and do not stop shared-task synchronization.

## Verification

- 29 selected tests passed across chat read counting/observation, scope isolation,
  presence display, chat presentation/composer, chat/settings UI, existing avatar and
  main-screen/quick-entry/repeat-editor regressions. Android debug build exited 0.
- A 150-message badge is acknowledged to zero; a subsequent message counts as one.
- UI observation test confirms the badge changes without another network refresh.
- Chat/header and settings previews rendered and visually inspected using sample data.
- Deployed v143_opt_in_chat_presence. Read-only query confirmed both tables have RLS,
  SELECT/INSERT/UPDATE (and session DELETE) policies, an invoker RPC, authenticated
  execution access and no anonymous RPC/table access.
- Security-advisor categories/counts unchanged: 7 pre-existing private RLS/no-policy
  findings, 17 pre-existing authenticated definer functions and leaked-password
  protection disabled. These settings were not changed.
- Full backend behavior script subsequently passed through Supabase execute_sql;
  synthetic accounts and rows rolled back. It verified invisible default, identity
  guards, multiple devices, owner-only writes, invisible privacy, heartbeat expiry,
  server timestamps, background handling, outsider access and unpair isolation.

## Before release

1. Backend rollback check completed: verification/chat_presence_test.sql.
2. Install a new test APK over the existing app on two separate accounts. Open a chat
   with a large badge; close it and confirm zero. Send one new message; confirm one.
3. Turn Show online status on for one phone. Confirm the other sees Online. Background
   the first phone; confirm Offline after the next poll. Force-stop it and allow up to
   70 seconds plus polling for the timeout.
4. Set Invisible. Confirm the other phone sees Offline while chat/send/read receipts
   still work. Try two devices on the same account: a heartbeat must not undo Invisible.
5. Disconnect the network and switch accounts/couples. Status must not claim a fresh
   Online connection or retain a previous partner's presence.

Included in v1.4.3 / 153. Earlier avatar/time button changes are retained.
Screenshots are not a live two-phone verification. See v143.md for release status.
