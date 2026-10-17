begin;
-- Uses one existing active couple only inside a rolled-back transaction.
-- No profile, message or reaction fixture is retained.
create temporary table rx_ctx as
 select s.id sid,m.user_id a,n.user_id b,gen_random_uuid() msg,gen_random_uuid() locket,gen_random_uuid() outsider
 from public.partner_spaces s join public.space_members m on m.space_id=s.id
 join public.space_members n on n.space_id=s.id and n.user_id<>m.user_id
 where s.closed_at is null limit 1;
do $$begin if not exists(select 1 from rx_ctx) then raise exception 'Need an active two-member test space';end if;end$$;
grant select on rx_ctx to authenticated,anon;
create function pg_temp.rx_assert(ok boolean,label text) returns void language plpgsql as $$begin
 if ok is distinct from true then raise exception 'FAILED: %',label;end if;
end$$;
create function pg_temp.rx_denied(q text) returns void language plpgsql security invoker as $$begin
 begin execute q;raise exception 'FAILED: unauthorized operation accepted';exception when insufficient_privilege then null;end;
end$$;
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from rx_ctx;
select public.mutate_shared_record(msg,sid,'appreciation',null,null,'{"kind":"Message","message":"Reaction test","taskTitle":"","picture":"","at":1}'::jsonb,0,msg,false) from rx_ctx;
select public.mutate_shared_record(locket,sid,'appreciation',null,null,'{"kind":"Locket","message":"","taskTitle":"","picture":"","at":1}'::jsonb,0,locket,false) from rx_ctx;
select public.set_chat_reaction(sid,msg,'❤️') from rx_ctx;
select public.set_chat_reaction(sid,msg,'❤️') from rx_ctx;
select pg_temp.rx_assert((select count(*) from public.chat_reactions where message_id=(select msg from rx_ctx))=1,'same desired reaction is idempotent');
select public.set_chat_reaction(sid,msg,'😂') from rx_ctx;
select pg_temp.rx_assert((select emoji from public.chat_reactions where message_id=(select msg from rx_ctx))='😂','own reaction changes');
select set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true) from rx_ctx;
select public.set_chat_reaction(sid,msg,'❤️') from rx_ctx;
select pg_temp.rx_assert((select count(*) from public.chat_reactions where message_id=(select msg from rx_ctx))=2,'partner sees both reactions');
with changed as(update public.chat_reactions set emoji='😊' where message_id=(select msg from rx_ctx) and user_id=(select a from rx_ctx) returning *)
 select pg_temp.rx_assert(count(*)=0,'cannot update partner reaction') from changed;
with changed as(delete from public.chat_reactions where message_id=(select msg from rx_ctx) and user_id=(select a from rx_ctx) returning *)
 select pg_temp.rx_assert(count(*)=0,'cannot delete partner reaction') from changed;
select pg_temp.rx_denied(format('insert into public.chat_reactions(message_id,user_id,space_id,emoji) values(%L,%L,%L,%L)',msg,outsider,sid,'❤️')) from rx_ctx;
select pg_temp.rx_denied(format('update public.chat_reactions set user_id=%L where message_id=%L',a,msg)) from rx_ctx;
select pg_temp.rx_denied(format('select public.set_chat_reaction(%L,%L,%L)',sid,locket,'❤️')) from rx_ctx;
select pg_temp.rx_denied(format('select public.set_chat_reaction(%L,%L,%L)',sid,gen_random_uuid(),'❤️')) from rx_ctx;
select pg_temp.rx_denied(format('insert into public.chat_reactions(message_id,user_id,space_id,emoji) values(%L,%L,%L,%L)',msg,b,gen_random_uuid(),'❤️')) from rx_ctx;
do $$declare t rx_ctx%rowtype;begin select * into t from rx_ctx;
 begin perform public.set_chat_reaction(t.sid,t.msg,'not an emoji');raise exception 'FAILED: invalid emoji accepted';exception when check_violation then null;end;
end$$;
select public.set_chat_reaction(sid,msg,'') from rx_ctx;
select public.set_chat_reaction(sid,msg,'') from rx_ctx;
select pg_temp.rx_assert((select count(*) from public.chat_reactions where message_id=(select msg from rx_ctx))=1,'remove is idempotent and keeps partner reaction');
select set_config('request.jwt.claims',jsonb_build_object('sub',outsider,'role','authenticated')::text,true) from rx_ctx;
select pg_temp.rx_assert((select count(*) from public.chat_reactions)=0,'outsider sees no reactions');
select pg_temp.rx_denied(format('select public.set_chat_reaction(%L,%L,%L)',sid,msg,'❤️')) from rx_ctx;
set local role anon;
select pg_temp.rx_denied('select * from public.chat_reactions');
select pg_temp.rx_denied(format('select public.set_chat_reaction(%L,%L,%L)',sid,msg,'❤️')) from rx_ctx;
reset role;
-- Soft deleting a message hides its reactions immediately.
update public.shared_records set deleted_at=now() where id=(select msg from rx_ctx);
set local role authenticated;
select set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true) from rx_ctx;
select pg_temp.rx_assert((select count(*) from public.chat_reactions where message_id=(select msg from rx_ctx))=0,'deleted message reactions hidden');
select pg_temp.rx_denied(format('select public.set_chat_reaction(%L,%L,%L)',sid,msg,'❤️')) from rx_ctx;
reset role;
select 'Reaction create/change/remove/retry, both members, forged user/space, partner ownership, invalid emoji, Locket/missing/deleted message, outsider and anonymous checks passed; rolled back.' as result;
rollback;
