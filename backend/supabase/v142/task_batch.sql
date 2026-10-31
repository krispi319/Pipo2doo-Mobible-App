-- Atomic task edits/deletions; delegates every write to the existing permission/version RPC.
create or replace function private.daily_task_changed() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if current_setting('pipo.defer_daily_expansion',true)='on' then return new; end if;
 if new.kind='task' and new.deleted_at is null and new.payload->>'repeat'='Daily' and coalesce(new.payload->>'seriesId','')='' then
  perform private.expand_daily_series(new.id);
 end if;
 return new;
end $$;
revoke all on function private.daily_task_changed() from public,anon,authenticated;

create or replace function public.mutate_shared_tasks(p_space uuid,p_changes jsonb) returns jsonb
language plpgsql security invoker set search_path='' as $$
declare item jsonb; saved jsonb; roots jsonb:='[]'; result jsonb:='[]'; previous_setting text;
begin
 if auth.uid() is null or not public.is_space_member(p_space) then raise exception 'Access denied'; end if;
 if jsonb_typeof(p_changes) is distinct from 'array' then raise exception 'Expected task changes'; end if;
 if jsonb_array_length(p_changes) not between 1 and 500 then raise exception 'Save at most 500 task changes together'; end if;
 if (select count(distinct value->>'id') from jsonb_array_elements(p_changes))<>jsonb_array_length(p_changes) then
  raise exception 'Duplicate task changes';
 end if;
 perform pg_advisory_xact_lock(hashtextextended('membership',0));
 previous_setting:=coalesce(current_setting('pipo.defer_daily_expansion',true),'');
 perform set_config('pipo.defer_daily_expansion','on',true);
 -- Establish the new template before reparenting its existing occurrences.
 -- Deletions remain last, with the client's child-before-parent order preserved.
 for item in select value from jsonb_array_elements(p_changes) with ordinality changes(value,position)
  order by case when coalesce((value->>'deleted')::boolean,false) then 2
   when value->'payload'->>'repeat'='Daily' and coalesce(value->'payload'->>'seriesId','')='' then 0 else 1 end,position loop
  if coalesce(item->>'kind','task') not in ('task','list') or (item->>'kind'='list' and not coalesce((item->>'deleted')::boolean,false)) then
   raise exception 'Expected task edits or list deletions';
  end if;
  saved:=public.mutate_shared_record((item->>'id')::uuid,p_space,coalesce(item->>'kind','task'),nullif(item->>'parent','')::uuid,
   nullif(item->>'assigned','')::uuid,item->'payload',(item->>'version')::bigint,(item->>'mutation')::uuid,
   coalesce((item->>'deleted')::boolean,false));
  result:=result||jsonb_build_array(saved);
  if saved->>'kind'='task' and saved->>'deleted_at' is null and saved->'payload'->>'repeat'='Daily' and coalesce(saved->'payload'->>'seriesId','')='' then
   roots:=roots||jsonb_build_array(saved);
  end if;
 end loop;
 perform set_config('pipo.defer_daily_expansion',previous_setting,true);
 -- Touch templates only after the whole series exists. The existing trigger expands them.
 for saved in select value from jsonb_array_elements(roots) loop
  item:=public.mutate_shared_record((saved->>'id')::uuid,p_space,'task',(saved->>'parent_id')::uuid,
   (saved->>'assigned_to')::uuid,saved->'payload',(saved->>'version')::bigint,gen_random_uuid(),false);
  select jsonb_agg(case when value->>'id'=item->>'id' then item else value end) into result from jsonb_array_elements(result);
 end loop;
 return result;
end $$;
revoke all on function public.mutate_shared_tasks(uuid,jsonb) from public,anon;
grant execute on function public.mutate_shared_tasks(uuid,jsonb) to authenticated;
