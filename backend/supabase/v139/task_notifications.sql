-- Pipo2doo 1.3.9. Run in Supabase SQL Editor BEFORE installing both updated apps.
-- No task, account, chat, care, photo or pairing records are deleted or backfilled.
begin;
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron;

-- Preserve every existing validation rule and add only the two schedule fields.
do $patch$
declare d text;
begin
 d:=pg_get_functiondef('private.pipo_validate_record()'::regprocedure);
 if position('''scheduledAt''' in d)=0 then
  if position('''parentId'',''completedAt'']' in d)=0 then raise exception 'Unexpected task whitelist: review backend version first'; end if;
  d:=replace(d,'''parentId'',''completedAt'']','''parentId'',''completedAt'',''scheduledAt'',''scheduleZone'']');
  execute d;
 end if;
end $patch$;
create or replace function private.validate_task_instant() returns trigger
language plpgsql security invoker set search_path='' as $$
declare at_ms bigint; z text; local_time timestamp;
begin
 if new.kind<>'task' then return new; end if;
 at_ms:=coalesce((new.payload->>'scheduledAt')::bigint,0); z:=coalesce(new.payload->>'scheduleZone','');
 if at_ms<0 or at_ms>32503680000000 then raise exception 'Invalid task instant'; end if;
 if at_ms>0 then
  if jsonb_typeof(new.payload->'scheduledAt')<>'number' or new.payload->>'minute' is null
    or not exists(select 1 from pg_catalog.pg_timezone_names where name=z) then raise exception 'Task instant needs a valid timezone and time'; end if;
  local_time:=to_timestamp(at_ms/1000.0) at time zone z;
  if (local_time::date-date '1970-01-01')<>(new.payload->>'day')::bigint or
    extract(hour from local_time)::int*60+extract(minute from local_time)::int<>(new.payload->>'minute')::int then
    raise exception 'Task time and UTC instant disagree'; end if;
 elsif z<>'' then raise exception 'A floating task cannot have a timezone'; end if;
 return new;
end $$;
revoke all on function private.validate_task_instant() from public,anon,authenticated;
drop trigger if exists task_instant_validation on public.shared_records;
create trigger task_instant_validation before insert or update on public.shared_records for each row execute function private.validate_task_instant();

create table if not exists private.task_reminder_settings(
 task_id uuid not null references public.shared_records(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 enabled boolean not null default true,
 offset_minutes integer not null default 0 check(offset_minutes between 0 and 43200),
 primary key(task_id,user_id)
);
create table if not exists private.task_push_queue(
 id uuid primary key default gen_random_uuid(),
 event_key text not null unique,
 task_id uuid not null references public.shared_records(id) on delete cascade,
 space_id uuid not null references public.partner_spaces(id) on delete cascade,
 recipient_id uuid not null references auth.users(id) on delete cascade,
 actor_id uuid references auth.users(id) on delete cascade,
 kind text not null check(kind in ('created','assigned','due','sync')),
 scheduled_at bigint not null default 0,
 offset_minutes integer not null default 0,
 due_at timestamptz not null default now(),
 status text not null default 'queued' check(status in ('queued','sending','sent','canceled','expired')),
 attempts integer not null default 0,
 lease_at timestamptz,
 created_at timestamptz not null default now()
);
alter table private.task_reminder_settings enable row level security;
alter table private.task_push_queue enable row level security;
revoke all on private.task_reminder_settings,private.task_push_queue from public,anon,authenticated;
create index if not exists task_push_pending on private.task_push_queue(due_at) where status in ('queued','sending');

-- Authenticated account can change only its own reminder choices, for current membership.
create or replace function public.sync_task_reminders(p_space uuid,p_items jsonb) returns void
language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); item jsonb; tid uuid;
begin
 if u is null or not public.is_space_member(p_space) then raise exception 'Access denied'; end if;
 if jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)>2000 then raise exception 'Invalid reminders'; end if;
 for item in select value from jsonb_array_elements(p_items) loop
  tid:=(item->>'id')::uuid;
  if not exists(select 1 from public.shared_records where id=tid and space_id=p_space and kind='task' and deleted_at is null) then raise exception 'Task unavailable'; end if;
  insert into private.task_reminder_settings(task_id,user_id,enabled,offset_minutes)
   values(tid,u,(item->>'enabled')::boolean,(item->>'offset')::integer)
   on conflict(task_id,user_id) do update set enabled=excluded.enabled,offset_minutes=excluded.offset_minutes;
 end loop;
