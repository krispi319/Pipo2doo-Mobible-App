begin;
create temporary table backup_test_ids(a uuid,b uuid);
insert into backup_test_ids values(gen_random_uuid(),gen_random_uuid());
insert into auth.users(id,aud,role,email) select a,'authenticated','authenticated',a::text||'@backup-test.invalid' from backup_test_ids union all select b,'authenticated','authenticated',b::text||'@backup-test.invalid' from backup_test_ids;
grant select on backup_test_ids to authenticated,anon;
create function pg_temp.assert_backup(ok boolean,label text) returns void language plpgsql as $$begin if ok is distinct from true then raise exception 'FAILED: %',label;end if;end$$;
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from backup_test_ids;
select pg_temp.assert_backup((public.save_account_backup('main','{"schema":1,"date":20000}'::jsonb,0)->>'version')::bigint=1,'first upload');
select pg_temp.assert_backup((public.save_account_backup('care','{"note":"private care"}'::jsonb,0)->>'version')::bigint=1,'care upload');
select pg_temp.assert_backup((select count(*) from public.account_backups)=2,'owner reads both sections without partner membership');
select pg_temp.assert_backup((public.save_account_backup('main','{"schema":1,"date":20001}'::jsonb,1)->>'version')::bigint=2,'CAS update');
do $$begin
 begin perform public.save_account_backup('main','{}'::jsonb,1);raise exception 'FAILED: stale revision accepted';exception when serialization_failure then null;end;
 begin perform public.save_account_backup('main','{}'::jsonb,0);raise exception 'FAILED: duplicate creation accepted';exception when serialization_failure then null;end;
 begin perform public.save_account_backup('invalid','{}'::jsonb,0);raise exception 'FAILED: invalid section accepted';exception when check_violation then null;end;
 begin perform public.save_account_backup('care','[]'::jsonb,1);raise exception 'FAILED: nonobject accepted';exception when check_violation then null;end;
end$$;
select pg_temp.assert_backup((select payload->>'date' from public.account_backups where section='main')='20001','failed writes leave original intact');
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from backup_test_ids;
select pg_temp.assert_backup((select count(*) from public.account_backups)=0,'second account cannot read first account backups');
do $$begin
 begin insert into public.account_backups(user_id,section,payload) select a,'main','{}'::jsonb from backup_test_ids;raise exception 'FAILED: cross-account insert accepted';exception when insufficient_privilege then null;end;
end$$;
with edited as (update public.account_backups set payload='{}'::jsonb returning *) select pg_temp.assert_backup(count(*)=0,'second account cannot update first account') from edited;
select pg_temp.assert_backup((public.save_account_backup('main','{"owner":"second"}'::jsonb,0)->>'version')::bigint=1,'second account independent backup');
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from backup_test_ids;
do $$begin
 begin update public.account_backups set user_id=(select b from backup_test_ids) where section='care';raise exception 'FAILED: owner reassignment allowed';exception when insufficient_privilege then null;end;
end$$;
select pg_temp.assert_backup((select payload->>'date' from public.account_backups where section='main')='20001','same account restores saved value after new session');
set local role anon;
do $$begin
 begin perform * from public.account_backups;raise exception 'FAILED: anonymous read allowed';exception when insufficient_privilege then null;end;
 begin perform public.save_account_backup('main','{}'::jsonb,0);raise exception 'FAILED: anonymous RPC allowed';exception when insufficient_privilege then null;end;
end$$;
reset role;
select 'Account backup isolation, restore and concurrency checks passed; fixtures rolled back.' as result;
rollback;
