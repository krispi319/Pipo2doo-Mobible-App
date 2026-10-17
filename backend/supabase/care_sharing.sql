-- Each person owns a separate snapshot. Only opted-in snapshots are visible to
-- their current partner; a partner can never edit another person's care record.
create or replace function public.valid_care_snapshot(p jsonb) returns boolean
language plpgsql immutable set search_path='' as $$
declare l jsonb;
begin
 if jsonb_typeof(p)<>'object' or (p-array['cycle','duration','logs'])<>'{}'::jsonb
   or not (p ?& array['cycle','duration','logs']) then return false; end if;
 if jsonb_typeof(p->'cycle')<>'number' or jsonb_typeof(p->'duration')<>'number'
   or (p->>'cycle')::numeric not between 10 and 120 or (p->>'duration')::numeric not between 1 and 14
   or (p->>'cycle')::numeric<>trunc((p->>'cycle')::numeric) or (p->>'duration')::numeric<>trunc((p->>'duration')::numeric)
   or jsonb_typeof(p->'logs')<>'array' or jsonb_array_length(p->'logs')>2000
   or octet_length(p::text)>2097152 then return false; end if;
 for l in select value from jsonb_array_elements(p->'logs') loop
  if jsonb_typeof(l)<>'object' or (l-array['day','start','end','flow','cramps','mood','cravings','symptoms','note'])<>'{}'::jsonb
   or not (l ?& array['day','start','end','flow','cramps','mood','cravings','symptoms','note'])
   or jsonb_typeof(l->'day')<>'number' or (l->>'day')::numeric not between -25567 and 47846
   or (l->>'day')::numeric<>trunc((l->>'day')::numeric)
   or jsonb_typeof(l->'start')<>'boolean' or jsonb_typeof(l->'end')<>'boolean'
   or jsonb_typeof(l->'flow')<>'string' or l->>'flow' not in ('None','Spotting','Light','Medium','Heavy')
   or jsonb_typeof(l->'cramps')<>'string' or l->>'cramps' not in ('None','Mild','Moderate','Severe')
   or jsonb_typeof(l->'mood')<>'number' or (l->>'mood')::numeric not in (-1,0,1,2,3,4)
   or jsonb_typeof(l->'cravings')<>'array' or jsonb_array_length(l->'cravings')>20
   or jsonb_typeof(l->'symptoms')<>'array' or jsonb_array_length(l->'symptoms')>20
   or jsonb_typeof(l->'note')<>'string' or length(l->>'note')>1000 then return false; end if;
  if exists(select 1 from jsonb_array_elements((l->'cravings')||(l->'symptoms')) v where jsonb_typeof(v)<>'string' or length(v#>>'{}')>80) then return false; end if;
 end loop;
 if (select count(*) from jsonb_array_elements(p->'logs'))<>(select count(distinct value->>'day') from jsonb_array_elements(p->'logs')) then return false; end if;
 return true;
exception when others then return false;
end $$;
revoke all on function public.valid_care_snapshot(jsonb) from public,anon;
grant execute on function public.valid_care_snapshot(jsonb) to authenticated;

create table if not exists public.care_shares (
 space_id uuid not null references public.partner_spaces(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 enabled boolean not null default false,
 payload jsonb not null default '{}'::jsonb,
 version bigint not null default 1 check(version>0),
 updated_at timestamptz not null default now(),
 primary key(space_id,user_id),
 constraint valid_shared_care check((not enabled and payload='{}'::jsonb) or (enabled and public.valid_care_snapshot(payload)))
);
alter table public.care_shares enable row level security;
grant select,insert,update,delete on public.care_shares to authenticated;
revoke all on public.care_shares from anon;
create policy "care owner or opted in partner reads" on public.care_shares for select to authenticated
using(public.is_space_member(space_id) and (user_id=(select auth.uid()) or enabled));
create policy "care owner inserts" on public.care_shares for insert to authenticated
with check(user_id=(select auth.uid()) and public.is_space_member(space_id));
create policy "care owner updates" on public.care_shares for update to authenticated
using(user_id=(select auth.uid()) and public.is_space_member(space_id))
with check(user_id=(select auth.uid()) and public.is_space_member(space_id));
create policy "care owner deletes" on public.care_shares for delete to authenticated
using(user_id=(select auth.uid()) and public.is_space_member(space_id));

create or replace function public.save_care_share(p_space uuid,p_enabled boolean,p_payload jsonb,p_expected_version bigint)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare r public.care_shares; data jsonb:=case when p_enabled then p_payload else '{}'::jsonb end;
begin
 if auth.uid() is null or p_expected_version<0 or p_expected_version is null or p_enabled is null then raise exception 'Authentication and valid version required'; end if;
 if p_expected_version=0 then
  insert into public.care_shares(space_id,user_id,enabled,payload) values(p_space,auth.uid(),p_enabled,data)
   on conflict(space_id,user_id) do nothing returning * into r;
 else
  update public.care_shares set enabled=p_enabled,payload=data,version=version+1,updated_at=now()
   where space_id=p_space and user_id=auth.uid() and version=p_expected_version returning * into r;
 end if;
 if r.user_id is null then raise exception using errcode='40001',message='Care changed on another device. Refresh before publishing this phone''s copy.'; end if;
 return jsonb_build_object('version',r.version,'enabled',r.enabled);
end $$;
revoke all on function public.save_care_share(uuid,boolean,jsonb,bigint) from public,anon;
grant execute on function public.save_care_share(uuid,boolean,jsonb,bigint) to authenticated;
