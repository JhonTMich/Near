-- Pause the weekly schedule for a range of dates (holidays, exam weeks).
-- A paused date hides the regular weekly occurrences; a moved occurrence stays, because it was
-- set for that exact date on purpose. Pauses are shared with the same people who see the schedule.
begin;
set local lock_timeout = '5s';

create table public.schedule_pauses (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references public.account_profiles(id) on delete cascade,
    title text not null check (length(trim(title)) between 1 and 60),
    start_date date not null,
    end_date date not null,
    created_at timestamptz not null default now(),
    constraint schedule_pause_range check (end_date >= start_date and end_date - start_date <= 366)
);
create index schedule_pauses_user on public.schedule_pauses(user_id, end_date);
alter table public.schedule_pauses enable row level security;
revoke all on public.schedule_pauses from anon, authenticated;
grant select, insert (id, user_id, title, start_date, end_date), delete on public.schedule_pauses to authenticated;

create policy pauses_read on public.schedule_pauses for select to authenticated using (
    user_id = (select auth.uid()) or private.connection_shares(user_id, 'schedule'));
create policy pauses_insert on public.schedule_pauses for insert to authenticated
    with check (user_id = (select auth.uid()));
create policy pauses_delete on public.schedule_pauses for delete to authenticated
    using (user_id = (select auth.uid()));

-- Dates in the owner's zone. Past pauses are removed when a new one is added, so they never pile up.
create function private.check_schedule_pause() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    today date;
begin
    select (now() at time zone p.timezone)::date into today from public.account_profiles p where p.id = new.user_id;
    if new.end_date < today or new.start_date > today + 366 then
        raise exception 'Invalid pause dates' using errcode = 'FM004';
    end if;
    delete from public.schedule_pauses where user_id = new.user_id and end_date < today - 30;
    if (select count(*) from public.schedule_pauses where user_id = new.user_id) >= 20 then
        raise exception 'Pause limit reached' using errcode = 'FM001';
    end if;
    return new;
end;
$$;
revoke all on function private.check_schedule_pause() from public, anon, authenticated;
create trigger findme_check_schedule_pause before insert on public.schedule_pauses
    for each row execute function private.check_schedule_pause();

create function private.schedule_paused(owner uuid, local_date date) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (select 1 from public.schedule_pauses x
                   where x.user_id = owner and local_date between x.start_date and x.end_date);
$$;
revoke all on function private.schedule_paused(uuid, date) from public, anon, authenticated;

-- The availability snapshot also carries both pauses, so a paused week shows as free.
drop function public.shared_schedules_for_availability(uuid);
create function public.shared_schedules_for_availability(contact_id uuid)
returns table (viewer_zone text, contact_zone text, viewer jsonb, contact jsonb, viewer_pauses jsonb, contact_pauses jsonb)
language sql stable security invoker set search_path = '' as $$
    select me.timezone, person.timezone,
        coalesce((select jsonb_agg(to_jsonb(s) order by s.id)
            from public.personal_schedule s where s.user_id = me.id), '[]'::jsonb),
        coalesce((select jsonb_agg(to_jsonb(s) order by s.id)
            from public.personal_schedule s where s.user_id = person.id), '[]'::jsonb),
        coalesce((select jsonb_agg(to_jsonb(x) order by x.start_date)
            from public.schedule_pauses x where x.user_id = me.id), '[]'::jsonb),
        coalesce((select jsonb_agg(to_jsonb(x) order by x.start_date)
            from public.schedule_pauses x where x.user_id = person.id), '[]'::jsonb)
    from public.account_profiles me
    join public.account_profiles person on person.id = contact_id and person.id <> me.id
    join public.person_connections c on c.person_a = least(me.id, person.id) and c.person_b = greatest(me.id, person.id)
    left join public.connection_preferences p on p.connection_id = c.id and p.owner_id = me.id
    where me.id = (select auth.uid())
      and coalesce(p.share_schedule, true)
      and private.connection_shares(person.id, 'schedule');
$$;
revoke all on function public.shared_schedules_for_availability(uuid) from public, anon;
grant execute on function public.shared_schedules_for_availability(uuid) to authenticated;

-- Activity pushes skip regular occurrences on paused dates.
do $migration$
begin
    if to_regprocedure('private.push_due_events()') is null then return; end if;
    execute $function$
        create or replace function private.push_due_events() returns void
        language plpgsql security definer set search_path = '' as $body$
        declare
            r record;
        begin
            for r in
                select s.id, s.user_id, s.title, s.location, p.display_name
                from public.personal_schedule s
                join public.account_profiles p on p.id = s.user_id
                join pg_catalog.pg_timezone_names z on z.name = p.timezone
                cross join lateral (select date_trunc('minute', now() at time zone p.timezone) as local_minute) l
                cross join lateral private.schedule_starts_on(s, l.local_minute::date) o
                where l.local_minute::date + o.start_time >= l.local_minute
                  and l.local_minute::date + o.start_time < l.local_minute + interval '1 minute'
                  and not exists (select 1 from public.personal_status st
                                  where st.user_id = s.user_id and st.is_active and st.expires_at > now())
                  and ((s.occurrence_exceptions ? to_char(o.original_date, 'YYYY-MM-DD'))
                       or not private.schedule_paused(s.user_id, o.original_date))
            loop
                perform private.queue_push('activity', private.push_audience(r.user_id, 'schedule'), jsonb_build_object(
                    'user_id', r.user_id, 'name', r.display_name, 'schedule_id', r.id,
                    'title', r.title, 'location', r.location));
            end loop;
            for r in
                select st.user_id, p.display_name from public.personal_status st
                join public.account_profiles p on p.id = st.user_id
                where st.is_active and st.expires_at <= now() and st.expires_at > now() - interval '1 minute'
            loop
                perform private.queue_push('status_end', private.push_audience(r.user_id, 'status'),
                    jsonb_build_object('user_id', r.user_id, 'name', r.display_name));
            end loop;
        end;
        $body$;
    $function$;
end;
$migration$;

commit;
