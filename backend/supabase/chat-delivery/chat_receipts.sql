begin;
create table public.chat_read_receipts (
 message_id uuid not null references public.shared_records(id) on delete cascade,
 reader_id uuid not null references auth.users(id) on delete cascade,
 space_id uuid not null references public.partner_spaces(id) on delete cascade,
 read_at timestamptz not null default now(),
 primary key(message_id,reader_id)
);
create index chat_receipts_space on public.chat_read_receipts(space_id);
alter table public.chat_read_receipts enable row level security;
revoke all on public.chat_read_receipts from public,anon,authenticated;
grant select,insert on public.chat_read_receipts to authenticated;
create policy chat_receipt_read on public.chat_read_receipts for select to authenticated
 using (public.is_space_member(space_id) and exists(select 1 from public.shared_records r where r.id=message_id and r.space_id=chat_read_receipts.space_id and r.deleted_at is null));
create policy chat_receipt_insert on public.chat_read_receipts for insert to authenticated
 with check (reader_id=(select auth.uid()) and public.is_space_member(space_id) and exists(
 select 1 from public.shared_records r where r.id=message_id and r.space_id=chat_read_receipts.space_id
 and r.kind='appreciation' and r.deleted_at is null and r.created_by<>(select auth.uid())
 and r.payload->>'kind' in ('Message','Thank you','You''re the best','Purr-fect','CareSupport','Heart')));
create function public.mark_chat_read(p_space uuid,p_ids uuid[]) returns integer
 language plpgsql security invoker set search_path='' as $$
declare marked integer;
begin
 if auth.uid() is null or not public.is_space_member(p_space) then raise exception 'Not a member' using errcode='42501';end if;
 if coalesce(array_length(p_ids,1),0)>200 then raise exception 'Read at most 200 messages at once';end if;
 insert into public.chat_read_receipts(message_id,reader_id,space_id)
 select r.id,auth.uid(),r.space_id from public.shared_records r
 where r.space_id=p_space and r.id=any(p_ids) and r.kind='appreciation' and r.deleted_at is null
 and r.created_by<>auth.uid() and r.payload->>'kind' in ('Message','Thank you','You''re the best','Purr-fect','CareSupport','Heart')
 on conflict(message_id,reader_id) do nothing;
 get diagnostics marked = row_count;
 return marked;
end $$;
revoke all on function public.mark_chat_read(uuid,uuid[]) from public,anon;
grant execute on function public.mark_chat_read(uuid,uuid[]) to authenticated;
commit;
