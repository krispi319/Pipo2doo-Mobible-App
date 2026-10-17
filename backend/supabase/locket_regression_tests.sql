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

-- Storage metadata is temporary; no real picture bytes or real push is sent.
insert into storage.objects(bucket_id,name) select 'pipo-shared-images',sid::text||'/'||lid::text||'.jpg' from pipo_test_context;
select pg_temp.check_test((select count(*) from storage.objects where bucket_id='pipo-shared-images' and name=sid::text||'/'||lid::text||'.jpg')=1,'member can upload/read image metadata') from pipo_test_context;
select public.mutate_shared_record(childid,sid,'task',lid,b,(select payload from public.shared_records where id=childid),2,gen_random_uuid(),false) from pipo_test_context;
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from pipo_test_context;
select public.set_poke_preferences(true,'Europe/Berlin',false,1320,450);
select public.register_push_device(lid,'pipo-test-token-'||a::text) from pipo_test_context;
select pg_temp.check_test((select count(*) from storage.objects where bucket_id='pipo-shared-images' and name=sid::text||'/'||lid::text||'.jpg')=1,'partner can read shared image') from pipo_test_context;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test(public.request_partner_poke(childid,mutation)->>'status'='queued','Poke queued only for consenting partner with a device') from pipo_test_context;
select pg_temp.check_test(public.request_partner_poke(childid,mutation)->>'status'='queued','same Poke request is idempotent') from pipo_test_context;
select pg_temp.check_test(public.request_partner_poke(childid,gen_random_uuid())->>'status'='cooldown','Poke cooldown enforced') from pipo_test_context;
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from pipo_test_context;
select public.set_poke_preferences(false,'Europe/Berlin',false,1320,450);
select public.mutate_shared_record(childid,sid,'task',lid,b,(select payload from public.shared_records where id=childid)||'{"status":"Done","completedAt":1}',3,gen_random_uuid(),false) from pipo_test_context;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test((select status from public.poke_events where id=mutation)='canceled','opt-out cancels queued Poke') from pipo_test_context;
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin perform public.request_partner_poke(t.childid,gen_random_uuid()); raise exception 'FAILED: completed task cannot be poked';
 exception when raise_exception then if SQLERRM like 'FAILED:%' then raise; end if; end;
end$$;
select set_config('request.jwt.claims',jsonb_build_object('sub',c,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test((select count(*) from public.shared_records)=0,'outsider cannot read shared records');
select pg_temp.check_test((select count(*) from storage.objects where bucket_id='pipo-shared-images' and name=sid::text||'/'||lid::text||'.jpg')=0,'outsider cannot read shared image') from pipo_test_context;
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin insert into storage.objects(bucket_id,name) values('pipo-shared-images',t.sid::text||'/'||gen_random_uuid()::text||'.jpg'); raise exception 'FAILED: outsider upload denied';
 exception when insufficient_privilege then null; end;
end$$;

select pg_temp.check_test((select count(*) from public.space_members)=0,'outsider cannot read members');
select pg_temp.check_test(public.redeem_pair_code_with_role(code,'user_2')->>'status'='invalid_or_unavailable','invitation is single use') from pipo_test_context;
do $$declare t record; begin
 select * into t from pipo_test_context;
 begin
  perform public.mutate_shared_record(gen_random_uuid(),t.sid,'tag',null,null,'{"name":"Attack"}',0,gen_random_uuid(),false);
  raise exception 'FAILED: outsider cannot write';
 exception when raise_exception then if SQLERRM like 'FAILED:%' then raise; end if; end;
end$$;
-- Additional Locket notification checks; metadata and fake tokens only, rolled back.
reset role;
create temporary table locket_test(id uuid);
insert into locket_test values(gen_random_uuid());
grant select on locket_test to authenticated;
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from pipo_test_context;
select public.mutate_shared_record(l.id,t.sid,'appreciation',null,null,jsonb_build_object('kind','Locket','message','Test photo','taskTitle','','picture','cloud:'||t.sid::text||'/'||t.lid::text||'.jpg','at',1),0,gen_random_uuid(),false) from pipo_test_context t cross join locket_test l;
select pg_temp.check_test((select count(*) from public.shared_records where id=(select id from locket_test))=1,'uploader sees own Locket');
do $$ begin
 begin perform public.claim_locket_notification((select id from locket_test),(select a from pipo_test_context));raise exception 'FAILED: client cannot retrieve tokens';
 exception when insufficient_privilege then null;end;
end $$;
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from pipo_test_context;
select pg_temp.check_test((select count(*) from public.shared_records where id=(select id from locket_test))=1,'partner sees Locket');
reset role;
select pg_temp.check_test(public.claim_locket_notification(l.id,t.c)->>'status'='unavailable','outsider cannot claim Locket') from pipo_test_context t cross join locket_test l;
select pg_temp.check_test(public.claim_locket_notification(l.id,t.a)->>'status'='recipient_unavailable','opt-out prevents Locket notification') from pipo_test_context t cross join locket_test l;
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from pipo_test_context;
select public.set_poke_preferences(true,'Europe/Berlin',true,0,1439);
-- Ensure this instant lies in quiet hours, regardless of local time.
reset role;
update public.poke_preferences set quiet_start=(extract(hour from now() at time zone 'Europe/Berlin')::int*60+extract(minute from now() at time zone 'Europe/Berlin')::int),quiet_end=(extract(hour from now() at time zone 'Europe/Berlin')::int*60+extract(minute from now() at time zone 'Europe/Berlin')::int+1)%1440 where user_id=(select b from pipo_test_context);
select pg_temp.check_test(public.claim_locket_notification(l.id,t.a)->>'status'='recipient_unavailable','quiet hours prevent Locket notification') from pipo_test_context t cross join locket_test l;
update public.poke_preferences set quiet_enabled=false where user_id=(select b from pipo_test_context);
delete from private.push_devices where user_id=(select b from pipo_test_context);
select pg_temp.check_test(public.claim_locket_notification(l.id,t.a)->>'status'='no_device','missing recipient device handled') from pipo_test_context t cross join locket_test l;
insert into private.push_devices(user_id,device_id,token) select b,lid,'pipo-locket-test-'||b::text from pipo_test_context;
select pg_temp.check_test(public.claim_locket_notification(l.id,t.a)->>'status'='claimed','consenting partner Locket claimed') from pipo_test_context t cross join locket_test l;
select pg_temp.check_test(public.claim_locket_notification(l.id,t.a)->>'status'='already_sent','Locket notification deduplicated') from pipo_test_context t cross join locket_test l;
update public.shared_records set deleted_at=now() where id=(select id from locket_test);
select pg_temp.check_test(public.claim_locket_notification(l.id,t.a)->>'status'='unavailable','deleted Locket rejected') from pipo_test_context t cross join locket_test l;
set local role authenticated;
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
select 'PASS: Locket uploader and partner visibility; client token access denied; outsider, opt-out, quiet hours, missing device, deduplication, deleted record; image member/partner/outsider policies; Poke opt-in, device registration, idempotency, cooldown, opt-out cancellation, completed-task rejection; pairing, cooldown, roles, single-use invitations, tag CRUD, stable task tags, idempotency, conflict checks, assignment, parent deletion, RLS, write denial, private-field rejection, outsider isolation and unpair revocation' as result;
rollback;
