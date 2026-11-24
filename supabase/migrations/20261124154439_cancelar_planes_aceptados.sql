begin;
set local lock_timeout = '5s';

-- Keep the ids even if a participant removes their own copy. Cancellation can still remove the other
-- copy, including after its title or time was edited. Clients cannot write these new columns.
alter table public.plan_proposals
    add column sender_schedule_id uuid,
    add column recipient_schedule_id uuid,
    add column schedule_links_ready boolean not null default false,
    add column cancelled_by uuid references public.account_profiles(id) on delete set null;
create index plan_sender_activity on public.plan_proposals(sender_schedule_id) where sender_schedule_id is not null;
create index plan_recipient_activity on public.plan_proposals(recipient_schedule_id) where recipient_schedule_id is not null;

-- Recover unambiguous copies of old accepted plans in each participant's zone. A missing copy may
-- already have been removed locally. Multiple matches are left unlinked rather than deleting guesses.
do $$
declare p public.plan_proposals; person uuid; zone text; candidates uuid[]; copies uuid[]; ready boolean;
begin
    for p in select * from public.plan_proposals where status = 'accepted' loop
        copies := '{}'; ready := true;
        foreach person in array array[p.sender_id, p.recipient_id] loop
            select timezone into zone from public.account_profiles where id = person;
            select array_agg(s.id) into candidates from public.personal_schedule s
            where s.user_id = person and s.title = p.title and s.location = p.location
              and s.category = 'free' and s.modality = 'presencial'
              and s.one_off_date = (p.starts_at at time zone zone)::date
              and s.start_time = (p.starts_at at time zone zone)::time
              and s.end_time = (p.ends_at at time zone zone)::time
              and s.ends_next_day = ((p.ends_at at time zone zone)::date > (p.starts_at at time zone zone)::date);
            if coalesce(cardinality(candidates), 0) > 1 then ready := false; end if;
            copies := array_append(copies, candidates[1]);
        end loop;
        if ready then
            update public.plan_proposals set sender_schedule_id = copies[1], recipient_schedule_id = copies[2],
                schedule_links_ready = true where id = p.id;
        end if;
    end loop;
end;
$$;

create or replace function private.answer_plan(plan_id uuid, accept boolean) returns setof public.plan_proposals
language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid(); p public.plan_proposals; person uuid; zone text;
    local_start timestamp; local_end timestamp; activity_id uuid; sender_activity uuid; recipient_activity uuid;
begin
    if caller is null or accept is null then raise insufficient_privilege; end if;
    select * into p from public.plan_proposals where id = plan_id and recipient_id = caller;
    if not found then raise insufficient_privilege; end if;
    if accept then
        perform 1 from public.person_connections c
        where c.person_a = least(p.sender_id, caller) and c.person_b = greatest(p.sender_id, caller) for key share;
        if not found then raise exception 'The connection has ended' using errcode = 'FM007'; end if;
    end if;
    select * into p from public.plan_proposals where id = plan_id and recipient_id = caller for update;
    if not found then raise insufficient_privilege; end if;
    if p.status <> 'pending' or p.starts_at <= now() then
        raise exception 'The plan is no longer pending' using errcode = 'FM004';
    end if;
    if accept then
        foreach person in array array[p.recipient_id, p.sender_id] loop
            select timezone into zone from public.account_profiles where id = person;
            local_start := p.starts_at at time zone zone; local_end := p.ends_at at time zone zone;
            begin
                insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title, location,
                    category, modality, ends_next_day, one_off_date)
                values (person, extract(isodow from local_start)::int, local_start::time, local_end::time,
                    p.title, p.location, 'free', 'presencial', local_end::date > local_start::date, local_start::date)
                returning id into activity_id;
                if person = p.sender_id then sender_activity := activity_id; else recipient_activity := activity_id; end if;
            exception when exclusion_violation or sqlstate '23P01' then
                if person = p.sender_id then
                    raise exception 'The plan clashes with the sender schedule' using errcode = 'FM005';
                end if;
                raise exception 'The plan clashes with the recipient schedule' using errcode = '23P01';
            end;
        end loop;
    end if;
    return query update public.plan_proposals set status = case when accept then 'accepted' else 'declined' end,
        answered_at = now(), sender_schedule_id = sender_activity, recipient_schedule_id = recipient_activity,
        schedule_links_ready = accept where id = plan_id returning *;
