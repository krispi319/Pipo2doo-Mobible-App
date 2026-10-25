-- Read-only checks. No tokens, keys, task titles, or personal information are returned.
select jobname,active,schedule from cron.job where jobname='pipo-task-notifications';
select name from vault.secrets where name in ('task_notification_secret','task_notification_url');
select status,count(*) from private.task_push_queue group by status;
select has_function_privilege('authenticated','public.claim_task_push_batch()','execute') as client_can_read_tokens,
       has_function_privilege('anon','public.validate_task_notification(uuid)','execute') as anonymous_can_read_events;
-- Expected: client_can_read_tokens=false; anonymous_can_read_events=false.