end $$;
revoke all on function public.sync_task_reminders(uuid,jsonb) from public,anon;
grant execute on function public.sync_task_reminders(uuid,jsonb) to authenticated;

create or replace function public.read_task_reminders(p_space uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not public.is_space_member(p_space) then raise exception 'Access denied'; end if;
 return (select coalesce(jsonb_agg(jsonb_build_object('id',s.task_id,'enabled',s.enabled,'offset',s.offset_minutes)),'[]'::jsonb)
 from private.task_reminder_settings s join public.shared_records r on r.id=s.task_id
 where s.user_id=auth.uid() and r.space_id=p_space and r.deleted_at is null);
end $$;
revoke all on function public.read_task_reminders(uuid) from public,anon;
grant execute on function public.read_task_reminders(uuid) to authenticated;

-- Vault secrets are installed in a separate setup step. Without them task saves still succeed.
create or replace function private.wake_task_push() returns void
language plpgsql security definer set search_path='' as $$
declare secret text; endpoint text;
begin
 select decrypted_secret into secret from vault.decrypted_secrets where name='task_notification_secret' limit 1;
 select decrypted_secret into endpoint from vault.decrypted_secrets where name='task_notification_url' limit 1;
 if secret is null or endpoint is null then return; end if;
 perform net.http_post(url:=endpoint,headers:=jsonb_build_object('Content-Type','application/json','X-Task-Secret',secret),body:='{}'::jsonb,timeout_milliseconds:=10000);
end $$;
revoke all on function private.wake_task_push() from public,anon,authenticated;

create or replace function private.enqueue_task_push() returns trigger
language plpgsql security definer set search_path='' as $$
declare event_kind text; u uuid; at_ms bigint:=coalesce((new.payload->>'scheduledAt')::bigint,0);
begin
 if new.kind<>'task' then return new; end if;
 if new.deleted_at is not null or new.payload->>'status'='Done' then
  update private.task_push_queue set status='canceled' where task_id=new.id and kind<>'sync' and status in ('queued','sending');
 end if;
 if tg_op='INSERT' then event_kind:='created';
 elsif old.assigned_to is distinct from new.assigned_to then event_kind:='assigned';
 else event_kind:='sync'; end if;
 -- Invalidated schedules cannot produce a late alert after edit, even if already leased.
 update private.task_push_queue set status='canceled' where task_id=new.id and kind='due' and scheduled_at<>at_ms and status in ('queued','sending');
 if event_kind is not null then
  for u in select user_id from public.space_members where space_id=new.space_id loop
   insert into private.task_push_queue(event_key,task_id,space_id,recipient_id,actor_id,kind)
    values(new.id||':'||new.version||':'||event_kind||':'||u,new.id,new.space_id,u,auth.uid(),event_kind) on conflict(event_key) do nothing;
  end loop;
  perform private.wake_task_push();
 end if;
 return new;
end $$;
revoke all on function private.enqueue_task_push() from public,anon,authenticated;
drop trigger if exists task_push_changes on public.shared_records;
create trigger task_push_changes after insert or update on public.shared_records for each row execute function private.enqueue_task_push();

-- Service-only RPC. Inserts each due occurrence once and leases jobs for bounded retries.
create or replace function public.claim_task_push_batch() returns jsonb
language plpgsql security definer set search_path='' as $$
declare answer jsonb;
begin
 insert into private.task_push_queue as existing(event_key,task_id,space_id,recipient_id,actor_id,kind,scheduled_at,offset_minutes,due_at)
 select r.id||':'||(r.payload->>'scheduledAt')||':'||coalesce(s.offset_minutes,0)||':'||m.user_id,
 r.id,r.space_id,m.user_id,null,'due',(r.payload->>'scheduledAt')::bigint,coalesce(s.offset_minutes,0),
 to_timestamp((r.payload->>'scheduledAt')::bigint/1000.0)-make_interval(mins=>coalesce(s.offset_minutes,0))
 from public.shared_records r join public.partner_spaces p on p.id=r.space_id and p.closed_at is null
 join public.space_members m on m.space_id=r.space_id
 join public.poke_preferences pp on pp.user_id=m.user_id and pp.opt_in
 left join private.task_reminder_settings s on s.task_id=r.id and s.user_id=m.user_id
 where r.kind='task' and r.deleted_at is null and r.payload->>'status'<>'Done'
 and coalesce(s.enabled,true) and coalesce((r.payload->>'scheduledAt')::bigint,0)>0
 and to_timestamp((r.payload->>'scheduledAt')::bigint/1000.0)-make_interval(mins=>coalesce(s.offset_minutes,0)) between now()-interval '24 hours' and now()
 on conflict(event_key) do update set status='queued',attempts=0,created_at=now() where existing.status='canceled';

 update private.task_push_queue q set status='canceled' where q.status in ('queued','sending') and (
  not exists(select 1 from public.space_members where space_id=q.space_id and user_id=q.recipient_id)
  or not exists(select 1 from public.partner_spaces where id=q.space_id and closed_at is null)
  or not exists(select 1 from public.poke_preferences where user_id=q.recipient_id and opt_in)
  or not exists(select 1 from public.shared_records r where r.id=q.task_id and (r.deleted_at is null or q.kind='sync') and (q.kind<>'due' or (r.payload->>'status'<>'Done' and coalesce((r.payload->>'scheduledAt')::bigint,0)=q.scheduled_at)))
  or (q.kind='due' and exists(select 1 from private.task_reminder_settings s where s.task_id=q.task_id and s.user_id=q.recipient_id and (not s.enabled or s.offset_minutes<>q.offset_minutes)))
 );
 update private.task_push_queue set status='expired' where status in ('queued','sending') and (created_at<now()-interval '24 hours' or attempts>=5);
 with chosen as (
  select id from private.task_push_queue where due_at<=now() and (status='queued' or (status='sending' and lease_at<now()-interval '2 minutes'))
  and exists(select 1 from private.push_devices d where d.user_id=recipient_id and d.updated_at>now()-interval '60 days')
  order by due_at limit 30 for update skip locked
 ), leased as (
  update private.task_push_queue q set status='sending',lease_at=now(),attempts=attempts+1 from chosen c where q.id=c.id returning q.*
 ) select coalesce(jsonb_agg(jsonb_build_object('event_id',l.id,'recipient_id',l.recipient_id,'kind',l.kind,'tokens',
   (select jsonb_agg(token) from private.push_devices where user_id=l.recipient_id and updated_at>now()-interval '60 days'))),'[]'::jsonb) into answer from leased l;
 return answer;
end $$;
revoke all on function public.claim_task_push_batch() from public,anon,authenticated;
grant execute on function public.claim_task_push_batch() to service_role;

create or replace function public.finish_task_push(p_event uuid,p_accepted boolean) returns void
language sql security definer set search_path='' as $$
 update private.task_push_queue set status=case when p_accepted then 'sent' else 'queued' end where id=p_event and status='sending';
$$;
revoke all on function public.finish_task_push(uuid,boolean) from public,anon,authenticated;
grant execute on function public.finish_task_push(uuid,boolean) to service_role;

-- Notification contents require a fresh authorized read. Never expose device tokens.
create or replace function public.validate_task_notification(p_event uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare e private.task_push_queue; r public.shared_records; setting private.task_reminder_settings; u uuid:=auth.uid();
begin
 if u is null then raise exception 'Authentication required'; end if;
 select * into e from private.task_push_queue where id=p_event and recipient_id=u and status in ('sending','sent') and created_at>now()-interval '24 hours';
 if not found or not public.is_space_member(e.space_id) or not exists(select 1 from public.partner_spaces where id=e.space_id and closed_at is null) then raise exception 'Notification unavailable'; end if;
 select * into r from public.shared_records where id=e.task_id and space_id=e.space_id and (deleted_at is null or e.kind='sync');
 if not found then raise exception 'Task unavailable'; end if;
 if not exists(select 1 from public.poke_preferences where user_id=u and opt_in) then raise exception 'Notifications disabled'; end if;
 select * into setting from private.task_reminder_settings where task_id=r.id and user_id=u;
 if e.kind='due' and (r.payload->>'status'='Done' or coalesce((r.payload->>'scheduledAt')::bigint,0)<>e.scheduled_at
  or not coalesce(setting.enabled,true) or coalesce(setting.offset_minutes,0)<>e.offset_minutes) then raise exception 'Reminder changed'; end if;
 return jsonb_build_object('uid',u,'space',e.space_id,'actor',e.actor_id,'kind',e.kind,'taskId',r.id,'assignedTo',r.assigned_to,
   'payload',r.payload,'deleted',r.deleted_at is not null,'enabled',coalesce(setting.enabled,true),'offset',coalesce(setting.offset_minutes,0));
end $$;
revoke all on function public.validate_task_notification(uuid) from public,anon;
grant execute on function public.validate_task_notification(uuid) to authenticated;

-- One-minute fallback; on-device alarms supply precise delivery when permission is enabled.
select cron.schedule('pipo-task-notifications','* * * * *','select private.wake_task_push();');
commit;
