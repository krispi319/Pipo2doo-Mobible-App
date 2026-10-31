-- Run after task_batch.sql, in one transaction. Synthetic accounts/data are rolled back.
begin;
create temporary table v142_context(u1 uuid,u2 uuid,outsider uuid,space uuid,list uuid,root uuid,selected uuid,day bigint);
grant select on v142_context to authenticated;
do $$ declare c v142_context; begin
 c.u1:=gen_random_uuid();c.u2:=gen_random_uuid();c.outsider:=gen_random_uuid();c.space:=gen_random_uuid();
 c.list:=gen_random_uuid();c.root:=gen_random_uuid();c.day:=current_date-date '1970-01-01';
 insert into auth.users(id) values(c.u1),(c.u2),(c.outsider);
 insert into public.partner_spaces(id,created_by) values(c.space,c.u1);
 insert into public.space_members(space_id,user_id,member_role) values(c.space,c.u1,'user_1'),(c.space,c.u2,'user_2');
 insert into v142_context values(c.*);
end $$;
create function pg_temp.task_change(r public.shared_records,p jsonb default null,d boolean default false) returns jsonb
language sql as $$ select jsonb_build_object('id',r.id,'kind',r.kind,'parent',r.parent_id,'assigned',r.assigned_to,
 'version',r.version,'mutation',gen_random_uuid(),'deleted',d,'payload',coalesce(p,r.payload)) $$;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select u1::text from v142_context),true);
do $$ declare c v142_context;p jsonb; begin
 select * into c from v142_context;
 perform public.mutate_shared_record(c.list,c.space,'list',null,c.u2,'{"name":"Release test","emoji":"","pinned":false,"pictures":[]}',0,gen_random_uuid());
 p:=jsonb_build_object('title','Watre plants','listId',c.list::text,'day',c.day,'minute',null,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'seriesId','','scheduledAt',0,'scheduleZone','');
 perform public.mutate_shared_record(c.root,c.space,'task',c.list,c.u2,p,0,gen_random_uuid());
 if (select count(*) from public.shared_records where space_id=c.space and kind='task')<>22 then raise exception 'Initial expansion failed'; end if;
end $$;
select set_config('request.jwt.claim.sub',(select u2::text from v142_context),true);
do $$ declare c v142_context;s public.shared_records;r public.shared_records;changes jsonb;old_title text;begin
 select * into c from v142_context;
 select * into s from public.shared_records where space_id=c.space and kind='task' and (payload->>'day')::bigint=c.day+2;
 select * into r from public.shared_records where space_id=c.space and kind='task' and (payload->>'day')::bigint=c.day+4;
 perform public.mutate_shared_record(r.id,c.space,'task',r.parent_id,r.assigned_to,r.payload||jsonb_build_object('status','Done','completedAt',1),r.version,gen_random_uuid());
 -- Split the series at the selected occurrence, preserving earlier dates.
 select jsonb_agg(pg_temp.task_change(t,case
  when t.id=s.id then t.payload||jsonb_build_object('title','Water plants','seriesId','','parentId','')
  when t.id=c.root then t.payload||jsonb_build_object('repeat','Once')
  when t.payload->>'status'='Done' then t.payload||jsonb_build_object('seriesId',s.id::text,'parentId',s.id::text)
  else t.payload||jsonb_build_object('title','Water plants','seriesId',s.id::text,'parentId',s.id::text) end)
  ||case when t.id=s.id then jsonb_build_object('parent',c.list)
    when t.id<>c.root then jsonb_build_object('parent',s.id) else '{}'::jsonb end)
 into changes from public.shared_records t where t.space_id=c.space and t.kind='task'
 and (t.id=c.root or (t.payload->>'day')::bigint>=c.day+2);
 perform public.mutate_shared_tasks(c.space,changes);
 if (select count(*) from public.shared_records where space_id=c.space and kind='task')<>22 then raise exception 'Duplicate occurrences after split'; end if;
 if exists(select 1 from public.shared_records where space_id=c.space and kind='task' and (payload->>'day')::bigint<c.day+2 and payload->>'title'<>'Watre plants') then raise exception 'History changed'; end if;
 if exists(select 1 from public.shared_records where space_id=c.space and kind='task' and (payload->>'day')::bigint>=c.day+2 and payload->>'status'<>'Done' and payload->>'title'<>'Water plants') then raise exception 'Correction missing'; end if;
 if not exists(select 1 from public.shared_records where id=r.id and payload->>'title'='Watre plants' and payload->>'status'='Done') then raise exception 'Completed history changed'; end if;
 -- A conflict on the second change must roll back the first change as well.
 select * into r from public.shared_records where id=c.root;
 old_title:=r.payload->>'title';
 select * into s from public.shared_records where id=s.id;
 begin
  perform public.mutate_shared_tasks(c.space,jsonb_build_array(
   pg_temp.task_change(r,r.payload||'{"title":"Must roll back"}'),
   pg_temp.task_change(s)||'{"version":999999}'::jsonb));
  raise exception 'Expected version conflict';
 exception when serialization_failure then null; end;
 if (select payload->>'title' from public.shared_records where id=c.root)<>old_title then raise exception 'Partial save escaped rollback'; end if;
