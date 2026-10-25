-- Requires v1.3.9 task_notifications.sql first. Install before both updated Android apps.
-- No old task is removed or marked complete. Old missed dates are not invented retroactively.
begin;
do $patch$
declare d text;
begin
 d:=pg_get_functiondef('private.pipo_validate_record()'::regprocedure);
 if position('''seriesId''' in d)=0 then
  if position('''scheduledAt'',''scheduleZone'']' in d)=0 then raise exception 'Install v1.3.9 task_notifications.sql first'; end if;
  execute replace(d,'''scheduledAt'',''scheduleZone'']','''scheduledAt'',''scheduleZone'',''seriesId'']');
 end if;
end $patch$;

-- Java UUID.nameUUIDFromBytes-compatible IDs; stable across clients, retries and cron runs.
create or replace function private.daily_occurrence_id(p_series uuid,p_day bigint) returns uuid
language sql immutable security invoker set search_path='' as $$
 with h as (select md5('pipo-daily:'||p_series::text||':'||p_day::text) s)
 select (substr(s,1,8)||'-'||substr(s,9,4)||'-3'||substr(s,14,3)||'-'||
 substr('89ab',((('x'||substr(s,17,1))::bit(4)::int)&3)+1,1)||substr(s,18,3)||'-'||substr(s,21,12))::uuid from h
$$;
revoke all on function private.daily_occurrence_id(uuid,bigint) from public,anon,authenticated;

-- Upgrade existing completion chains to independent siblings. Preserve every ID/status/time.
with recursive chain as (
 select r.id,r.id root,r.space_id,0 depth from public.shared_records r
 where r.kind='task' and r.payload->>'repeat'='Daily' and coalesce(r.payload->>'seriesId','')=''
 and not exists(select 1 from public.shared_records p where p.id=r.parent_id and p.kind='task' and p.payload->>'repeat'='Daily')
 union all
 select c.id,p.root,c.space_id,p.depth+1 from public.shared_records c join chain p on c.parent_id=p.id and c.space_id=p.space_id
 where c.kind='task' and c.payload->>'repeat'='Daily' and p.depth<64 and coalesce(c.payload->>'seriesId','')=''
)
update public.shared_records r set payload=r.payload||jsonb_build_object('seriesId',c.root::text,'parentId',c.root::text),
 parent_id=c.root,version=r.version+1,updated_at=now()
from chain c where r.id=c.id and c.id<>c.root;

-- New field does not grant editing rights: the existing RPC and ancestry checks still apply.
create or replace function private.validate_daily_series() returns trigger
language plpgsql security invoker set search_path='' as $$
declare s text:=coalesce(new.payload->>'seriesId','');
begin
 if new.kind<>'task' or s='' then return new; end if;
 if jsonb_typeof(new.payload->'seriesId')<>'string' or not exists(
  select 1 from public.shared_records r where r.id=s::uuid and r.id<>new.id and r.space_id=new.space_id and r.kind='task'
 ) then raise exception 'Invalid daily series'; end if;
 return new;
end $$;
revoke all on function private.validate_daily_series() from public,anon,authenticated;
drop trigger if exists daily_series_validation on public.shared_records;
create trigger daily_series_validation before insert or update on public.shared_records for each row execute function private.validate_daily_series();

