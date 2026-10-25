-- Verification-only contracts for the isolated pipo_v139_test fixture. Do not run against production.
do $$
declare p jsonb;n int;before_n int;root uuid:='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';sid uuid:='10000000-0000-0000-0000-000000000001';u uuid:='00000000-0000-0000-0000-000000000001';d bigint:=current_date-date '1970-01-01';child uuid;
begin
 p:=jsonb_build_object('title','Daily','listId','home','day',d-1,'minute',1080,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'scheduledAt',0,'scheduleZone','');
 insert into pipo_v139_test.shared_records(id,space_id,kind,created_by,payload) values(root,sid,'task',u,p);
 select count(*) into n from pipo_v139_test.shared_records where payload->>'seriesId'=root::text;
 if n<>22 then raise exception 'Expected today plus 21 future days, got %',n; end if;
 if not exists(select 1 from pipo_v139_test.shared_records where payload->>'seriesId'=root::text and (payload->>'day')::bigint=d+1 and payload->>'status'='Todo') then raise exception 'Tomorrow missing'; end if;
 if (select count(*) from pipo_v139_test.task_push_queue where kind='created' and task_id=root)<>2 then raise exception 'Generated occurrence spammed creation notifications'; end if;
 perform pipo_v139_test.expand_daily_tasks();
 if (select count(*) from pipo_v139_test.shared_records where payload->>'seriesId'=root::text)<>22 then raise exception 'Duplicate generation'; end if;
 child:=pipo_v139_test.daily_occurrence_id(root,d);
 update pipo_v139_test.shared_records set payload=payload||jsonb_build_object('status','Done','completedAt',123) where id=child;
 perform pipo_v139_test.expand_daily_tasks();
 if (select payload->>'status' from pipo_v139_test.shared_records where id=child)<>'Done' then raise exception 'Completed day was reset'; end if;
 if (select payload->>'status' from pipo_v139_test.shared_records where id=pipo_v139_test.daily_occurrence_id(root,d+1))<>'Todo' then raise exception 'One completion changed another day'; end if;
 update pipo_v139_test.shared_records set payload=payload||jsonb_build_object('status','Done','completedAt',124) where id=root;
 if (select count(*) from pipo_v139_test.shared_records where payload->>'seriesId'=root::text)<>22 then raise exception 'Root completion duplicated dates'; end if;
 update pipo_v139_test.shared_records set deleted_at=now() where id=child;
 perform pipo_v139_test.expand_daily_tasks();
 if (select deleted_at from pipo_v139_test.shared_records where id=child) is null then raise exception 'Deleted occurrence resurrected'; end if;
 -- Every generated day remains a shallow sibling.
 if exists(select 1 from pipo_v139_test.shared_records where payload->>'seriesId'=root::text and parent_id<>root) then raise exception 'Deep ancestry created'; end if;
 -- Stop rule retains existing dates but prevents a new future day.
 update pipo_v139_test.shared_records set payload=jsonb_set(payload,'{repeat}','"Once"') where id=root;
 select count(*) into before_n from pipo_v139_test.shared_records;
 perform pipo_v139_test.expand_daily_tasks();
 if (select count(*) from pipo_v139_test.shared_records)<>before_n then raise exception 'Stopped series expanded'; end if;
 if has_function_privilege('authenticated','pipo_v139_test.expand_daily_tasks()','execute') or has_function_privilege('anon','pipo_v139_test.expand_daily_series(uuid,timestamptz)','execute') then raise exception 'Private worker publicly executable'; end if;
 if not exists(select 1 from cron.jobs where name='pipo-daily-occurrences') then raise exception 'Cron missing'; end if;
end $$;
-- Anchor generation around autumn transition in the current 21-day window.
do $$
declare r uuid:='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';p jsonb;day bigint:=date '2026-10-24'-date '1970-01-01';
begin
 p:=jsonb_build_object('title','DST','listId','home','day',day,'minute',150,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'scheduledAt',1792801800000,'scheduleZone','Europe/Berlin');
 -- epoch derives from the declared wall clock rather than a hard-coded test clock.
 p:=jsonb_set(p,'{scheduledAt}',to_jsonb((extract(epoch from timestamp '2026-10-24 02:30' at time zone 'Europe/Berlin')*1000)::bigint));
 insert into pipo_v139_test.shared_records(id,space_id,kind,created_by,payload) values(r,'10000000-0000-0000-0000-000000000001','task','00000000-0000-0000-0000-000000000001',p);
 if current_date between date '2026-10-24' and date '2026-10-25' and (select (payload->>'scheduledAt')::bigint from pipo_v139_test.shared_records where id=pipo_v139_test.daily_occurrence_id(r,day+1))<>1792888200000 then raise exception 'Autumn overlap differs from Android earlier offset'; end if;
end $$;
select 'Daily generation, history, no duplicates, completion isolation, deletion, stopping, private grants and cron checks passed' as result;

do $$
declare r uuid:='cccccccc-cccc-cccc-cccc-cccccccccccc';p jsonb;day bigint:=date '2026-03-28'-date '1970-01-01';v jsonb;
begin
 p:=jsonb_build_object('title','Spring','listId','home','day',day,'minute',150,'repeat','Daily','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'scheduledAt',(extract(epoch from timestamp '2026-03-28 02:30' at time zone 'Europe/Berlin')*1000)::bigint,'scheduleZone','Europe/Berlin');
 insert into pipo_v139_test.shared_records(id,space_id,kind,created_by,payload) values(r,'10000000-0000-0000-0000-000000000001','task','00000000-0000-0000-0000-000000000001',p);
 perform pipo_v139_test.expand_daily_series(r,timestamptz '2026-03-28 12:00+01');
 select payload into v from pipo_v139_test.shared_records where id=pipo_v139_test.daily_occurrence_id(r,day+1);
 if (v->>'minute')::int<>210 then raise exception 'Spring gap did not move to 03:30'; end if;
 if (v->>'scheduledAt')::bigint<>(extract(epoch from timestamptz '2026-03-29 01:30Z')*1000)::bigint then raise exception 'Wrong spring instant';end if;
end $$;
select 'Spring DST instant and canonical minute validation passed' as result;

do $$
declare r uuid:='dddddddd-dddd-dddd-dddd-dddddddddddd';p jsonb;d bigint:=current_date-date '1970-01-01';u uuid:='00000000-0000-0000-0000-000000000001';
begin
 p:=jsonb_build_object('title','Quiet daily','listId','home','day',d,'minute',1080,'repeat','Once','status','Todo','tags','[]'::jsonb,'parentId','','completedAt',0,'scheduledAt',0,'scheduleZone','');
 insert into pipo_v139_test.shared_records(id,space_id,kind,created_by,payload) values(r,'10000000-0000-0000-0000-000000000001','task',u,p);
 insert into pipo_v139_test.task_reminder_settings values(r,u,false,15);
 update pipo_v139_test.shared_records set payload=jsonb_set(payload,'{repeat}','"Daily"') where id=r;
 if (select count(*) from pipo_v139_test.task_reminder_settings s join pipo_v139_test.shared_records c on c.id=s.task_id where c.payload->>'seriesId'=r::text and s.user_id=u and s.enabled=false and s.offset_minutes=15)<>21 then raise exception 'New dates did not inherit private reminder settings'; end if;
end $$;
select 'Per-user reminder defaults inherited by newly generated dates' as result;
