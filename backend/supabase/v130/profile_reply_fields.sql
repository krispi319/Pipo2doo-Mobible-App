begin;
do $patch$
declare definition text;
begin
 select pg_get_functiondef('public.mutate_shared_record(uuid,uuid,text,uuid,uuid,jsonb,bigint,uuid,boolean)'::regprocedure) into definition;
 if position('(''name'',''avatar'',''photo'')' in definition)=0 then raise exception 'Profile whitelist changed; inspect before patching';end if;
 definition:=replace(definition,'(''name'',''avatar'',''photo'')','(''name'',''avatar'',''photo'',''timezone'')');
 execute definition;
 select pg_get_functiondef('private.pipo_validate_record()'::regprocedure) into definition;
 definition:=replace(definition,'array[''name'',''avatar'',''photo'']','array[''name'',''avatar'',''photo'',''timezone'']');
 definition:=replace(definition,'array[''kind'',''message'',''taskTitle'',''picture'',''at'']','array[''kind'',''message'',''taskTitle'',''picture'',''at'',''replyTo'']');
 definition:=replace(definition,' if new.deleted_at is not null', $validation$
 if new.kind='profile' and new.payload ? 'timezone' then
  if jsonb_typeof(new.payload->'timezone') is distinct from 'string' or not exists(select 1 from pg_catalog.pg_timezone_names where name=new.payload->>'timezone') then raise exception 'Invalid clock timezone';end if;
 end if;
 if new.kind='appreciation' and new.deleted_at is null and coalesce(new.payload->>'replyTo','')<>'' then
  if jsonb_typeof(new.payload->'replyTo') is distinct from 'string' or not exists(select 1 from public.shared_records target where target.id=(new.payload->>'replyTo')::uuid and target.space_id=new.space_id and target.kind='appreciation' and target.deleted_at is null and target.payload->>'kind'<>'Locket') then raise exception 'Reply target is not available in this chat';end if;
 end if;
 if new.deleted_at is not null$validation$);
 execute definition;
end $patch$;
commit;
