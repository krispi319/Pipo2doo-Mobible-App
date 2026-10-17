-- v1.2.0: account-private backups, independent of partner membership.
begin;
create table if not exists public.account_backups (
 user_id uuid not null references auth.users(id) on delete cascade,
 section text not null check(section in ('main','care')),
 payload jsonb not null check(jsonb_typeof(payload)='object' and octet_length(payload::text)<=20971520),
 version bigint not null default 1 check(version>0),
 updated_at timestamptz not null default now(),
 primary key(user_id,section)
);
alter table public.account_backups enable row level security;
revoke all on public.account_backups from public, anon, authenticated;
grant select,insert,update on public.account_backups to authenticated;
create policy account_backup_read on public.account_backups for select to authenticated using ((select auth.uid())=user_id);
create policy account_backup_insert on public.account_backups for insert to authenticated with check ((select auth.uid())=user_id);
create policy account_backup_update on public.account_backups for update to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
create or replace function public.save_account_backup(p_section text,p_payload jsonb,p_expected bigint)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare saved bigint; owner_id uuid := auth.uid();
begin
 if owner_id is null then raise exception 'Sign in first' using errcode='42501'; end if;
 if p_expected < 0 then raise exception 'Invalid version'; end if;
 if p_expected=0 then
   insert into public.account_backups(user_id,section,payload) values(owner_id,p_section,p_payload)
   on conflict(user_id,section) do nothing returning version into saved;
 else
   update public.account_backups set payload=p_payload,version=version+1,updated_at=now()
   where user_id=owner_id and section=p_section and version=p_expected returning version into saved;
 end if;
 if saved is null then raise exception 'Backup changed on another device. Local changes have been preserved.' using errcode='40001'; end if;
 return jsonb_build_object('version',saved);
end $$;
revoke all on function public.save_account_backup(text,jsonb,bigint) from public,anon;
grant execute on function public.save_account_backup(text,jsonb,bigint) to authenticated;
commit;
