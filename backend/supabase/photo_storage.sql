-- Pipo2doo shared photos — run in your Supabase SQL editor after reviewing.
-- The bucket is private. Policies authorize access ONLY to members of the
-- active space identified by the first path segment.
-- Never use a service-role key in the Android app.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('pipo-shared-images', 'pipo-shared-images', false, 5242880, array['image/jpeg'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "pipo shared photo read" on storage.objects;
drop policy if exists "pipo shared photo upload" on storage.objects;

create policy "pipo shared photo read"
on storage.objects for select to authenticated
using (
    bucket_id = 'pipo-shared-images'
    and name ~ '^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\.jpg$'
    and exists (
       select 1 from public.space_members m
       join public.partner_spaces s on s.id = m.space_id
       where m.user_id = auth.uid()
         and s.closed_at is null
         and m.space_id::text = (storage.foldername(name))[1]
    )
);

create policy "pipo shared photo upload"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'pipo-shared-images'
    and name ~ '^[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\.jpg$'
    and exists (
       select 1 from public.space_members m
       join public.partner_spaces s on s.id = m.space_id
       where m.user_id = auth.uid()
         and s.closed_at is null
         and m.space_id::text = (storage.foldername(name))[1]
    )
);

-- Verified on 2026-10-17: the restored shared_records schema and RPC accept tag records.
-- Applied and policy-tested on 2026-10-17 after user approval.
