-- Synthetic fixture only. No notifications or test data survive rollback.
begin;
create temporary table series_test_context(uid uuid,space uuid,task uuid,ids jsonb,changes jsonb);
grant select on series_test_context to authenticated;
do $test$
declare a uuid:=gen_random_uuid();b uuid:=gen_random_uuid();outsider uuid:=gen_random_uuid();s uuid:=gen_random_uuid();
 lid uuid:=gen_random_uuid();t uuid:=gen_random_uuid();other uuid:=gen_random_uuid();new_template uuid:=gen_random_uuid();anchor uuid;history2 uuid;
 ids jsonb;changes jsonb;p jsonb;r record;before_count int;failed boolean;
begin
 insert into auth.users(id) values(a),(b),(outsider);
 insert into public.partner_spaces(id,created_by) values(s,a);
 insert into public.space_members(space_id,user_id,member_role) values(s,a,'user_1'),(s,b,'user_2');
 perform set_config('request.jwt.claim.sub',a::text,true);
 perform public.mutate_shared_record(lid,s,'list',null,null,'{"name":"Series regression","emoji":"","pinned":false,"pictures":[]}',0,gen_random_uuid(),false);
 p:=jsonb_build_object('title','Fix pipo2doo','listId',lid,'day',(now() at time zone 'UTC')::date-date '1970-01-01',
 'minute',null,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'scheduledAt',0,'scheduleZone','');
 perform public.mutate_shared_record(t,s,'task',lid,a,p,0,gen_random_uuid(),false);
 perform public.mutate_shared_record(other,s,'task',lid,a,p||'{"repeat":"Once"}',0,gen_random_uuid(),false);
 select jsonb_agg(id::text),count(*) into ids,before_count from public.shared_records where space_id=s and kind='task' and (id=t or payload->>'seriesId'=t::text) and deleted_at is null;
 if before_count<20 then raise exception 'Daily fixture not expanded';end if;
 select jsonb_agg(jsonb_build_object('id',id,'payload',payload||'{"title":"Improve Pipo2doo"}','parent',parent_id,'assigned',assigned_to,'version',version,'mutation',gen_random_uuid(),'deleted',false)) into changes
  from public.shared_records where space_id=s and kind='task' and (id=t or payload->>'seriesId'=t::text) and deleted_at is null;
 -- Paired user 2 can edit user 1's full series.
 perform set_config('request.jwt.claim.sub',b::text,true);
 perform public.mutate_recurring_tasks(s,t,ids,changes);
 if exists(select 1 from public.shared_records where space_id=s and (id=t or payload->>'seriesId'=t::text) and payload->>'title'<>'Improve Pipo2doo') then raise exception 'Series rename incomplete';end if;
 if (select payload->>'title' from public.shared_records where id=other)<>'Fix pipo2doo' then raise exception 'Unrelated same-title task changed';end if;
 -- Unrelated records cannot be smuggled into the wrapper.
 select jsonb_build_array(jsonb_build_object('id',id,'payload',payload||'{"title":"Wrong"}','parent',parent_id,'assigned',assigned_to,'version',version,'mutation',gen_random_uuid(),'deleted',false)) into changes from public.shared_records where id=other;
 failed:=false;
 begin perform public.mutate_recurring_tasks(s,t,ids,changes);exception when raise_exception then failed:=true;end;
 if not failed then raise exception 'Unrelated edit accepted';end if;
 -- A valid newly promoted template may be introduced with a linked existing row.
 begin
  select * into r from public.shared_records where id=t;
  changes:=jsonb_build_array(
   jsonb_build_object('id',new_template,'payload',r.payload||jsonb_build_object('day',(r.payload->>'day')::bigint+30,'seriesId','','parentId',''),
    'parent',lid,'assigned',a,'version',0,'mutation',gen_random_uuid(),'deleted',false),
   jsonb_build_object('id',r.id,'payload',r.payload||jsonb_build_object('repeat','Once','seriesId',new_template::text,'parentId',new_template::text),
    'parent',new_template,'assigned',a,'version',r.version,'mutation',gen_random_uuid(),'deleted',false));
  perform public.mutate_recurring_tasks(s,t,ids,changes);
  if not exists(select 1 from public.shared_records where id=new_template and deleted_at is null) then raise exception 'New template rejected';end if;
  raise exception 'Rollback template subcase' using errcode='ZX001';
 exception when sqlstate 'ZX001' then null;
 end;
 -- A stale second row must roll back the first row too.
 select jsonb_agg(jsonb_build_object('id',id,'payload',payload||'{"title":"Must roll back"}','parent',parent_id,'assigned',assigned_to,'version',case when id=t then version else version-1 end,'mutation',gen_random_uuid(),'deleted',false)) into changes
 from (select * from public.shared_records where id=t or id=(select id from public.shared_records where payload->>'seriesId'=t::text limit 1)) rows;
 failed:=false;
 begin perform public.mutate_recurring_tasks(s,t,ids,changes);exception when others then failed:=true;end;
 if not failed or exists(select 1 from public.shared_records where space_id=s and payload->>'title'='Must roll back') then raise exception 'Partial series save';end if;
 -- A newly materialized occurrence invalidates the old reviewed set.
 perform private.expand_daily_series(t,now()+interval '1 day');
 failed:=false;
 begin perform public.mutate_recurring_tasks(s,t,ids,changes);exception when serialization_failure then failed:=true;end;
 if not failed then raise exception 'Stale occurrence set accepted';end if;
 select jsonb_agg(id::text) into ids from public.shared_records where space_id=s and kind='task' and (id=t or payload->>'seriesId'=t::text) and deleted_at is null;
 -- Keep two completed occurrences as history, deleting the unfinished root and plans.
 select id into anchor from public.shared_records where payload->>'seriesId'=t::text order by payload->>'day' limit 1;
 select id into history2 from public.shared_records where payload->>'seriesId'=t::text and id<>anchor order by payload->>'day' limit 1;
 for r in select * from public.shared_records where id in (anchor,history2) loop
  perform public.mutate_shared_record(r.id,s,'task',r.parent_id,r.assigned_to,r.payload||jsonb_build_object('status','Done','completedAt',1),r.version,gen_random_uuid(),false);
 end loop;
 changes:='[]';
 for r in select * from public.shared_records where space_id=s and kind='task' and (id=t or payload->>'seriesId'=t::text) and deleted_at is null order by (id=t) loop
  changes:=changes||jsonb_build_array(jsonb_build_object('id',r.id,'version',r.version,'mutation',gen_random_uuid(),'assigned',r.assigned_to,
   'parent',case when r.id=anchor then lid when r.id=history2 then anchor else r.parent_id end,
   'payload',case when r.id=anchor then r.payload||'{"repeat":"Once","seriesId":"","parentId":""}'
    when r.id=history2 then r.payload||jsonb_build_object('seriesId',anchor::text,'parentId',anchor::text) else r.payload end,
   'deleted',r.id not in (anchor,history2)));
 end loop;
 perform public.mutate_recurring_tasks(s,t,ids,changes);
 perform private.expand_daily_series(t,now()+interval '30 days');
 perform private.expand_daily_series(anchor,now()+interval '30 days');
 if (select count(*) from public.shared_records where space_id=s and kind='task' and deleted_at is null and id<>other)<>2 then raise exception 'Deleted series regenerated';end if;
 if exists(select 1 from public.shared_records where id in (anchor,history2) and (deleted_at is not null or payload->>'status'<>'Done' or payload->>'title'<>'Improve Pipo2doo')) then raise exception 'Completed history changed';end if;
 if exists(select 1 from private.task_push_queue q join public.shared_records target on target.id=q.task_id where target.space_id=s and target.deleted_at is not null and q.kind<>'sync' and q.status in ('queued','sending')) then raise exception 'Deleted-task alert still pending';end if;
 insert into series_test_context select b,s,anchor,jsonb_build_array(anchor,history2),jsonb_build_array(jsonb_build_object('id',id,'payload',payload,'parent',parent_id,'assigned',assigned_to,'version',version,'mutation',gen_random_uuid(),'deleted',false)) from public.shared_records where id=anchor;
 perform set_config('request.jwt.claim.sub',outsider::text,true);
 failed:=false;
 begin perform public.mutate_recurring_tasks(s,anchor,jsonb_build_array(anchor),changes);exception when raise_exception then failed:=true;end;
 if not failed then raise exception 'Outsider accepted';end if;
 if has_function_privilege('anon','public.mutate_recurring_tasks(uuid,uuid,jsonb,jsonb)','execute') then raise exception 'Anonymous access';end if;
 if (select prosecdef from pg_proc where oid='public.mutate_recurring_tasks(uuid,uuid,jsonb,jsonb)'::regprocedure) then raise exception 'Wrapper must be invoker';end if;
end $test$;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select uid::text from series_test_context),true);
do $$declare c series_test_context;begin
 select * into c from series_test_context;
 perform public.mutate_recurring_tasks(c.space,c.task,c.ids,c.changes);
end $$;
reset role;
select 'PASS: paired series edits, same-title isolation, unrelated-write rejection, all-or-nothing conflict, concurrent-generation guard, history retention, no regeneration, alert cancellation and outsider isolation' as result;
rollback;
