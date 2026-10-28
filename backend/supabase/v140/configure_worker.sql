-- v1.4.0: idempotent worker authentication/configuration. No task or account data is changed.
-- Run as postgres in Supabase SQL Editor. Requires the existing v1.3.11 notification schema.
begin;
create or replace function public.task_worker_authorized(p_secret text) returns boolean
language sql stable security definer set search_path = '' as $$
 select coalesce((select length(p_secret)>=32 and decrypted_secret=p_secret
 from vault.decrypted_secrets where name='task_notification_secret' limit 1),false)
 and coalesce(auth.jwt()->>'role'='service_role',false);
$$;
revoke all on function public.task_worker_authorized(text) from public,anon,authenticated;
grant execute on function public.task_worker_authorized(text) to service_role;
do $$
begin
 if not exists(select 1 from vault.secrets where name='task_notification_secret') then
  perform vault.create_secret(replace(gen_random_uuid()::text,'-','') || replace(gen_random_uuid()::text,'-',''),'task_notification_secret');
 end if;
 if not exists(select 1 from vault.secrets where name='task_notification_url') then
  perform vault.create_secret('https://iilrnezrnipufiggltcf.supabase.co/functions/v1/task-notifications','task_notification_url');
 end if;
end $$;
commit;
