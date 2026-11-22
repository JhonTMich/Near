begin;
set local lock_timeout = '5s';

create or replace function private.check_plan_proposal() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if new.starts_at <= now() or new.starts_at > now() + interval '31 days' then
        raise exception 'Invalid plan time' using errcode = 'FM004';
    end if;
    perform 1 from public.person_connections c
    where c.person_a = least(new.sender_id, new.recipient_id)
      and c.person_b = greatest(new.sender_id, new.recipient_id)
    for key share;
    if not found then raise insufficient_privilege; end if;
    delete from public.plan_proposals where sender_id = new.sender_id and ends_at < now() - interval '7 days';
    if (select count(*) from public.plan_proposals
        where sender_id = new.sender_id and status = 'pending' and ends_at > now()) >= 20 then
        raise exception 'Pending plan limit reached' using errcode = 'FM001';
    end if;
    return new;
end;
$$;

create or replace function private.answer_plan(plan_id uuid, accept boolean) returns setof public.plan_proposals
language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    plan public.plan_proposals;
    person uuid;
    zone text;
    local_start timestamp;
    local_end timestamp;
begin
    if caller is null or accept is null then raise insufficient_privilege; end if;
    select * into plan from public.plan_proposals where id = plan_id and recipient_id = caller;
    if not found then raise insufficient_privilege; end if;
    if accept then
        perform 1 from public.person_connections c
        where c.person_a = least(plan.sender_id, caller)
          and c.person_b = greatest(plan.sender_id, caller)
        for key share;
        if not found then
            raise exception 'The connection has ended' using errcode = 'FM007';
        end if;
    end if;
    select * into plan from public.plan_proposals where id = plan_id and recipient_id = caller for update;
    if not found then raise insufficient_privilege; end if;
    if plan.status <> 'pending' or plan.starts_at <= now() then
        raise exception 'The plan is no longer pending' using errcode = 'FM004';
    end if;
    if accept then
        foreach person in array array[plan.recipient_id, plan.sender_id] loop
            select p.timezone into zone from public.account_profiles p where p.id = person;
            local_start := plan.starts_at at time zone zone;
            local_end := plan.ends_at at time zone zone;
            begin
                insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title, location,
                    category, modality, ends_next_day, one_off_date)
                values (person, extract(isodow from local_start)::int, local_start::time, local_end::time,
                    plan.title, plan.location, 'free', 'presencial', local_end::date > local_start::date, local_start::date);
            exception when exclusion_violation or sqlstate '23P01' then
                if person = plan.sender_id then
                    raise exception 'The plan clashes with the sender schedule' using errcode = 'FM005';
                end if;
                raise exception 'The plan clashes with the recipient schedule' using errcode = '23P01';
            end;
        end loop;
    end if;
    return query update public.plan_proposals
        set status = case when accept then 'accepted' else 'declined' end, answered_at = now()
        where id = plan_id returning *;
end;
$$;

create function private.cancel_plans_on_disconnect() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    update public.plan_proposals p
    set status = 'cancelled', answered_at = now()
    where p.status = 'pending'
      and ((p.sender_id = old.person_a and p.recipient_id = old.person_b)
        or (p.sender_id = old.person_b and p.recipient_id = old.person_a));
    return old;
end;
$$;

revoke all on function private.cancel_plans_on_disconnect() from public, anon, authenticated;

create trigger cancel_plans_on_disconnect after delete on public.person_connections
    for each row execute function private.cancel_plans_on_disconnect();

update public.plan_proposals p set status = 'cancelled', answered_at = now()
where p.status = 'pending' and not exists (
    select 1 from public.person_connections c
    where c.person_a = least(p.sender_id, p.recipient_id)
      and c.person_b = greatest(p.sender_id, p.recipient_id)
);

commit;