-- Private worker only: deterministic insert, no updates to existing or deleted occurrences.
create or replace function private.expand_daily_series(p_root uuid,p_now timestamptz default now()) returns void
language plpgsql security definer set search_path='' as $$
declare r public.shared_records;z text;anchor timestamp;first_day bigint;today bigint;d bigint;at_ms bigint;v jsonb;
begin
 select * into r from public.shared_records where id=p_root for update;
 if not found or r.kind<>'task' or r.deleted_at is not null or r.payload->>'repeat'<>'Daily'
  or coalesce(r.payload->>'seriesId','')<>'' or not exists(select 1 from public.partner_spaces where id=r.space_id and closed_at is null)
  or not exists(select 1 from public.space_members where space_id=r.space_id and user_id=r.created_by)
  or (r.assigned_to is not null and not exists(select 1 from public.space_members where space_id=r.space_id and user_id=r.assigned_to)) then return; end if;
 -- Do not recreate tasks whose list/ancestor was removed.
 if r.parent_id is not null and not exists(select 1 from public.shared_records where id=r.parent_id and space_id=r.space_id and deleted_at is null) then return; end if;
 z:=coalesce(nullif(r.payload->>'scheduleZone',''),'UTC');
 first_day:=(r.payload->>'day')::bigint;
 if coalesce((r.payload->>'scheduledAt')::bigint,0)>0 and r.payload->>'minute' is not null then
  anchor:=to_timestamp((r.payload->>'scheduledAt')::bigint/1000.0) at time zone z;
  first_day:=anchor::date-date '1970-01-01';
 end if;
 today:=(p_now at time zone z)::date-date '1970-01-01';
 for d in greatest(first_day+1,today)..today+21 loop
  if exists(select 1 from public.shared_records c where c.space_id=r.space_id and c.kind='task'
    and c.payload->>'seriesId'=r.id::text and (c.payload->>'day')::bigint=d) then continue; end if;
  at_ms:=0;
  if anchor is not null then
   -- Match java.time: earlier instant during an overlap, forward through a gap.
   select (extract(epoch from coalesce(min(candidate),((date '1970-01-01'+d::int)+anchor::time) at time zone z))*1000)::bigint into at_ms
   from (select (((date '1970-01-01'+d::int)+anchor::time) at time zone z)+make_interval(mins=>m) candidate
     from unnest(array[-120,-60,-30,0,30,60,120]) m) candidates
   where candidate at time zone z=(date '1970-01-01'+d::int)+anchor::time;
  end if;
  v:=r.payload||jsonb_build_object('day',d,'status','Todo','completedAt',0,'parentId',r.id::text,'seriesId',r.id::text,
    'scheduledAt',at_ms,'scheduleZone',case when at_ms>0 then z else '' end,
    'minute',case when at_ms>0 then to_jsonb(extract(hour from to_timestamp(at_ms/1000.0) at time zone z)::int*60+extract(minute from to_timestamp(at_ms/1000.0) at time zone z)::int) else r.payload->'minute' end);
  insert into public.shared_records(id,space_id,kind,parent_id,created_by,assigned_to,payload,last_mutation_id)
   values(private.daily_occurrence_id(r.id,d),r.space_id,'task',r.id,r.created_by,r.assigned_to,v,gen_random_uuid())
   on conflict(id) do nothing;
  insert into private.task_reminder_settings(task_id,user_id,enabled,offset_minutes)
   select c.id,s.user_id,s.enabled,s.offset_minutes from private.task_reminder_settings s
    join public.shared_records c on c.id=private.daily_occurrence_id(r.id,d) and c.deleted_at is null
    where s.task_id=r.id on conflict(task_id,user_id) do nothing;
 end loop;
end $$;
revoke all on function private.expand_daily_series(uuid,timestamptz) from public,anon,authenticated;

create or replace function private.expand_daily_tasks() returns void
language plpgsql security definer set search_path='' as $$
declare r record;
begin
 -- Same lock order as the existing membership/record RPC; prevents deletion races.
 perform pg_advisory_xact_lock(hashtextextended('membership',0));
 for r in select id from public.shared_records where kind='task' and deleted_at is null
  and payload->>'repeat'='Daily' and coalesce(payload->>'seriesId','')='' order by id loop
  perform private.expand_daily_series(r.id);
 end loop;
end $$;
revoke all on function private.expand_daily_tasks() from public,anon,authenticated;

create or replace function private.daily_task_changed() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.kind='task' and new.deleted_at is null and new.payload->>'repeat'='Daily' and coalesce(new.payload->>'seriesId','')='' then
  perform private.expand_daily_series(new.id);
 end if;
 return new;
end $$;
revoke all on function private.daily_task_changed() from public,anon,authenticated;
drop trigger if exists daily_task_expansion on public.shared_records;
create trigger daily_task_expansion after insert or update on public.shared_records for each row execute function private.daily_task_changed();

-- Suppress 21 "new task" pushes when precreating dates; due reminders use the existing worker.
-- Preserve all other cancellation, reassignment and sync behavior from v1.3.9.
do $patch$
declare d text;
begin
 d:=pg_get_functiondef('private.enqueue_task_push()'::regprocedure);
 if position('daily generated occurrence' in d)=0 then
  if position('if tg_op=''INSERT'' then event_kind:=''created'';' in d)=0 then raise exception 'Unexpected push function; review before updating'; end if;
  d:=replace(d,'if tg_op=''INSERT'' then event_kind:=''created'';',
   'if tg_op=''INSERT'' and coalesce(new.payload->>''seriesId'','''')<>'''' then return new; end if; -- daily generated occurrence
 if tg_op=''INSERT'' then event_kind:=''created'';');
  execute d;
 end if;
end $patch$;
select private.expand_daily_tasks();
select cron.schedule('pipo-daily-occurrences','17 * * * *','select private.expand_daily_tasks()');
commit;