end;
$$;

create or replace function private.cancel_plan(plan_id uuid) returns setof public.plan_proposals
language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); p public.plan_proposals;
begin
    if caller is null then raise insufficient_privilege; end if;
    select * into p from public.plan_proposals where id = plan_id and caller in (sender_id, recipient_id) for update;
    if not found then raise insufficient_privilege; end if;
    -- Retries are safe and do not repeat deletion or notifications.
    if p.status = 'cancelled' then return next p; return; end if;
    if p.status not in ('pending', 'accepted') or p.ends_at <= now()
       or (p.status = 'pending' and caller <> p.sender_id) then
        raise exception 'This plan cannot be cancelled' using errcode = 'FM004';
    end if;
    if p.status = 'accepted' then
        if not p.schedule_links_ready then
            raise exception 'Legacy plan activities could not be linked safely' using errcode = 'FM008';
        end if;
        delete from public.personal_schedule
        where (id = p.sender_schedule_id and user_id = p.sender_id)
           or (id = p.recipient_schedule_id and user_id = p.recipient_id);
    end if;
    return query update public.plan_proposals set status = 'cancelled', answered_at = now(), cancelled_by = caller
        where id = plan_id returning *;
end;
$$;

-- A save that started before cancellation cannot reinsert a removed plan copy afterwards.
create function private.check_cancelled_plan_activity() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    perform 1 from public.plan_proposals p
    where (p.sender_schedule_id = new.id and p.sender_id = new.user_id)
       or (p.recipient_schedule_id = new.id and p.recipient_id = new.user_id)
    for key share;
    if exists (select 1 from public.plan_proposals p where p.status = 'cancelled'
        and ((p.sender_schedule_id = new.id and p.sender_id = new.user_id)
          or (p.recipient_schedule_id = new.id and p.recipient_id = new.user_id))) then
        raise exception 'The shared plan was cancelled' using errcode = 'FM004';
    end if;
    return new;
end;
$$;
revoke all on function private.check_cancelled_plan_activity() from public, anon, authenticated;
create trigger check_cancelled_plan_activity before insert or update on public.personal_schedule
    for each row execute function private.check_cancelled_plan_activity();

create function private.push_on_plan_cancelled() returns trigger
language plpgsql security definer set search_path = '' as $$
declare other_person uuid;
begin
    if old.status not in ('pending', 'accepted') or new.status <> 'cancelled' or new.cancelled_by is null then return null; end if;
    other_person := case when new.cancelled_by = new.sender_id then new.recipient_id else new.sender_id end;
    -- Disconnect cleanup has no actor and does not alert former contacts.
    if not exists (select 1 from public.person_connections
        where person_a = least(new.sender_id, new.recipient_id) and person_b = greatest(new.sender_id, new.recipient_id)) then return null; end if;
    perform private.queue_push('plan_cancelled', array[other_person], jsonb_build_object(
        'id', new.id, 'user_id', new.cancelled_by, 'title', new.title, 'starts_at', new.starts_at,
        'was_accepted', old.status = 'accepted',
        'name', (select display_name from public.account_profiles where id = new.cancelled_by)));
    return null;
end;
$$;
revoke all on function private.push_on_plan_cancelled() from public, anon, authenticated;
create trigger near_push_plan_cancelled after update of status on public.plan_proposals
    for each row execute function private.push_on_plan_cancelled();

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
       and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                       and schemaname = 'public' and tablename = 'plan_proposals') then
        alter publication supabase_realtime add table public.plan_proposals;
    end if;
end;
$$;
commit;
