-- Run as the database admin. Everything, including test Auth users, rolls back.
begin;
create temporary table pipo_test_context(a uuid,b uuid,c uuid,sid uuid,code text,lid uuid,tid uuid,tagid uuid,childid uuid,mutation uuid);
insert into pipo_test_context values(gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),null,null,gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),gen_random_uuid());
insert into auth.users(id,aud,role,email) select a,'authenticated','authenticated',a::text||'@pipo-test.invalid' from pipo_test_context
 union all select b,'authenticated','authenticated',b::text||'@pipo-test.invalid' from pipo_test_context
 union all select c,'authenticated','authenticated',c::text||'@pipo-test.invalid' from pipo_test_context;
grant select,update on pipo_test_context to authenticated;
create function pg_temp.check_test(ok boolean,label text) returns void language plpgsql as $$begin if ok is distinct from true then raise exception 'FAILED: %',label; end if; end$$;
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from pipo_test_context;
update pipo_test_context set sid=public.create_partner_space_with_role('user_1');
select pg_temp.check_test(public.create_partner_space_with_role('user_1')=sid,'create space is idempotent') from pipo_test_context;
update pipo_test_context set code=public.issue_pair_code()->>'code';
select pg_temp.check_test(code~'^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$','valid invitation') from pipo_test_context;
select pg_temp.check_test(public.issue_pair_code()->>'status'='cooldown','regeneration cooldown');
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test(public.redeem_pair_code_with_role(code,'user_2')->>'status'='connected','second member joins') from pipo_test_context;
select pg_temp.check_test((select count(*) from public.space_members)=2,'both memberships readable');
select public.mutate_shared_record(lid,sid,'list',null,null,jsonb_build_object('name','Regression list','emoji','🧺','pinned',false,'pictures','[]'::jsonb),0,gen_random_uuid(),false) from pipo_test_context;
select public.mutate_shared_record(tagid,sid,'tag',null,null,jsonb_build_object('name','Shopping'),0,mutation,false) from pipo_test_context;
select pg_temp.check_test((public.mutate_shared_record(tagid,sid,'tag',null,null,jsonb_build_object('name','Shopping'),0,mutation,false)->>'version')::int=1,'retry is idempotent') from pipo_test_context;
select public.mutate_shared_record(tid,sid,'task',lid,a,jsonb_build_object('title','Test task','listId',lid,'day',20743,'minute',null,'repeat','Daily','status','Todo','tags',jsonb_build_array(tagid),'parentId','','completedAt',0),0,gen_random_uuid(),false) from pipo_test_context;
-- B can assign a new task to A; B cannot subsequently alter A's task.
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin
  perform public.mutate_shared_record(t.tid,t.sid,'task',t.lid,t.a,(select payload from public.shared_records where id=t.tid),1,gen_random_uuid(),true);
  raise exception 'FAILED: partner assignment protected';
 exception when raise_exception then if SQLERRM like 'FAILED:%' then raise; end if; end;
 begin
  update public.shared_records set payload='{}';
  raise exception 'FAILED: direct table writes denied';
 exception when insufficient_privilege then null; end;
end$$;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test((select count(*) from public.shared_records)=3,'member sees tag even without reconstruction');
select public.mutate_shared_record(tagid,sid,'tag',null,null,jsonb_build_object('name','Renamed'),1,gen_random_uuid(),false) from pipo_test_context;
select pg_temp.check_test((select payload->'tags' from public.shared_records where id=tid)=jsonb_build_array(tagid),'tag rename preserves task assignment') from pipo_test_context;
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin
  perform public.mutate_shared_record(t.tagid,t.sid,'tag',null,null,jsonb_build_object('name','Stale'),1,gen_random_uuid(),false);
  raise exception 'FAILED: stale version rejected';
 exception when serialization_failure then null; end;
 begin
  perform public.mutate_shared_record(gen_random_uuid(),t.sid,'tag',null,null,jsonb_build_object('name','Renamed'),0,gen_random_uuid(),false);
  raise exception 'FAILED: duplicate tag rejected';
 exception when unique_violation then null; end;
 begin
  perform public.mutate_shared_record(t.tid,t.sid,'task',t.lid,t.a,(select payload from public.shared_records where id=t.tid)||'{"privateNote":"must not upload"}',1,gen_random_uuid(),false);
  raise exception 'FAILED: private fields rejected';
 exception when raise_exception then if SQLERRM like 'FAILED:%' then raise; end if; end;
end$$;
select public.mutate_shared_record(childid,sid,'task',tid,a,jsonb_build_object('title','Future','listId',lid,'day',20744,'minute',null,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId',tid,'completedAt',0),0,gen_random_uuid(),false) from pipo_test_context;
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin
  perform public.mutate_shared_record(t.tid,t.sid,'task',t.lid,t.a,(select payload from public.shared_records where id=t.tid),1,gen_random_uuid(),true);
  raise exception 'FAILED: ancestor deletion protected';
 exception when raise_exception then if SQLERRM like 'FAILED:%' then raise; end if; end;
end$$;
-- Reparent the survivor first, matching Android's updated write order.
select public.mutate_shared_record(childid,sid,'task',lid,a,(select payload from public.shared_records where id=childid)||'{"parentId":""}',1,gen_random_uuid(),false) from pipo_test_context;
select public.mutate_shared_record(tid,sid,'task',lid,a,(select payload from public.shared_records where id=tid),1,gen_random_uuid(),true) from pipo_test_context;
select public.mutate_shared_record(tagid,sid,'tag',null,null,jsonb_build_object('name','Renamed'),2,gen_random_uuid(),true) from pipo_test_context;
select pg_temp.check_test((select count(*) from public.shared_records where kind='tag' and deleted_at is null)=0,'tag deletion persists as a tombstone');
select set_config('request.jwt.claims',jsonb_build_object('sub',c,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test((select count(*) from public.shared_records)=0,'outsider cannot read shared records');
select pg_temp.check_test((select count(*) from public.space_members)=0,'outsider cannot read members');
select pg_temp.check_test(public.redeem_pair_code_with_role(code,'user_2')->>'status'='invalid_or_unavailable','invitation is single use') from pipo_test_context;
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin
  perform public.mutate_shared_record(gen_random_uuid(),t.sid,'tag',null,null,'{"name":"Attack"}',0,gen_random_uuid(),false);
  raise exception 'FAILED: outsider cannot write';
 exception when raise_exception then if SQLERRM like 'FAILED:%' then raise; end if; end;
end$$;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from pipo_test_context;
select public.leave_partner_space();
select pg_temp.check_test((select count(*) from public.shared_records)=0,'leaving revokes own access');
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test((select count(*) from public.shared_records)=0,'leaving revokes other member access');
set local role anon;
do $$begin
 begin perform public.create_partner_space_with_role('user_1'); raise exception 'FAILED: anonymous RPC blocked';
 exception when insufficient_privilege then null; end;
end$$;
reset role;
select 'PASS: pairing, cooldown, roles, single-use invitations, tag CRUD, stable task tags, idempotency, conflict checks, assignment, parent deletion, RLS, write denial, private-field rejection, outsider isolation and unpair revocation' as result;
rollback;
