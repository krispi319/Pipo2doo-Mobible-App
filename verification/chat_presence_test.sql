-- Synthetic accounts and presence records; everything is rolled back.
begin;
create temporary table presence_context(a uuid,b uuid,outsider uuid,space uuid,device_a uuid,device_b uuid,device_b2 uuid);
grant select on presence_context to authenticated;
create function pg_temp.expect_presence(value boolean,label text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'Presence test failed: %',label;end if;end $$;
do $$ declare c presence_context;begin
 c.a:=gen_random_uuid();c.b:=gen_random_uuid();c.outsider:=gen_random_uuid();c.space:=gen_random_uuid();
 c.device_a:=gen_random_uuid();c.device_b:=gen_random_uuid();c.device_b2:=gen_random_uuid();
 insert into auth.users(id) values(c.a),(c.b),(c.outsider);
 insert into public.partner_spaces(id,created_by) values(c.space,c.a);
 insert into public.space_members(space_id,user_id,member_role) values(c.space,c.a,'user_1'),(c.space,c.b,'user_2');
 insert into presence_context values(c.*);
end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select a::text from presence_context),true);
do $$ declare c presence_context;r jsonb;begin
 select * into c from presence_context;
 r:=public.update_chat_presence(c.space,c.device_a,true,c.a);
 perform pg_temp.expect_presence(r='{"visible":false,"partner_online":false}','default invisible');
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence),'invisible creates no heartbeat');
 begin perform public.update_chat_presence(c.space,c.device_a,true,c.b,true);raise exception 'Expected identity failure';
 exception when insufficient_privilege then null;end;
end $$;
select set_config('request.jwt.claim.sub',(select b::text from presence_context),true);
do $$ declare c presence_context;begin
 select * into c from presence_context;
 perform public.update_chat_presence(c.space,c.device_b,true,c.b,true);
 perform public.update_chat_presence(c.space,c.device_b2,true,c.b);
 perform public.update_chat_presence(c.space,c.device_b,false,c.b);
end $$;
select set_config('request.jwt.claim.sub',(select a::text from presence_context),true);
do $$ declare c presence_context;r jsonb;n integer;begin
 select * into c from presence_context;
 r:=public.update_chat_presence(c.space,c.device_a,true,c.a);
 perform pg_temp.expect_presence((r->>'partner_online')::boolean,'second partner device stays online');
 update public.chat_presence_preferences set show_online=false where user_id=c.b;
 get diagnostics n=row_count;perform pg_temp.expect_presence(n=0,'cannot alter partner visibility');
 begin insert into public.chat_presence(space_id,user_id,device_id) values(c.space,c.b,c.device_a);raise exception 'Expected write denial';
 exception when insufficient_privilege then null;end;
end $$;
select set_config('request.jwt.claim.sub',(select b::text from presence_context),true);
do $$ declare c presence_context;r jsonb;begin
 select * into c from presence_context;
 perform public.update_chat_presence(c.space,c.device_b,true,c.b,false);
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence where user_id=c.b),'invisible clears every device');
 r:=public.update_chat_presence(c.space,c.device_b2,true,c.b);
 perform pg_temp.expect_presence(not (r->>'visible')::boolean,'stale heartbeat does not undo invisibility');
 -- Even a directly inserted session cannot bypass the visibility preference.
 insert into public.chat_presence(space_id,user_id,device_id) values(c.space,c.b,c.device_b);
end $$;
select set_config('request.jwt.claim.sub',(select a::text from presence_context),true);
do $$ declare c presence_context;r jsonb;begin
 select * into c from presence_context;
 r:=public.update_chat_presence(c.space,c.device_a,true,c.a);
 perform pg_temp.expect_presence(not (r->>'partner_online')::boolean,'invisible is offline to partner');
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence where user_id=c.b),'invisible timestamps hidden by RLS');
end $$;
select set_config('request.jwt.claim.sub',(select b::text from presence_context),true);
do $$ declare c presence_context;begin
 select * into c from presence_context;
 perform public.update_chat_presence(c.space,c.device_b,true,c.b,true);
 update public.chat_presence set last_seen_at=now()+interval '1 day' where user_id=c.b;
 perform pg_temp.expect_presence((select max(last_seen_at)<=clock_timestamp() from public.chat_presence where user_id=c.b),'cannot spoof a permanent future heartbeat');
end $$;
reset role;
-- Age only the synthetic heartbeat, bypassing the timestamp trigger inside this rollback.
alter table public.chat_presence disable trigger stamp_chat_presence;
update public.chat_presence set last_seen_at=now()-interval '71 seconds' where user_id=(select b from presence_context);
alter table public.chat_presence enable trigger stamp_chat_presence;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select a::text from presence_context),true);
do $$ declare c presence_context;r jsonb;begin
 select * into c from presence_context;
 r:=public.update_chat_presence(c.space,c.device_a,true,c.a);
 perform pg_temp.expect_presence(not (r->>'partner_online')::boolean,'expired session is offline');
end $$;
select set_config('request.jwt.claim.sub',(select b::text from presence_context),true);
do $$ declare c presence_context;begin
 select * into c from presence_context;
 perform public.update_chat_presence(c.space,c.device_b,true,c.b);
 perform public.update_chat_presence(c.space,c.device_b,false,c.b);
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence where user_id=c.b and device_id=c.device_b),'background removes device heartbeat');
end $$;
select set_config('request.jwt.claim.sub',(select outsider::text from presence_context),true);
do $$ declare c presence_context;begin
 select * into c from presence_context;
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence),'outsider cannot read presence');
 begin perform public.update_chat_presence(c.space,c.device_a,true,c.outsider);raise exception 'Expected membership denial';
 exception when insufficient_privilege then null;end;
end $$;
reset role;
delete from public.space_members where user_id=(select b from presence_context);
set local role authenticated;
select set_config('request.jwt.claim.sub',(select a::text from presence_context),true);
do $$ declare c presence_context;begin
 select * into c from presence_context;
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence where user_id=c.b),'unpair removes sessions');
 perform pg_temp.expect_presence((select count(*)=0 from public.chat_presence_preferences where user_id=c.b),'unpair hides preferences');
end $$;
reset role;
select pg_temp.expect_presence(not has_function_privilege('anon','public.update_chat_presence(uuid,uuid,boolean,uuid,boolean)','EXECUTE'),'anonymous RPC denied');
select 'PASS: invisible default, identity guard, multi-device activity, owner-only writes, invisible privacy, heartbeat expiry, timestamp guard, background, outsider and unpair isolation' as result;
rollback;
