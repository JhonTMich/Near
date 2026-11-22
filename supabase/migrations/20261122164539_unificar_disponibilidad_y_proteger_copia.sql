begin;
set local lock_timeout = '5s';

create or replace function private.person_busy(person uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.personal_status st
        where st.user_id = person and st.is_active and st.expires_at > now()
          and lower(trim(st.custom_location)) <> 'disponible'
    ) or exists (
        select 1 from public.effective_personal_schedule s
        join public.account_profiles p on p.id = s.user_id
        join pg_catalog.pg_timezone_names z on z.name = p.timezone
        cross join lateral (select now() at time zone p.timezone as local_now) l
        cross join generate_series(-1, 0) offset_day
        cross join lateral private.schedule_starts_on(
            jsonb_populate_record(null::public.personal_schedule, to_jsonb(s)),
            l.local_now::date + offset_day
        ) o
        where s.user_id = person
          and (s.source_owner_id is null or private.connection_shares_between(s.source_owner_id, person))
          and (s.one_off_date is not null
               or (s.occurrence_exceptions ? to_char(o.original_date, 'YYYY-MM-DD'))
               or not private.schedule_paused(person, o.original_date))
          and (l.local_now::date + offset_day) + o.start_time <= l.local_now
          and l.local_now < (l.local_now::date + offset_day + case when o.ends_next_day then 1 else 0 end) + o.end_time
    );
$$;

create or replace function private.end_schedule_follow(request_id uuid, keep_all boolean)
returns void language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    request public.schedule_follow_requests;
    today date;
begin
    select * into request from public.schedule_follow_requests r
    where r.id = end_schedule_follow.request_id and r.follower_id = caller
    for update;
    if not found then return; end if;

    if request.approved and not private.connection_shares(request.leader_id, 'schedule') then
        raise exception 'The source schedule is no longer shared' using errcode = 'FM006';
    end if;
    if request.approved then
        select (now() at time zone p.timezone)::date into today
        from public.account_profiles p where p.id = caller;
        insert into public.personal_schedule (
            id, user_id, day_of_week, start_time, end_time, title, location, category,
            modality, ends_next_day, one_off_date, occurrence_exceptions
        )
        select a.id, caller, edited.day_of_week, edited.start_time, edited.end_time,
            edited.title, edited.location, edited.category, edited.modality, edited.ends_next_day,
            s.one_off_date,
            (case when edited.day_of_week = s.day_of_week then s.occurrence_exceptions else '{}'::jsonb end)
                || a.occurrence_changes
        from public.followed_schedule_activities a
        join public.personal_schedule s on s.id = a.source_id and s.user_id = request.leader_id
        cross join lateral jsonb_populate_record(s, a.customization - array['id', 'user_id', 'one_off_date', 'occurrence_exceptions', 'weekly_span']) edited
        where a.request_id = request.id
            and (s.one_off_date is null or s.one_off_date >= today)
            and (keep_all or a.customization <> '{}'::jsonb or a.occurrence_changes <> '{}'::jsonb);
    end if;

    delete from public.schedule_follow_requests r where r.id = request.id;
end;
$$;

commit;
