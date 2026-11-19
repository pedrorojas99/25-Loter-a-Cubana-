create or replace function public.save_ticket_with_selections(p_ticket_name text, p_selections jsonb)
returns uuid
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_user_id uuid := auth.uid();
  v_ticket_id uuid;
  v_item jsonb;
  v_game text;
  v_period text;
  v_bet_type text;
  v_selection text;
begin
  if v_user_id is null then
    raise exception 'authentication_required' using errcode = '28000';
  end if;
  if p_selections is null or jsonb_typeof(p_selections) <> 'array'
     or jsonb_array_length(p_selections) < 1
     or jsonb_array_length(p_selections) > 30 then
    raise exception 'invalid_selections' using errcode = '22023';
  end if;
  insert into public.tickets(user_id, ticket_name, status)
  values (v_user_id, nullif(left(trim(coalesce(p_ticket_name, '')), 80), ''), 'saved')
  returning id into v_ticket_id;
  for v_item in select value from jsonb_array_elements(p_selections)
  loop
    v_game := v_item->>'game';
    v_period := v_item->>'period';
    v_bet_type := v_item->>'bet_type';
    v_selection := v_item->>'selection';
    if v_game is distinct from 'PICK_3'
       or v_period is null or v_period not in ('MIDDAY','EVENING')
       or v_bet_type is null or v_bet_type not in ('NUMBER','CENTENA')
       or v_selection is null
       or not (v_selection ~ (case when v_bet_type = 'NUMBER' then '^[0-9]{2}$' else '^[0-9]{3}$' end)) then
      raise exception 'invalid_selection' using errcode = '22023';
    end if;
    insert into public.ticket_selections(ticket_id, game, period, bet_type, selection)
    values (v_ticket_id, v_game, v_period, v_bet_type, v_selection);
  end loop;
  return v_ticket_id;
end;
$$;
revoke all on function public.save_ticket_with_selections(text, jsonb) from public, anon;
grant execute on function public.save_ticket_with_selections(text, jsonb) to authenticated;
