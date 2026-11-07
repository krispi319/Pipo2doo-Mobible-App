-- Extended recurrence check; confirmation expired before execution on 2026-11-07.
-- Synthetic accounts only; all data and queue events roll back.
begin;
create temporary table cancel_ctx(a uuid,b uuid,outsider uuid,sid uuid,code text,lid uuid,tid uuid);
insert into cancel_ctx values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),null,null,gen_random_uuid(),gen_random_uuid());
insert into auth.users(id,aud,role,email)
 select a,'authenticated','authenticated',a::text||'@cancel-test.invalid' from cancel_ctx
 union all select b,'authenticated','authenticated',b::text||'@cancel-test.invalid' from cancel_ctx
 union all select outsider,'authenticated','authenticated',outsider::text||'@cancel-test.invalid' from cancel_ctx;
grant select,update on cancel_ctx to authenticated;
create function pg_temp.cancel_check(ok boolean,label text) returns void language plpgsql as $$begin if ok is distinct from true then raise exception 'FAILED: %',label;end if;end$$;
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from cancel_ctx;
update cancel_ctx set sid=public.create_partner_space_with_role('user_1');
update cancel_ctx set code=public.issue_pair_code()->>'code';
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from cancel_ctx;
select public.redeem_pair_code_with_role(code,'user_2') from cancel_ctx;
select public.mutate_shared_record(lid,sid,'list',null,null,jsonb_build_object('name','Cancellation fixture','emoji','x','pinned',false,'pictures','[]'::jsonb),0,gen_random_uuid()) from cancel_ctx;
select public.mutate_shared_record(tid,sid,'task',lid,a,jsonb_build_object('title','Synthetic task','listId',lid,'day',current_date-date '1970-01-01','minute',null,'repeat','Once','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0),0,gen_random_uuid()) from cancel_ctx;
-- Partner B cancels a task assigned only to A, retaining the assignment and record.
select public.mutate_recurring_tasks(sid,tid,jsonb_build_array(tid),jsonb_build_array(jsonb_build_object('id',tid,'parent',lid,'assigned',a,'version',1,'mutation',gen_random_uuid(),'payload',
 (select payload from public.shared_records where id=tid)||jsonb_build_object('status','Done','repeat','Once','completedAt',0,'cancelledAt',1,'cancelledBy',b,'cancelledByName','Synthetic B','cancelReason','Not needed')))) from cancel_ctx;
select pg_temp.cancel_check((select payload->>'status'='Done' and (payload->>'cancelledAt')::bigint>1 and payload->>'cancelledBy'=(select b::text from cancel_ctx) and payload->>'cancelReason'='Not needed' and deleted_at is null and assigned_to=(select a from cancel_ctx) from public.shared_records where id=(select tid from cancel_ctx)),'shared cancellation, actor, server time, reason, assignment retained');
do $$declare t cancel_ctx%rowtype;r public.shared_records;begin
 select * into t from cancel_ctx;select * into r from public.shared_records where id=t.tid;
 begin
  perform public.mutate_shared_record(t.tid,t.sid,'task',t.lid,t.a,r.payload-'cancelledAt'-'cancelledBy'-'cancelledByName'-'cancelReason',r.version,gen_random_uuid());
  raise exception 'FAILED: old client stripped cancellation';
 exception when raise_exception then if sqlerrm like 'FAILED:%' then raise;end if;end;
 begin
  perform public.mutate_shared_record(t.tid,t.sid,'task',t.lid,t.a,r.payload||'{"status":"Todo"}',r.version,gen_random_uuid());
  raise exception 'FAILED: reopened cancellation';
 exception when raise_exception then if sqlerrm like 'FAILED:%' then raise;end if;end;
 begin
  perform public.mutate_shared_record(t.tid,t.sid,'task',t.lid,t.a,r.payload,r.version-1,gen_random_uuid());
  raise exception 'FAILED: stale overwrite';
 exception when serialization_failure then null;end;
end$$;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from cancel_ctx;
select pg_temp.cancel_check((select payload->>'cancelledByName'='Synthetic B' from public.shared_records where id=(select tid from cancel_ctx)),'other member reads cancellation');
-- Exercise the actual guarded batch + daily generator, including a cancelled template.
do $$declare t cancel_ctx%rowtype;root uuid:=gen_random_uuid();anchor uuid;ids jsonb;changes jsonb;r public.shared_records;count_before integer;begin
 select * into t from cancel_ctx;
 perform public.mutate_shared_record(root,t.sid,'task',t.lid,null,jsonb_build_object('title','Synthetic daily','listId',t.lid,'day',current_date-date '1970-01-01','minute',null,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0),0,gen_random_uuid());
 select id into anchor from public.shared_records where payload->>'seriesId'=root::text and space_id=t.sid order by (payload->>'day')::bigint limit 1;
 if anchor is null then raise exception 'FAILED: daily generation';end if;
 select jsonb_agg(id) into ids from public.shared_records where space_id=t.sid and (id=root or payload->>'seriesId'=root::text);
 select jsonb_agg(jsonb_build_object('id',id,'version',version,'mutation',gen_random_uuid(),'parent',case when id=anchor then t.lid else anchor end,'payload',
  payload||jsonb_build_object('seriesId',case when id=anchor then '' else anchor::text end,'parentId',case when id=anchor then '' else anchor::text end)||
  case when id=root then jsonb_build_object('status','Done','repeat','Once','cancelledAt',1,'cancelledBy',t.a,'cancelledByName','Synthetic A','cancelReason','One date') else '{}'::jsonb end)) into changes
 from public.shared_records where space_id=t.sid and (id=root or payload->>'seriesId'=root::text);
 perform public.mutate_recurring_tasks(t.sid,root,ids,changes);
 perform pg_temp.cancel_check((select payload->>'repeat'='Once' and (payload->>'cancelledAt')::bigint>0 from public.shared_records where id=root),'template cancelled, not deleted');
 perform pg_temp.cancel_check((select payload->>'repeat'='Daily' and payload->>'status'='Todo' from public.shared_records where id=anchor),'next date remains active template');
 -- Stop all remaining unfinished dates; keep the previous cancellation metadata intact.
 select jsonb_agg(id) into ids from public.shared_records where space_id=t.sid and (id=anchor or payload->>'seriesId'=anchor::text);
 select jsonb_agg(jsonb_build_object('id',id,'version',version,'mutation',gen_random_uuid(),'parent',parent_id,'payload',
  payload||jsonb_build_object('repeat','Once','status','Done','completedAt',0,'cancelledAt',1,'cancelledBy',t.a,'cancelledByName','Synthetic A','cancelReason','Stop series')))
 into changes from public.shared_records where space_id=t.sid and (id=anchor or payload->>'seriesId'=anchor::text) and payload->>'status'<>'Done';
 perform public.mutate_recurring_tasks(t.sid,anchor,ids,changes);
 select count(*) into count_before from public.shared_records where space_id=t.sid;
 perform pg_temp.cancel_check(not exists(select 1 from public.shared_records where space_id=t.sid and payload->>'repeat'='Daily' and coalesce(payload->>'seriesId','')=''),'no daily generator survives stopping series');
end$$;
select set_config('request.jwt.claims',jsonb_build_object('sub',outsider,'role','authenticated')::text,true) from cancel_ctx;
select pg_temp.cancel_check((select count(*)=0 from public.shared_records where space_id=(select sid from cancel_ctx)),'outsider cannot read');
reset role;
select pg_temp.cancel_check(not exists(select 1 from private.task_push_queue where task_id=(select tid from cancel_ctx) and kind<>'sync' and status in ('queued','sending')),'pending notifications cancelled');
select 'PASS: paired cancellation, server audit, old-client protection, version conflict, shared read, daily root re-anchoring, stop-series generation, outsider isolation, notification invalidation. Synthetic data rolled back.' as result;
rollback;
