-- Keep silent sync for both phones, but scope visible assignment/due alerts to the task assignee.
do $patch$
declare d text;
begin
 d:=pg_get_functiondef('private.enqueue_task_push()'::regprocedure);
 if position('v143 assignment-scoped alerts' in d)=0 then
  if position('declare event_kind text;' in d)=0 or position('new.version||'':''||event_kind||'':''||u' in d)=0 then raise exception 'Unexpected task enqueue function';end if;
  d:=replace(d,'declare event_kind text;','declare recipient_kind text; event_kind text;');
  d:=replace(d,'if new.kind<>''task'' then return new; end if;',
   'if new.kind<>''task'' then return new; end if;
   -- v143 assignment-scoped alerts
   update private.task_push_queue set status=''canceled'' where task_id=new.id and kind<>''sync'' and status in (''queued'',''sending'')
    and new.assigned_to is not null and recipient_id<>new.assigned_to;');
  d:=replace(d,'insert into private.task_push_queue(event_key,task_id,space_id,recipient_id,actor_id,kind)',
   'recipient_kind:=case when event_kind in (''created'',''assigned'') and new.assigned_to is not null and u<>new.assigned_to then ''sync'' else event_kind end;
   insert into private.task_push_queue(event_key,task_id,space_id,recipient_id,actor_id,kind)');
  d:=replace(d,'new.version||'':''||event_kind||'':''||u','new.version||'':''||recipient_kind||'':''||u');
  d:=replace(d,'new.space_id,u,auth.uid(),event_kind)','new.space_id,u,auth.uid(),recipient_kind)');
  execute d;
 end if;
 d:=pg_get_functiondef('public.claim_task_push_batch()'::regprocedure);
 if position('r.assigned_to=m.user_id' in d)=0 then
  if position('and coalesce(s.enabled,true) and' in d)=0 or position('(r.deleted_at is null or q.kind=''sync'') and (q.kind<>''due''' in d)=0 then raise exception 'Unexpected task push worker';end if;
  d:=replace(d,'and coalesce(s.enabled,true) and','and (r.assigned_to is null or r.assigned_to=m.user_id) and coalesce(s.enabled,true) and');
  d:=replace(d,'(r.deleted_at is null or q.kind=''sync'') and (q.kind<>''due''',
   '(r.deleted_at is null or q.kind=''sync'') and (q.kind=''sync'' or r.assigned_to is null or r.assigned_to=q.recipient_id) and (q.kind<>''due''');
  execute d;
 end if;
 d:=pg_get_functiondef('public.validate_task_notification(uuid)'::regprocedure);
 if position('Task assigned to another member' in d)=0 then
  if position('if not found then raise exception ''Task unavailable''; end if;' in d)=0 then raise exception 'Unexpected task notification validation';end if;
  d:=replace(d,'if not found then raise exception ''Task unavailable''; end if;',
   'if not found then raise exception ''Task unavailable''; end if;
   if e.kind<>''sync'' and r.assigned_to is not null and r.assigned_to<>u then raise exception ''Task assigned to another member'' using errcode=''42501'';end if;');
  execute d;
 end if;
end $patch$;
