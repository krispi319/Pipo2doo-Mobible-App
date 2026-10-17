-- Adds validation to the existing Pipo2doo backend without replacing pairing/RPCs.
begin;
create or replace function private.pipo_validate_record() returns trigger
language plpgsql security invoker set search_path='' as $$
declare permitted text[];
begin
 if new.kind in ('task','list','tag','profile','appreciation') then
  permitted:=case new.kind
   when 'task' then array['title','listId','day','minute','repeat','status','tags','parentId','completedAt']
   when 'list' then array['name','emoji','pinned','pictures']
   when 'tag' then array['name']
   when 'profile' then array['name','avatar','photo']
   else array['kind','message','taskTitle','picture','at'] end;
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
 if new.deleted_at is not null and exists(select 1 from public.shared_records where parent_id=new.id and deleted_at is null) then raise exception 'Move or remove child records before deleting their parent'; end if;
 return new;
end $$;
revoke all on function private.pipo_validate_record() from public,anon,authenticated;
drop trigger if exists pipo_validate_record on public.shared_records;
create trigger pipo_validate_record before insert or update on public.shared_records for each row execute function private.pipo_validate_record();
create unique index if not exists pipo_unique_tag_name on public.shared_records(space_id,lower(btrim(payload->>'name'))) where kind='tag' and deleted_at is null;
commit;
