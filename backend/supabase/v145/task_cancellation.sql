CREATE OR REPLACE FUNCTION private.pipo_validate_record()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare permitted text[];
begin
 if new.kind in ('task','list','tag','profile','appreciation') then
  permitted:=case new.kind
   when 'task' then array['title','listId','day','minute','repeat','status','tags','parentId','completedAt','scheduledAt','scheduleZone','seriesId','cancelledAt','cancelledBy','cancelledByName','cancelReason']
   when 'list' then array['name','emoji','pinned','pictures']
   when 'tag' then array['name']
   when 'profile' then array['name','avatar','photo','timezone']
   else array['kind','message','taskTitle','picture','at','replyTo'] end;
  if exists(select 1 from jsonb_object_keys(new.payload) k where not k=any(permitted)) then raise exception 'Unsupported shared field. Private notes and reminders stay on your phone.'; end if;
 end if;
 if new.kind in ('list','tag','profile') then
  if jsonb_typeof(new.payload->'name') is distinct from 'string' or coalesce(length(btrim(new.payload->>'name')),0) not between 1 and 80 then raise exception 'Name is required (maximum 80 characters)'; end if;
 end if;
 if new.kind='task' then
  if jsonb_typeof(new.payload->'title') is distinct from 'string' or coalesce(length(btrim(new.payload->>'title')),0) not between 1 and 80 then raise exception 'Task title is required (maximum 80 characters)'; end if;
  if coalesce(new.payload->>'repeat','') not in ('Once','Daily','Weekly') or coalesce(new.payload->>'status','') not in ('Todo','Doing','Done')
    or coalesce((new.payload->>'day')::bigint,-9999999) not between -365000 and 365000
    or (new.payload->>'minute' is not null and (new.payload->>'minute')::integer not between 0 and 1439)
    or jsonb_typeof(new.payload->'tags') is distinct from 'array' then raise exception 'Invalid task schedule or tags'; end if;
  if jsonb_array_length(new.payload->'tags')>50 then raise exception 'Maximum 50 tags per task'; end if;
 end if;

 if new.kind='profile' and new.payload ? 'timezone' then
  if jsonb_typeof(new.payload->'timezone') is distinct from 'string' or not exists(select 1 from pg_catalog.pg_timezone_names where name=new.payload->>'timezone') then raise exception 'Invalid clock timezone';end if;
 end if;
 if new.kind='appreciation' and new.deleted_at is null and coalesce(new.payload->>'replyTo','')<>'' then
  if jsonb_typeof(new.payload->'replyTo') is distinct from 'string' or not exists(select 1 from public.shared_records target where target.id=(new.payload->>'replyTo')::uuid and target.space_id=new.space_id and target.kind='appreciation' and target.deleted_at is null and target.payload->>'kind'<>'Locket') then raise exception 'Reply target is not available in this chat';end if;
 end if;
 if new.deleted_at is not null and exists(select 1 from public.shared_records where parent_id=new.id and deleted_at is null) then raise exception 'Move or remove child records before deleting their parent'; end if;
 return new;
end $function$;


-- Done remains the wire status so older apps safely stop reminders. New clients use
-- cancellation metadata to distinguish retained history from genuine completions.
create or replace function private.guard_task_cancellation() returns trigger
language plpgsql security invoker set search_path='' as $$
declare at_ms bigint:=coalesce((new.payload->>'cancelledAt')::bigint,0);
begin
 if new.kind<>'task' then return new;end if;
 if tg_op='UPDATE' and coalesce((old.payload->>'cancelledAt')::bigint,0)>0 then
  if (new.payload->'cancelledAt',new.payload->'cancelledBy',new.payload->'cancelledByName',new.payload->'cancelReason')
      is distinct from (old.payload->'cancelledAt',old.payload->'cancelledBy',old.payload->'cancelledByName',old.payload->'cancelReason')
      or new.payload->>'status'<>'Done' or new.payload->>'repeat'<>'Once'
      or coalesce((new.payload->>'completedAt')::bigint,0)<>0 then
   raise exception 'Cancelled tasks stay in History. Update the app and refresh.';
  end if;
 elsif at_ms>0 then
  if tg_op<>'UPDATE' or auth.uid() is null or new.payload->>'cancelledBy' is distinct from auth.uid()::text
     or new.payload->>'status'<>'Done' or new.payload->>'repeat'<>'Once'
     or coalesce((new.payload->>'completedAt')::bigint,0)<>0
     or (tg_op='UPDATE' and old.payload->>'status'='Done')
     or jsonb_typeof(new.payload->'cancelReason') is distinct from 'string'
     or length(new.payload->>'cancelReason')>200
     or jsonb_typeof(new.payload->'cancelledByName') is distinct from 'string'
     or length(new.payload->>'cancelledByName') not between 1 and 80 then
   raise exception 'Invalid task cancellation';
  end if;
  new.payload:=jsonb_set(new.payload,'{cancelledAt}',to_jsonb((extract(epoch from clock_timestamp())*1000)::bigint));
 end if;
 return new;
end $$;
revoke all on function private.guard_task_cancellation() from public,anon,authenticated;
drop trigger if exists cancellation_validation on public.shared_records;
create trigger cancellation_validation before insert or update on public.shared_records
for each row execute function private.guard_task_cancellation();
