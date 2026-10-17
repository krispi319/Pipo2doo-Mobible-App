-- Private deduplication ledger. No photos or tokens stored in this table.
create table if not exists private.locket_push_sent (
  record_id uuid primary key references public.shared_records(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  recipient_id uuid not null references auth.users(id) on delete cascade,
  claimed_at timestamptz not null default now()
);
alter table private.locket_push_sent enable row level security;
revoke all on private.locket_push_sent from public, anon, authenticated;

-- Only the Edge Function may retrieve device tokens. Its caller is verified via Auth.
create or replace function public.claim_locket_notification(p_record uuid,p_sender uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.shared_records; recipient uuid; tokens jsonb; claimed uuid;
begin
  perform pg_advisory_xact_lock(hashtextextended('membership',0));
  select * into r from public.shared_records where id=p_record and created_by=p_sender
    and kind='appreciation' and payload->>'kind'='Locket'
    and payload->>'picture' like 'cloud:%' and deleted_at is null
    and updated_at>now()-interval '24 hours';
  if not found or not exists(select 1 from public.space_members where space_id=r.space_id and user_id=p_sender)
    or not exists(select 1 from public.partner_spaces where id=r.space_id and closed_at is null)
    then return jsonb_build_object('status','unavailable'); end if;
  select user_id into recipient from public.space_members where space_id=r.space_id and user_id<>p_sender;
  if recipient is null then return jsonb_build_object('status','unavailable'); end if;
  if not private.poke_allowed(recipient) then return jsonb_build_object('status','recipient_unavailable'); end if;
  select jsonb_agg(token) into tokens from private.push_devices where user_id=recipient and updated_at>now()-interval '60 days';
  if tokens is null then return jsonb_build_object('status','no_device'); end if;
  insert into private.locket_push_sent(record_id,sender_id,recipient_id) values(p_record,p_sender,recipient)
    on conflict(record_id) do nothing returning record_id into claimed;
  if claimed is null then return jsonb_build_object('status','already_sent'); end if;
  return jsonb_build_object('status','claimed','record_id',r.id,'recipient_id',recipient,'tokens',tokens);
end $$;
revoke all on function public.claim_locket_notification(uuid,uuid) from public,anon,authenticated;
grant execute on function public.claim_locket_notification(uuid,uuid) to service_role;
