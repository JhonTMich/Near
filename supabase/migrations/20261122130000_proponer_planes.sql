-- Plans proposed from the common free time. The proposal stores instants, so each person sees it
-- in their own zone. Accepting it adds a one-off activity to both schedules in one transaction.
begin;
set local lock_timeout = '5s';

create table public.plan_proposals (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null default auth.uid() references public.account_profiles(id) on delete cascade,
    recipient_id uuid not null references public.account_profiles(id) on delete cascade,
    title text not null check (length(trim(title)) between 1 and 120),
    location text not null default '' check (length(location) <= 160),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'cancelled')),
    created_at timestamptz not null default now(),
    answered_at timestamptz,
    check (sender_id <> recipient_id),
    check (ends_at > starts_at and ends_at - starts_at <= interval '12 hours')
);
create index plan_proposals_recipient on public.plan_proposals(recipient_id, starts_at);
create index plan_proposals_sender on public.plan_proposals(sender_id, starts_at);
alter table public.plan_proposals enable row level security;
revoke all on public.plan_proposals from anon, authenticated;
grant select, insert (id, sender_id, recipient_id, title, location, starts_at, ends_at) on public.plan_proposals to authenticated;

create policy plans_participants on public.plan_proposals for select to authenticated
    using ((select auth.uid()) in (sender_id, recipient_id));
create policy plans_propose on public.plan_proposals for insert to authenticated with check (
    sender_id = (select auth.uid()) and status = 'pending' and exists (
        select 1 from public.person_connections c
        where c.person_a = least(sender_id, recipient_id) and c.person_b = greatest(sender_id, recipient_id)));

create function private.check_plan_proposal() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if new.starts_at <= now() or new.starts_at > now() + interval '31 days' then
        raise exception 'Invalid plan time' using errcode = 'FM004';
    end if;
    delete from public.plan_proposals where sender_id = new.sender_id and ends_at < now() - interval '7 days';
    if (select count(*) from public.plan_proposals
        where sender_id = new.sender_id and status = 'pending' and ends_at > now()) >= 20 then
        raise exception 'Pending plan limit reached' using errcode = 'FM001';
    end if;
    return new;
end;
$$;
revoke all on function private.check_plan_proposal() from public, anon, authenticated;
create trigger findme_check_plan_proposal before insert on public.plan_proposals
    for each row execute function private.check_plan_proposal();

-- The recipient answers. Accepting adds the activity to both schedules; a clash in the sender's
-- schedule is reported as FM005 and one in the recipient's own schedule as 23P01.
create function private.answer_plan(plan_id uuid, accept boolean) returns setof public.plan_proposals
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

create function private.cancel_plan(plan_id uuid) returns setof public.plan_proposals
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise insufficient_privilege; end if;
    return query update public.plan_proposals set status = 'cancelled', answered_at = now()
        where id = plan_id and sender_id = auth.uid() and status = 'pending' returning *;
    if not found then raise exception 'Only a pending plan can be cancelled' using errcode = 'FM004'; end if;
end;
$$;
revoke all on function private.answer_plan(uuid, boolean), private.cancel_plan(uuid) from public, anon;
grant execute on function private.answer_plan(uuid, boolean), private.cancel_plan(uuid) to authenticated;

create function public.answer_plan(plan_id uuid, accept boolean) returns setof public.plan_proposals
language sql security invoker set search_path = '' as $$ select * from private.answer_plan(plan_id, accept); $$;
create function public.cancel_plan(plan_id uuid) returns setof public.plan_proposals
language sql security invoker set search_path = '' as $$ select * from private.cancel_plan(plan_id); $$;
revoke all on function public.answer_plan(uuid, boolean), public.cancel_plan(uuid) from public, anon;
grant execute on function public.answer_plan(uuid, boolean), public.cancel_plan(uuid) to authenticated;

-- Pushes: the recipient hears about a new plan unless they silenced the sender; the sender always
-- hears the answer, because they are waiting for it.
create function private.push_on_plan() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if tg_op = 'INSERT' then
        if not exists (select 1 from public.person_connections c
                       join public.connection_preferences p on p.connection_id = c.id and p.owner_id = new.recipient_id
                       where c.person_a = least(new.sender_id, new.recipient_id)
                         and c.person_b = greatest(new.sender_id, new.recipient_id) and p.muted) then
            perform private.queue_push('plan', array[new.recipient_id], jsonb_build_object(
                'id', new.id, 'user_id', new.sender_id, 'title', new.title,
                'starts_at', new.starts_at, 'ends_at', new.ends_at,
                'name', (select display_name from public.account_profiles where id = new.sender_id)));
        end if;
    elsif old.status = 'pending' and new.status in ('accepted', 'declined') then
        perform private.queue_push('plan_answer', array[new.sender_id], jsonb_build_object(
            'id', new.id, 'user_id', new.recipient_id, 'title', new.title, 'starts_at', new.starts_at,
            'accepted', new.status = 'accepted',
            'name', (select display_name from public.account_profiles where id = new.recipient_id)));
    end if;
    return null;
end;
$$;
revoke all on function private.push_on_plan() from public, anon, authenticated;
create trigger near_push_plan after insert or update of status on public.plan_proposals
    for each row execute function private.push_on_plan();

commit;
