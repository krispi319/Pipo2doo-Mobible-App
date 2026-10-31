-- Synthetic users only. Queue changes and network wake requests roll back with this transaction.
begin;
do $test$
declare a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); s uuid:=gen_random_uuid();
 lid uuid:=gen_random_uuid(); t uuid:=gen_random_uuid(); together uuid:=gen_random_uuid();
 due_ms bigint:=(extract(epoch from date_trunc('minute',now()-interval '2 minutes'))*1000)::bigint;
 p jsonb; e uuid; n int; answer jsonb;
begin
 insert into auth.users(id) values(a),(b);
 insert into public.partner_spaces(id,created_by) values(s,a);
 insert into public.space_members(space_id,user_id,member_role) values(s,a,'user_1'),(s,b,'user_2');
 insert into public.poke_preferences(user_id,opt_in,quiet_enabled) values(a,true,false),(b,true,false);
 perform set_config('request.jwt.claim.sub',a::text,true);
 perform public.mutate_shared_record(lid,s,'list',null,b,'{"name":"Notification regression","emoji":"","pinned":false,"pictures":[]}',0,gen_random_uuid(),false);
 p:=jsonb_build_object('title','Assignment regression','listId',lid,'day',(to_timestamp(due_ms/1000.0) at time zone 'UTC')::date-date '1970-01-01',
 'minute',extract(hour from to_timestamp(due_ms/1000.0) at time zone 'UTC')::int*60+extract(minute from to_timestamp(due_ms/1000.0) at time zone 'UTC')::int,
 'repeat','Once','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'scheduledAt',due_ms,'scheduleZone','UTC');
 perform public.mutate_shared_record(t,s,'task',lid,a,p,0,gen_random_uuid(),false);
 if not exists(select 1 from private.task_push_queue where task_id=t and recipient_id=a and kind='created')
 or not exists(select 1 from private.task_push_queue where task_id=t and recipient_id=b and kind='sync')
 or exists(select 1 from private.task_push_queue where task_id=t and recipient_id=b and kind<>'sync') then raise exception 'User 1 creation routing failed';end if;
 perform public.claim_task_push_batch();
 if (select count(*) from private.task_push_queue where task_id=t and kind='due' and status='queued')<>1
 or not exists(select 1 from private.task_push_queue where task_id=t and kind='due' and recipient_id=a and status='queued') then raise exception 'User 1 due routing failed';end if;
 -- Partner may edit/reassign even though user 1 owns the task.
 perform set_config('request.jwt.claim.sub',b::text,true);
 perform public.mutate_shared_record(t,s,'task',lid,b,p,1,gen_random_uuid(),false);
 if exists(select 1 from private.task_push_queue where task_id=t and recipient_id=a and kind<>'sync' and status in ('queued','sending'))
 or not exists(select 1 from private.task_push_queue where task_id=t and recipient_id=b and kind='assigned')
 or not exists(select 1 from private.task_push_queue where task_id=t and recipient_id=a and kind='sync') then raise exception 'Reassignment cancellation/sync failed';end if;
 perform public.claim_task_push_batch();
 if not exists(select 1 from private.task_push_queue where task_id=t and recipient_id=b and kind='due' and status='queued') then raise exception 'User 2 due routing failed';end if;
 -- Simulate an already sent stale alert: delivery validation must still reject the former owner.
 insert into private.task_push_queue(event_key,task_id,space_id,recipient_id,kind,status) values(gen_random_uuid()::text,t,s,a,'assigned','sent') returning id into e;
 perform set_config('request.jwt.claim.sub',a::text,true);
 begin perform public.validate_task_notification(e);raise exception 'Former assignee accepted';exception when insufficient_privilege then null;end;
 update private.task_push_queue set status='sent' where task_id=t and recipient_id=a and kind='sync' returning id into e;
 answer:=public.validate_task_notification(e);
 if answer->>'kind'<>'sync' then raise exception 'Former assignee silent sync denied';end if;
 select id into e from private.task_push_queue where task_id=t and recipient_id=b and kind='assigned';
 update private.task_push_queue set status='sent' where id=e;
 perform set_config('request.jwt.claim.sub',b::text,true);
 answer:=public.validate_task_notification(e);
 if (answer->>'assignedTo')::uuid<>b then raise exception 'Current assignee validation failed';end if;
 -- Together includes both, independent of the parent list assignment.
 perform public.mutate_shared_record(together,s,'task',lid,null,p,0,gen_random_uuid(),false);
 perform public.claim_task_push_batch();
 select count(*) into n from private.task_push_queue where task_id=together and kind='created';
 if n<>2 or (select count(*) from private.task_push_queue where task_id=together and kind='due' and status='queued')<>2 then raise exception 'Together routing failed';end if;
 insert into private.task_reminder_settings(task_id,user_id,enabled) values(together,a,false);
 perform public.claim_task_push_batch();
 if exists(select 1 from private.task_push_queue where task_id=together and recipient_id=a and kind='due' and status in ('queued','sending')) then raise exception 'Personal reminder opt-out ignored';end if;
 perform public.mutate_shared_record(together,s,'task',lid,null,p||'{"status":"Done"}',1,gen_random_uuid(),false);
 perform public.claim_task_push_batch();
 if exists(select 1 from private.task_push_queue where task_id=together and kind<>'sync' and status in ('queued','sending')) then raise exception 'Completed task still alerts';end if;
end $test$;
select 'PASS: user 1, user 2, Together, reassignment cancellation, silent sync, recipient validation, parent-list independence, opt-out and completion' as result;
rollback;