end $$;
-- The creator may also edit a task assigned to the other member.
select set_config('request.jwt.claim.sub',(select u1::text from v142_context),true);
do $$ declare c v142_context;r public.shared_records;l uuid:=gen_random_uuid();t uuid:=gen_random_uuid();changes jsonb;begin
 select * into c from v142_context;
 select * into r from public.shared_records where id=c.root;
 perform public.mutate_shared_tasks(c.space,jsonb_build_array(pg_temp.task_change(r,r.payload||'{"title":"Creator can edit"}')));
 -- Deleting a list and its tasks is one transaction, including conflict rollback.
 perform public.mutate_shared_record(l,c.space,'list',null,c.u2,'{"name":"Delete test"}',0,gen_random_uuid());
 perform public.mutate_shared_record(t,c.space,'task',l,c.u2,jsonb_build_object('title','Test','listId',l::text,'day',c.day,'minute',null,'repeat','Once','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0),0,gen_random_uuid());
 select jsonb_agg(pg_temp.task_change(record_row,null,true) order by case when record_row.kind='task' then 0 else 1 end) into changes
 from public.shared_records record_row where record_row.id in(l,t);
 begin
  perform public.mutate_shared_tasks(c.space,jsonb_set(changes,'{1,version}','999999'));
  raise exception 'Expected delete conflict';
 exception when serialization_failure then null;end;
 if exists(select 1 from public.shared_records where id in(l,t) and deleted_at is not null) then raise exception 'Partial deletion';end if;
 perform public.mutate_shared_tasks(c.space,changes);
 if (select count(*) from public.shared_records where id in(l,t) and deleted_at is not null)<>2 then raise exception 'Deletion failed';end if;
end $$;
reset role;
-- The new template must supply the corrected title beyond the original lookahead window.
do $$ declare c v142_context;s uuid;begin
 select * into c from v142_context;
 select id into s from public.shared_records where space_id=c.space and kind='task' and payload->>'repeat'='Daily' and coalesce(payload->>'seriesId','')='';
 perform private.expand_daily_series(s,now()+interval '30 days');
 if not exists(select 1 from public.shared_records where space_id=c.space and (payload->>'day')::bigint=c.day+40 and payload->>'title'='Water plants') then raise exception 'Future generator kept old title'; end if;
end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select outsider::text from v142_context),true);
do $$ declare c v142_context;begin
 select * into c from v142_context;
 begin perform public.mutate_shared_tasks(c.space,'[]');raise exception 'Outsider allowed';
 exception when others then if sqlerrm<>'Access denied' then raise;end if;end;
end $$;
reset role;
select 'PASS: both members, atomic series split, stable occurrence count, earlier/completed history, conflict rollback, atomic list deletion, future generation and outsider isolation' as result;
rollback;
