-- Adds an invoker-only wrapper; existing record permissions/version checks remain authoritative.
create or replace function public.mutate_recurring_tasks(p_space uuid,p_task uuid,p_expected_ids jsonb,p_changes jsonb) returns jsonb
language plpgsql security invoker set search_path='' as $$
declare actual uuid[]; expected uuid[]; item jsonb;
begin
 if auth.uid() is null or not public.is_space_member(p_space) then raise exception 'Access denied';end if;
 perform pg_advisory_xact_lock(hashtextextended('membership',0));
 if not exists(select 1 from public.shared_records where id=p_task and space_id=p_space and kind='task' and deleted_at is null) then
  raise exception 'This task is no longer available. Refresh and try again.';
 end if;
 if jsonb_typeof(p_expected_ids) is distinct from 'array' or jsonb_typeof(p_changes) is distinct from 'array' then raise exception 'Expected series IDs and task changes';end if;
 with recursive edges(a,b) as (
  select id,nullif(payload->>'seriesId','')::uuid from public.shared_records where space_id=p_space and kind='task' and deleted_at is null
  union
  select r.id,r.parent_id from public.shared_records r join public.shared_records parent on parent.id=r.parent_id and parent.space_id=r.space_id and parent.kind='task' and parent.deleted_at is null
   where r.space_id=p_space and r.kind='task' and r.deleted_at is null
 ), family(id) as (
  select p_task
  union
  select case when e.a=f.id then e.b else e.a end from edges e join family f on e.a=f.id or e.b=f.id where e.b is not null
 ) select array_agg(distinct r.id order by r.id) into actual from family f join public.shared_records r on r.id=f.id and r.space_id=p_space and r.kind='task' and r.deleted_at is null;
 select array_agg(distinct value::uuid order by value::uuid) into expected from jsonb_array_elements_text(p_expected_ids);
 if actual is distinct from expected then raise exception 'The recurring series changed. Refresh and review the affected tasks before retrying.' using errcode='40001';end if;
 for item in select value from jsonb_array_elements(p_changes) loop
  if coalesce(item->>'kind','task')<>'task' then raise exception 'Only linked tasks may change in a series operation';end if;
  if not ((item->>'id')::uuid=any(actual)) and not (
   coalesce((item->>'version')::bigint,-1)=0 and exists(
    select 1 from jsonb_array_elements(p_changes) linked where (linked->>'id')::uuid=any(actual)
     and (linked->'payload'->>'seriesId'=item->>'id' or linked->>'parent'=item->>'id')
   )
  ) then raise exception 'Unrelated tasks cannot be changed by a series operation';end if;
 end loop;
 return public.mutate_shared_tasks(p_space,p_changes);
end $$;
revoke all on function public.mutate_recurring_tasks(uuid,uuid,jsonb,jsonb) from public,anon;
grant execute on function public.mutate_recurring_tasks(uuid,uuid,jsonb,jsonb) to authenticated;
