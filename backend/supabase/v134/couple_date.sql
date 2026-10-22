begin;
create table public.couple_dates (
 space_id uuid primary key references public.partner_spaces(id) on delete cascade,
 since_day bigint not null check(since_day >= -25567 and since_day <= (current_date-date '1970-01-01')+1),
 version bigint not null default 1 check(version>0),
 updated_at timestamptz not null default now()
);
alter table public.couple_dates enable row level security;
revoke all on public.couple_dates from public,anon,authenticated;
grant select on public.couple_dates to authenticated;
grant insert(space_id,since_day),update(since_day) on public.couple_dates to authenticated;
create policy couple_date_read on public.couple_dates for select to authenticated using(public.is_space_member(space_id));
create policy couple_date_insert on public.couple_dates for insert to authenticated with check(public.is_space_member(space_id));
create policy couple_date_update on public.couple_dates for update to authenticated using(public.is_space_member(space_id)) with check(public.is_space_member(space_id));
create function private.bump_couple_date() returns trigger language plpgsql security invoker set search_path='' as $$
begin new.version=old.version+1;new.updated_at=now();return new;end $$;
revoke all on function private.bump_couple_date() from public,anon,authenticated;
create trigger couple_date_version before update on public.couple_dates for each row execute function private.bump_couple_date();
create function public.save_couple_date(p_space uuid,p_day bigint,p_expected bigint) returns jsonb
 language plpgsql security invoker set search_path='' as $$
declare current_row public.couple_dates%rowtype;
begin
 if auth.uid() is null or not public.is_space_member(p_space) then raise exception 'Not a member' using errcode='42501';end if;
 if p_expected is null or p_expected<0 then raise exception 'Invalid date version';end if;
 select * into current_row from public.couple_dates where space_id=p_space for update;
 if not found then
  if p_expected<>0 then raise exception 'Together date changed. Refresh and try again.' using errcode='40001';end if;
  insert into public.couple_dates(space_id,since_day) values(p_space,p_day) on conflict(space_id) do nothing returning * into current_row;
  if found then return to_jsonb(current_row);end if;
  select * into current_row from public.couple_dates where space_id=p_space for update;
 end if;
 if current_row.since_day=p_day then return to_jsonb(current_row);end if;
 if current_row.version<>p_expected then raise exception 'Together date changed on the other phone. Refresh and try again.' using errcode='40001';end if;
 update public.couple_dates set since_day=p_day where space_id=p_space returning * into current_row;
 return to_jsonb(current_row);
end $$;
revoke all on function public.save_couple_date(uuid,bigint,bigint) from public,anon;
grant execute on function public.save_couple_date(uuid,bigint,bigint) to authenticated;
commit;
