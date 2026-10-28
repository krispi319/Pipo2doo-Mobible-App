# Worker maintenance — v1.4.0

Already applied to the existing Pipo2doo Supabase project on 2026-10-28. Normal use of the replacement Android files does not require another SQL run or another Firebase project/key.

`configure_worker.sql` is an idempotent standalone dashboard setup file, not a CLI-generated migration. It adds a service-role-only boolean secret-check RPC and creates missing Vault entries without returning their values. It does not alter task data or relax user policies. Its URL is specific to the existing Pipo2doo project; change it deliberately if maintaining a different project.

The Edge Function source is in `../task-notifications/index.ts`. Deploy as task-notifications with Verify JWT off; it explicitly verifies X-Task-Secret before any processing. It uses the existing FCM_SERVICE_ACCOUNT_JSON and injected SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY server-side variables. A pre-existing TASK_NOTIFICATION_SECRET environment variable, when present, takes precedence and must match the Vault cron secret.

Health mode: an authenticated POST with X-Pipo-Health: 1 checks configuration without claiming/sending jobs. No live secret is supplied in this package.

Do not rerun the older v1.3.9 notification schema over v1.3.11 daily-occurrence SQL. Those prerequisites and generators were already present and were not replaced by this setup.
