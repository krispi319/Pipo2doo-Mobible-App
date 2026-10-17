begin;
create table public.chat_reactions (
 message_id uuid not null references public.shared_records(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 space_id uuid not null references public.partner_spaces(id) on delete cascade,
 emoji text not null check (emoji in ('❤️','🥰','😘','😂','😊','🥺','😍','💕','🫶','👍','🎉','😢','🐱','🌸','✨','💖','🤗','😴','🙈','😎','🤭','😭','😮','💪','🙏','🔥','💯','🌷','☀️','🌙','🍀','🧸','☕','🍰','🎂','💌')),
 primary key(message_id,user_id)
);
create index chat_reactions_space on public.chat_reactions(space_id);
alter table public.chat_reactions enable row level security;
revoke all on public.chat_reactions from public,anon,authenticated;
grant select,insert,delete on public.chat_reactions to authenticated;
grant update(emoji) on public.chat_reactions to authenticated;
create policy chat_reaction_read on public.chat_reactions for select to authenticated
 using (public.is_space_member(space_id) and exists(select 1 from public.shared_records r
 where r.id=message_id and r.space_id=chat_reactions.space_id and r.kind='appreciation' and r.deleted_at is null and r.payload->>'kind'<>'Locket'));
create policy chat_reaction_insert on public.chat_reactions for insert to authenticated
 with check (user_id=(select auth.uid()) and public.is_space_member(space_id) and exists(select 1 from public.shared_records r
 where r.id=message_id and r.space_id=chat_reactions.space_id and r.kind='appreciation' and r.deleted_at is null and r.payload->>'kind'<>'Locket'));
create policy chat_reaction_update on public.chat_reactions for update to authenticated
 using (user_id=(select auth.uid()) and public.is_space_member(space_id))
 with check (user_id=(select auth.uid()) and public.is_space_member(space_id) and exists(select 1 from public.shared_records r
 where r.id=message_id and r.space_id=chat_reactions.space_id and r.kind='appreciation' and r.deleted_at is null and r.payload->>'kind'<>'Locket'));
create policy chat_reaction_delete on public.chat_reactions for delete to authenticated
 using (user_id=(select auth.uid()) and public.is_space_member(space_id));
create function public.set_chat_reaction(p_space uuid,p_message uuid,p_emoji text) returns void
 language plpgsql security invoker set search_path='' as $$
begin
 if auth.uid() is null or not public.is_space_member(p_space) then raise exception 'Not a member' using errcode='42501';end if;
 if not exists(select 1 from public.shared_records r where r.id=p_message and r.space_id=p_space
 and r.kind='appreciation' and r.deleted_at is null and r.payload->>'kind'<>'Locket') then
 raise exception 'Message is no longer available' using errcode='42501';end if;
 if p_emoji='' then
 delete from public.chat_reactions where message_id=p_message and user_id=auth.uid() and space_id=p_space;
 else
 insert into public.chat_reactions(message_id,user_id,space_id,emoji) values(p_message,auth.uid(),p_space,p_emoji)
 on conflict(message_id,user_id) do update set emoji=excluded.emoji;
 end if;
end $$;
revoke all on function public.set_chat_reaction(uuid,uuid,text) from public,anon;
grant execute on function public.set_chat_reaction(uuid,uuid,text) to authenticated;
commit;
