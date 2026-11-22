-- Activities that happen once, on one date (a medical appointment, a trip).
-- They live in personal_schedule with one_off_date set, so the occurrence helpers, the activity
-- pushes, the availability snapshot and the overlap checks treat them like any other activity.
begin;
set local lock_timeout = '5s';
set local search_path = pg_catalog, extensions, public;

alter table public.personal_schedule
    add column one_off_date date,
    add constraint schedule_one_off_weekday check (one_off_date is null or extract(isodow from one_off_date)::int = day_of_week);

grant insert (one_off_date), update (one_off_date) on public.personal_schedule to authenticated;

-- Weekly rows are compared with weekly rows; one-off rows with one-off rows of the same date.
-- The trigger below compares one-off rows with everything else, date by date.
alter table public.personal_schedule
    drop constraint personal_schedule_no_conflicting_overlap,
    add constraint personal_schedule_no_conflicting_overlap
    exclude using gist (
        user_id with =,
        coalesce(one_off_date, date '1900-01-01') with =,
        weekly_span with &&,
        int4range(
            case when category = 'work' then 1 else 0 end,
            case when category = 'university' and modality = 'virtual' then 1 else 2 end,
            '[)'
        ) with &&
    );

create or replace function private.schedule_starts_on(s public.personal_schedule, local_date date)
returns table (original_date date, start_time time, end_time time, ends_next_day boolean)
language sql immutable security invoker set search_path = '' as $$
    select local_date, s.start_time, s.end_time, s.ends_next_day
    where s.day_of_week = extract(isodow from local_date)::int
      and (s.one_off_date is null or s.one_off_date = local_date)
      and not (s.occurrence_exceptions ? to_char(local_date, 'YYYY-MM-DD'))
    union all
    select e.key::date, (e.value->>'start_time')::time, (e.value->>'end_time')::time,
           (e.value->>'ends_next_day')::boolean
    from jsonb_each(s.occurrence_exceptions) e
    where e.value->>'date' = to_char(local_date, 'YYYY-MM-DD');
$$;
revoke all on function private.schedule_starts_on(public.personal_schedule, date) from public, anon, authenticated;

create function private.check_one_off_overlaps() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    today date;
begin
    select (now() at time zone p.timezone)::date into today from public.account_profiles p where p.id = new.user_id;
    if new.one_off_date is not null then
        -- A one-off activity is changed or cancelled by editing it, never through dated exceptions.
        if new.occurrence_exceptions <> '{}'::jsonb then
            raise exception 'One-off activities have no dated exceptions' using errcode = 'FM004';
        end if;
        if (tg_op = 'INSERT' or old.one_off_date is distinct from new.one_off_date)
           and (new.one_off_date < today or new.one_off_date > today + 366) then
            raise exception 'Invalid one-off date' using errcode = 'FM004';
        end if;
        if exists (
            select 1 from public.personal_schedule other
            cross join generate_series(-1, 1) offset_day
            cross join lateral private.schedule_starts_on(other, new.one_off_date + offset_day) candidate
            where other.user_id = new.user_id and other.id <> new.id
              and not (
                  (new.category = 'work' and other.category = 'university' and other.modality = 'virtual')
                  or (other.category = 'work' and new.category = 'university' and new.modality = 'virtual')
              )
              and new.one_off_date + new.start_time <
                  (new.one_off_date + offset_day + case when candidate.ends_next_day then 1 else 0 end) + candidate.end_time
              and (new.one_off_date + offset_day) + candidate.start_time <
                  (new.one_off_date + case when new.ends_next_day then 1 else 0 end) + new.end_time
        ) then
            raise exception 'One-off activity overlaps another activity' using errcode = '23P01';
        end if;
    elsif exists (
        select 1 from public.personal_schedule other
        cross join generate_series(-1, 1) offset_day
        cross join lateral private.schedule_starts_on(new, other.one_off_date + offset_day) candidate
        where other.user_id = new.user_id and other.id <> new.id and other.one_off_date >= today - 1
          and not (
              (new.category = 'work' and other.category = 'university' and other.modality = 'virtual')
              or (other.category = 'work' and new.category = 'university' and new.modality = 'virtual')
          )
          and other.one_off_date + other.start_time <
              (other.one_off_date + offset_day + case when candidate.ends_next_day then 1 else 0 end) + candidate.end_time
          and (other.one_off_date + offset_day) + candidate.start_time <
              (other.one_off_date + case when other.ends_next_day then 1 else 0 end) + other.end_time
    ) then
        raise exception 'Weekly activity overlaps a one-off activity' using errcode = '23P01';
    end if;
    return new;
end;
$$;
revoke all on function private.check_one_off_overlaps() from public, anon, authenticated;
create trigger findme_check_one_off_overlaps after insert or update on public.personal_schedule
    for each row execute function private.check_one_off_overlaps();

-- A pause hides weekly occurrences only; a one-off activity was planned for that date.
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
                  and (s.one_off_date is not null
                       or (s.occurrence_exceptions ? to_char(o.original_date, 'YYYY-MM-DD'))
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
