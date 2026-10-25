-- Read-only setup check. Does not change tasks.
select to_regprocedure('private.expand_daily_tasks()') is not null as daily_worker_installed,
 exists(select 1 from pg_trigger where tgrelid='public.shared_records'::regclass and tgname='daily_task_expansion' and not tgisinternal) as daily_trigger_installed;
select jobname,schedule,active from cron.job where jobname in ('pipo-daily-occurrences','pipo-task-notifications');
select j.jobname,d.status,d.return_message,d.start_time from cron.job_run_details d join cron.job j on j.jobid=d.jobid
 where j.jobname='pipo-daily-occurrences' order by d.start_time desc limit 5;
