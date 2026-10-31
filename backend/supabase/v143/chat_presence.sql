-- Presence is opt-in, account-wide and restricted to the active couple.
create table public.chat_presence_preferences (
 user_id uuid primary key references auth.users(id) on delete cascade,
 show_online boolean not null default false
);
alter table public.chat_presence_preferences enable row level security;
create policy chat_visibility_read on public.chat_presence_preferences for select to authenticated using (
 user_id=(select auth.uid()) or (show_online and exists(
  select 1 from public.space_members m where m.user_id=chat_presence_preferences.user_id and public.is_space_member(m.space_id)))
);
create policy chat_visibility_insert on public.chat_presence_preferences for insert to authenticated with check (user_id=(select auth.uid()));
create policy chat_visibility_update on public.chat_presence_preferences for update to authenticated
 using (user_id=(select auth.uid())) with check (user_id=(select auth.uid()));
revoke all on public.chat_presence_preferences from public,anon,authenticated;
grant select,insert,update on public.chat_presence_preferences to authenticated;

create table public.chat_presence (
 space_id uuid not null,
 user_id uuid not null,
 device_id uuid not null,
 last_seen_at timestamptz not null default now(),
 primary key(space_id,user_id,device_id),
 foreign key(space_id,user_id) references public.space_members(space_id,user_id) on delete cascade
);
create index chat_presence_user on public.chat_presence(user_id);
alter table public.chat_presence enable row level security;
create policy chat_presence_read on public.chat_presence for select to authenticated using (
 public.is_space_member(space_id) and (user_id=(select auth.uid()) or exists(
  select 1 from public.chat_presence_preferences p where p.user_id=chat_presence.user_id and p.show_online))
);
create policy chat_presence_insert on public.chat_presence for insert to authenticated
 with check (user_id=(select auth.uid()) and public.is_space_member(space_id));
create policy chat_presence_update on public.chat_presence for update to authenticated
 using (user_id=(select auth.uid()) and public.is_space_member(space_id))
 with check (user_id=(select auth.uid()) and public.is_space_member(space_id));
create policy chat_presence_delete on public.chat_presence for delete to authenticated using (user_id=(select auth.uid()));
revoke all on public.chat_presence from public,anon,authenticated;
grant select,insert,update,delete on public.chat_presence to authenticated;

-- Callers cannot write a future heartbeat to keep a session permanently online.
create function private.stamp_chat_presence() returns trigger
language plpgsql security invoker set search_path='' as $$
begin new.last_seen_at:=clock_timestamp();return new;end $$;
revoke all on function private.stamp_chat_presence() from public,anon,authenticated;
create trigger stamp_chat_presence before insert or update on public.chat_presence
 for each row execute function private.stamp_chat_presence();

create function public.update_chat_presence(p_space uuid,p_device uuid,p_active boolean,p_expected_user uuid,p_visible boolean default null) returns jsonb
language plpgsql security invoker set search_path='' as $$
declare u uuid:=auth.uid();visible boolean;
begin
 if u is null or p_expected_user is distinct from u or p_space is null or p_device is null or p_active is null or not public.is_space_member(p_space) then
  raise exception 'Access denied' using errcode='42501';
 end if;
 -- Serialize this account's explicit visibility changes with heartbeats on other devices.
 perform pg_advisory_xact_lock(hashtextextended('chat-presence:'||u::text,0));
 insert into public.chat_presence_preferences(user_id,show_online) values(u,false) on conflict do nothing;
 if p_visible is not null then update public.chat_presence_preferences set show_online=p_visible where user_id=u;end if;
 select show_online into visible from public.chat_presence_preferences where user_id=u;
 if not visible then delete from public.chat_presence where user_id=u;
 elsif p_active then
  insert into public.chat_presence(space_id,user_id,device_id) values(p_space,u,p_device)
   on conflict(space_id,user_id,device_id) do update set last_seen_at=excluded.last_seen_at;
 else delete from public.chat_presence where space_id=p_space and user_id=u and device_id=p_device;
 end if;
 return jsonb_build_object('visible',visible,'partner_online',exists(
  select 1 from public.chat_presence s join public.chat_presence_preferences p on p.user_id=s.user_id
  where s.space_id=p_space and s.user_id<>u and p.show_online and s.last_seen_at>=clock_timestamp()-interval '70 seconds'));
end $$;
revoke all on function public.update_chat_presence(uuid,uuid,boolean,uuid,boolean) from public,anon;
grant execute on function public.update_chat_presence(uuid,uuid,boolean,uuid,boolean) to authenticated;
