-- Preserve existing RPC signatures, grants and membership checks.
begin;
do $$declare d text; begin
 d:=pg_get_functiondef('public.request_partner_poke(uuid,uuid)'::regprocedure);
 if position('where id=p_task and kind=''task'' and deleted_at is null' in d)>0 then
  d:=replace(d,'where id=p_task and kind=''task'' and deleted_at is null','where id=p_task and kind=''task'' and deleted_at is null and coalesce(payload->>''status'',''Todo'')<>''Done''');
  execute d;
 else raise exception 'Unexpected request_partner_poke definition; review before patching'; end if;
 d:=pg_get_functiondef('public.claim_partner_poke(uuid,uuid)'::regprocedure);
 if position('where id=e.task_id and deleted_at is null' in d)>0 then
  d:=replace(d,'where id=e.task_id and deleted_at is null','where id=e.task_id and deleted_at is null and coalesce(payload->>''status'',''Todo'')<>''Done''');
  execute d;
 else raise exception 'Unexpected claim_partner_poke definition'; end if;
 d:=pg_get_functiondef('public.register_push_device(uuid,text)'::regprocedure);
 if position('if (select count(*) from private.push_devices' in d)>0 then
  d:=replace(d,'if (select count(*) from private.push_devices','if length(p_token) not between 1 and 4096 then raise exception ''Invalid push token''; end if; delete from private.push_devices where token=p_token and user_id<>u; if (select count(*) from private.push_devices');
  execute d;
 else raise exception 'Unexpected register_push_device definition'; end if;
 d:=pg_get_functiondef('private.poke_allowed(uuid)'::regprocedure);
 d:=replace(d,'if p.quiet_enabled and (p.quiet_start=p.quiet_end or','if p.quiet_enabled and p.quiet_start<>p.quiet_end and (');
 execute d;
end $$;
commit;
