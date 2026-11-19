create table if not exists public.account_recovery_codes (
  user_id uuid primary key references auth.users(id) on delete cascade,
  code_hash text not null,
  attempts integer not null default 0 check (attempts >= 0),
  locked_until timestamptz,
  claim_id uuid,
  claim_expires_at timestamptz,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);
alter table public.account_recovery_codes enable row level security;
revoke all on table public.account_recovery_codes from anon, authenticated;
grant all on table public.account_recovery_codes to service_role;

create or replace function public.claim_account_recovery_code(p_email text, p_code_hash text, p_claim_id uuid)
returns uuid language plpgsql security definer set search_path = ''
as $$
declare
  v_user_id uuid;
  v_code_hash text;
  v_attempts integer;
  v_locked_until timestamptz;
  v_claim_expires_at timestamptz;
  v_consumed_at timestamptz;
begin
  select u.id into v_user_id from auth.users u
  where lower(u.email) = lower(trim(p_email)) limit 1;
  if v_user_id is null then return null; end if;
  select r.code_hash, r.attempts, r.locked_until, r.claim_expires_at, r.consumed_at
    into v_code_hash, v_attempts, v_locked_until, v_claim_expires_at, v_consumed_at
  from public.account_recovery_codes r where r.user_id = v_user_id for update;
  if not found or v_consumed_at is not null then return null; end if;
  if v_locked_until is not null and v_locked_until > now() then return null; end if;
  if v_claim_expires_at is not null and v_claim_expires_at > now() then return null; end if;
  if v_code_hash <> lower(p_code_hash) then
    update public.account_recovery_codes
    set attempts = attempts + 1,
        locked_until = case when attempts + 1 >= 5 then now() + interval '15 minutes' else locked_until end
    where user_id = v_user_id;
    return null;
  end if;
  update public.account_recovery_codes
  set claim_id = p_claim_id, claim_expires_at = now() + interval '2 minutes'
  where user_id = v_user_id;
  return v_user_id;
end;
$$;

create or replace function public.complete_account_recovery_code(p_user_id uuid, p_claim_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_count integer;
begin
  update public.account_recovery_codes
  set consumed_at = now(), claim_id = null, claim_expires_at = null
  where user_id = p_user_id and claim_id = p_claim_id and consumed_at is null;
  get diagnostics v_count = row_count;
  return v_count = 1;
end;
$$;

create or replace function public.release_account_recovery_claim(p_user_id uuid, p_claim_id uuid)
returns boolean language plpgsql security definer set search_path = ''
as $$
declare v_count integer;
begin
  update public.account_recovery_codes
  set claim_id = null, claim_expires_at = null
  where user_id = p_user_id and claim_id = p_claim_id and consumed_at is null;
  get diagnostics v_count = row_count;
  return v_count = 1;
end;
$$;

revoke all on function public.claim_account_recovery_code(text,text,uuid) from public, anon, authenticated;
revoke all on function public.complete_account_recovery_code(uuid,uuid) from public, anon, authenticated;
revoke all on function public.release_account_recovery_claim(uuid,uuid) from public, anon, authenticated;
grant execute on function public.claim_account_recovery_code(text,text,uuid) to service_role;
grant execute on function public.complete_account_recovery_code(uuid,uuid) to service_role;
grant execute on function public.release_account_recovery_claim(uuid,uuid) to service_role;
