# Firebase implementation handoff — not deployed or behavior-tested

## Verified foundation
Project `pipo2doo-bfc83`; Android ID remains `ai.mwm.android_2ebb28d1_fa04_4d16_89bf_06b0a18eff09`. Configuration materialized by cloud pipeline at `app/google-services.json`. BoM 34.15.0. Default Firestore in eur3, FCM APIs verified by Cloud Architect. No client access is permitted by the released rules.

## Prepared artifacts
- `functions/index.js`: gen2 **callable** export `pipoApi`, nodejs22, region europe-west1. Deploy it as a callable (`onCall`), not a plain REST handler.
- `functions/policies.js`: schema/health field whitelist and recipient quiet-hour policies.
- `backend/firestore.rules`: deny-all, intentional. All authorized access is through the callable; Admin SDK handlers implement access checks. Do not deploy globally authenticated/open rules.
- `backend/firestore.indexes.json`: no composite indexes needed; current queries use single-field default indexes.
- `functions/test/*.test.js`: policy and local emulator regression tests. **Written, not run.** No emulator/test runner is available here.
- Android adapters `PipoFirebase`, `FirebaseSetupViewModel`, `PartnerPokeService`, revalidated notification routing.

## Required before deployment
1. User opens Firebase Authentication and clicks Get started. Cloud Architect retries anonymous enablement and reads it back.
2. Explicit user authorization for Blaze billing. Do not link automatically. Firestore/Authentication do not require billing.
3. Cloud Architect creates a random high-entropy **server-only** secret `PAIRING_HMAC_KEY` and binds access for the function runtime. Never ship this secret to the Android app. Use the same value across function revisions or invalidate existing invitations/idempotency entries deliberately.
4. Review the handlers, dependencies and access behavior, then provision the callable via the supported Functions deployment path; check that secret bindings and callable protocol are supported by that tool. Resource provisioning alone does not run the test suite.
5. Run real local emulator tests and adversarial verification including concurrent join/cooldown, foreign-device access, opt-out/completion races and consent narrowing/deletion, then two-device integration tests.

## Client activation is deliberately blocked
`CloudRelease.ENABLED=false`; FCM auto-init is off. The only live cloud action is the user-triggered **Check Firebase setup** button (anonymous identity + readiness probe; no task or health content). Adapters provide authenticated create/join/snapshot/CRUD/preferences/device/Poke/consent/upload/delete/read methods, but are **not yet a replacement for the main local repository**. Pairing, shared repository state, automatic upload orchestration, partner read-only calendar UI and server-ID reminder reconciliation must be integrated and verified after deployment; do not flip the gate just because the API exists. Existing welcome, task/list mechanics, private health vault and local reminders remain local. No examples or health history are silently imported.

## Schema and guarantees
- `users/{uid}` private current-space pointer; devices under `users/{uid}/devices/{id}`. `spaces/{id}` immutable generation and two-member list. Invitations HMAC-hashed, one-use, 15-minute TTL. User/network hashed quotas survive rejected joins.
- `spaces/{id}/lists|tasks/{id}` optimistic integer version. Task ID is an occurrence; completion forbids its Poke. Recurrence orchestration is still pending native repository integration; create the next occurrence with a new server ID, never reuse the completed one.
- `preferences/{uid}` opt-in, quiet interval + IANA zone. Tokens server-private and encrypted at rest by Firestore; never returned to partners or logged. FCM data-only event IDs, no health or task text.
- `consents/{owner}` explicit recipient, space, generation, category whitelist, history start and random revision. Notes require separate `notesOptIn`. `health/{owner}/entries/{day}` minimal scoped projections. Partners never write owner records. Active paired membership and matching consent are rechecked in every transaction. No direct Firestore health SDK or durable partner cache.
- Consent revocation denies further access before cleanup; unpair rotates generation and revokes both parties. Old projections cannot be read with the new revision. Previously viewed information cannot be recalled. Server cleanup retention/purge job must be added before release to remove inaccessible old projections.
- `pokes/{HMAC(uid:nonce)}` transactional event, task version, membership generation and ten-minute expiry; 15-minute task cooldown, sender 10/hour, recipient 6/hour. Fresh server check before each send + fresh receiver check before display/tap. Provider acceptance is not delivery/read.

## Outstanding server hardening before release
No automatic outbox worker is deployed/prepared: this version attempts dispatch inline; retrying an accepted ambiguous action reuses the same nonce. It must not be represented as guaranteed retry/delivery. Add private leased outbox processing if background bounded retries are required. Implement single-owner device token rebinding/revocation and per-device send receipts; current registration enforces UID path ownership but does not globally deduplicate tokens. Consider App Check with Play Integrity after registering/configuring it; it is not currently enforced. Add server health retention cleanup and account/reinstall recovery decisions. These are known incomplete production requirements, not verified capabilities.

## Verification status
The remote Android build validates Android compilation only. It does not run Node tests, security behavior tests or push delivery. Complete QA requires two distinct accounts/devices, live pairing/sync conflict tests, scope/notes/revocation/unpair tests and physical-device permission/channel/quiet-hour/dedup/cold-start tests. Until then retain per-service Setup required/Private statuses.
