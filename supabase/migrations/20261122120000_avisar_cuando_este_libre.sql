-- "Tell me when they are free": a person asks once and gets one push when the other person has no
-- activity in progress and no status other than «Disponible». Requests last 24 hours.
begin;
set local lock_timeout = '5s';

create table public.free_alerts (
    watcher_id uuid not null default auth.uid() references public.account_profiles(id) on delete cascade,
    target_id uuid not null references public.account_profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '24 hours',
    primary key (watcher_id, target_id),
    check (watcher_id <> target_id)
);
create index free_alerts_target on public.free_alerts(target_id);
alter table public.free_alerts enable row level security;
revoke all on public.free_alerts from anon, authenticated;
grant select, insert (watcher_id, target_id), delete on public.free_alerts to authenticated;

-- Only for people who share their schedule with the one asking. The target never sees the request.
create policy free_alerts_own on public.free_alerts for select to authenticated
    using (watcher_id = (select auth.uid()));
create policy free_alerts_insert on public.free_alerts for insert to authenticated
    with check (watcher_id = (select auth.uid()) and private.connection_shares(target_id, 'schedule'));
create policy free_alerts_delete on public.free_alerts for delete to authenticated
    using (watcher_id = (select auth.uid()));

-- Busy: an activity in progress in the person's zone (pauses and one-off activities included, as in
-- the activity pushes) or a shared status that is not «Disponible».
create function private.person_busy(person uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.personal_status st
        where st.user_id = person and st.is_active and st.expires_at > now()
          and lower(trim(st.custom_location)) <> 'disponible'
    ) or exists (
        select 1 from public.personal_schedule s
        join public.account_profiles p on p.id = s.user_id
        join pg_catalog.pg_timezone_names z on z.name = p.timezone
        cross join lateral (select now() at time zone p.timezone as local_now) l
        cross join generate_series(-1, 0) offset_day
        cross join lateral private.schedule_starts_on(s, l.local_now::date + offset_day) o
        where s.user_id = person
          and (s.one_off_date is not null
               or (s.occurrence_exceptions ? to_char(o.original_date, 'YYYY-MM-DD'))
               or not private.schedule_paused(person, o.original_date))
          and (l.local_now::date + offset_day) + o.start_time <= l.local_now
          and l.local_now < (l.local_now::date + offset_day + case when o.ends_next_day then 1 else 0 end) + o.end_time
    );
$$;
revoke all on function private.person_busy(uuid) from public, anon, authenticated;

-- Run every minute by pg_cron. Sends at most one push per request, then forgets it.
create function private.push_free_alerts() returns void
language plpgsql security definer set search_path = '' as $$
declare
    r record;
begin
    delete from public.free_alerts where expires_at <= now();
    for r in
        select f.watcher_id, f.target_id, p.display_name
        from public.free_alerts f
        join public.account_profiles p on p.id = f.target_id
        join public.person_connections c on c.person_a = least(f.watcher_id, f.target_id)
                                        and c.person_b = greatest(f.watcher_id, f.target_id)
        left join public.connection_preferences shared on shared.connection_id = c.id and shared.owner_id = f.target_id
        where coalesce(shared.share_schedule, true) and not private.person_busy(f.target_id)
    loop
        perform private.queue_push('free', array[r.watcher_id],
            jsonb_build_object('user_id', r.target_id, 'name', r.display_name));
        delete from public.free_alerts where watcher_id = r.watcher_id and target_id = r.target_id;
    end loop;
    -- A request whose connection or sharing ended can never be answered.
    delete from public.free_alerts f where not private.connection_shares_between(f.target_id, f.watcher_id);
end;
$$;

-- Same question as connection_shares, asked for two given people instead of the caller.
create function private.connection_shares_between(owner uuid, viewer uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.person_connections c
        left join public.connection_preferences p on p.connection_id = c.id and p.owner_id = owner
        where c.person_a = least(owner, viewer) and c.person_b = greatest(owner, viewer)
          and coalesce(p.share_schedule, true));
$$;
revoke all on function private.push_free_alerts(), private.connection_shares_between(uuid, uuid) from public, anon, authenticated;

commit;
