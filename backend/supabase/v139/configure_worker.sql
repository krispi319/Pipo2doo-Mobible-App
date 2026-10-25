-- One-time worker configuration. Replace ONLY the placeholder below with the same
-- 64-character random secret saved as TASK_NOTIFICATION_SECRET in Edge Function Secrets.
-- Never put this secret in GitHub, an APK, or a chat message.
begin;
do $$
declare secret text := 'PASTE_YOUR_RANDOM_64_CHARACTER_SECRET_HERE';
begin
 if length(secret)<64 or secret like 'PASTE_%' then raise exception 'Paste your private worker secret before running'; end if;
 if exists(select 1 from vault.secrets where name in ('task_notification_secret','task_notification_url')) then
  raise exception 'Worker configuration already exists. Do not create duplicate secrets; keep the existing secret or update it in Vault.';
 end if;
 perform vault.create_secret(secret,'task_notification_secret');
 perform vault.create_secret('https://iilrnezrnipufiggltcf.supabase.co/functions/v1/task-notifications','task_notification_url');
end $$;
commit;
