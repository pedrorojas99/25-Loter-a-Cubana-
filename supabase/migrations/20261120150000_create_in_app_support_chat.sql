create table if not exists public.support_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.support_admins enable row level security;
drop policy if exists "support_admins_read_self" on public.support_admins;
create policy "support_admins_read_self" on public.support_admins
  for select to authenticated using (user_id = auth.uid());

create table if not exists public.support_conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  subject text not null check (char_length(trim(subject)) between 3 and 100),
  status text not null default 'open' check (status in ('open','closed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_message_at timestamptz not null default now()
);
create index if not exists support_conversations_user_last_idx on public.support_conversations(user_id, last_message_at desc);
alter table public.support_conversations enable row level security;

drop policy if exists "support_conversations_read_participant" on public.support_conversations;
create policy "support_conversations_read_participant" on public.support_conversations
  for select to authenticated using (
    user_id = auth.uid() or exists (select 1 from public.support_admins a where a.user_id = auth.uid())
  );
drop policy if exists "support_conversations_create_owner" on public.support_conversations;
create policy "support_conversations_create_owner" on public.support_conversations
  for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "support_conversations_admin_update" on public.support_conversations;
create policy "support_conversations_admin_update" on public.support_conversations
  for update to authenticated using (exists (select 1 from public.support_admins a where a.user_id = auth.uid()))
  with check (exists (select 1 from public.support_admins a where a.user_id = auth.uid()));

create table if not exists public.support_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.support_conversations(id) on delete cascade,
  sender_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  sender_role text not null default 'user' check (sender_role in ('user','admin')),
  body text not null check (char_length(trim(body)) between 1 and 2000),
  created_at timestamptz not null default now()
);
create index if not exists support_messages_conversation_created_idx on public.support_messages(conversation_id, created_at);
alter table public.support_messages enable row level security;

drop policy if exists "support_messages_read_participant" on public.support_messages;
create policy "support_messages_read_participant" on public.support_messages
  for select to authenticated using (
    exists (
      select 1 from public.support_conversations c where c.id = conversation_id
      and (c.user_id = auth.uid() or exists (select 1 from public.support_admins a where a.user_id = auth.uid()))
    )
  );
drop policy if exists "support_messages_insert_owner_or_admin" on public.support_messages;
create policy "support_messages_insert_owner_or_admin" on public.support_messages
  for insert to authenticated with check (
    sender_id = auth.uid() and (
      (sender_role = 'user' and exists (
        select 1 from public.support_conversations c where c.id = conversation_id and c.user_id = auth.uid() and c.status = 'open'
      ))
      or (sender_role = 'admin' and exists (select 1 from public.support_admins a where a.user_id = auth.uid()))
    )
  );

create or replace function public.touch_support_conversation()
returns trigger language plpgsql security definer set search_path = public, pg_temp
as $$
begin
  update public.support_conversations set updated_at = now(), last_message_at = now() where id = new.conversation_id;
  return new;
end;
$$;
drop trigger if exists support_messages_touch_conversation on public.support_messages;
create trigger support_messages_touch_conversation after insert on public.support_messages
  for each row execute function public.touch_support_conversation();

revoke all on public.support_admins from anon, authenticated;
grant select on public.support_admins to authenticated;
revoke all on public.support_conversations from anon, authenticated;
grant select, insert, update on public.support_conversations to authenticated;
revoke all on public.support_messages from anon, authenticated;
grant select, insert on public.support_messages to authenticated;
