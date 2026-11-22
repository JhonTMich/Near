begin;

create table public.followed_schedule_activities (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null references public.schedule_follow_requests(id) on delete cascade,
    source_id uuid not null references public.personal_schedule(id) on delete cascade,
    customization jsonb not null default '{}',
    occurrence_changes jsonb not null default '{}',
    unique (request_id, source_id),
    check (jsonb_typeof(customization) = 'object'),
    check (jsonb_typeof(occurrence_changes) = 'object')
);

create index followed_schedule_activities_source
    on public.followed_schedule_activities(source_id);
alter table public.followed_schedule_activities enable row level security;
revoke all on public.followed_schedule_activities from public, anon, authenticated;
grant select, delete on public.followed_schedule_activities to authenticated;
grant insert(request_id, source_id), update(customization)
    on public.followed_schedule_activities to authenticated;

create policy followed_activities_read on public.followed_schedule_activities
    for select to authenticated using (exists (
        select 1 from public.schedule_follow_requests r
        where r.id = request_id and r.follower_id = (select auth.uid()) and r.approved
    ));

create policy followed_activities_select_source on public.followed_schedule_activities
    for insert to authenticated with check (exists (
        select 1 from public.schedule_follow_requests r
        join public.personal_schedule s on s.id = source_id and s.user_id = r.leader_id
        where r.id = request_id and r.follower_id = (select auth.uid()) and r.approved
    ));

create policy followed_activities_customize on public.followed_schedule_activities
    for update to authenticated using (exists (
        select 1 from public.schedule_follow_requests r
        where r.id = request_id and r.follower_id = (select auth.uid()) and r.approved
    ));

create policy followed_activities_remove on public.followed_schedule_activities
    for delete to authenticated using (exists (
        select 1 from public.schedule_follow_requests r
        where r.id = request_id and r.follower_id = (select auth.uid()) and r.approved
    ));

create view public.effective_personal_schedule with (security_invoker = true) as
select s.id, s.user_id, s.day_of_week, s.start_time, s.end_time, s.title,
    s.location, s.category, s.modality, s.ends_next_day, s.occurrence_exceptions,
    s.one_off_date, null::uuid as source_id, null::uuid as source_owner_id,
    false as followed_customized
from public.personal_schedule s
union all
select a.id, r.follower_id, edited.day_of_week, edited.start_time, edited.end_time,
    edited.title, edited.location, edited.category, edited.modality, edited.ends_next_day,
    (case when edited.day_of_week = s.day_of_week then s.occurrence_exceptions else '{}'::jsonb end)
        || a.occurrence_changes,
    s.one_off_date, s.id, s.user_id, a.customization <> '{}'::jsonb or a.occurrence_changes <> '{}'::jsonb
from public.followed_schedule_activities a
join public.schedule_follow_requests r on r.id = a.request_id and r.approved
join public.personal_schedule s on s.id = a.source_id and s.user_id = r.leader_id
cross join lateral jsonb_populate_record(s, a.customization - array['id', 'user_id', 'one_off_date', 'occurrence_exceptions', 'weekly_span']) edited;

revoke all on public.effective_personal_schedule from public, anon, authenticated;
grant select on public.effective_personal_schedule to authenticated;

-- Only send the follower's own effective schedule to their availability calculation.
do $migration$
declare
    rpc text := pg_get_functiondef('public.shared_schedules_for_availability(uuid)'::regprocedure);
begin
    execute replace(rpc, 'from public.personal_schedule s where s.user_id = me.id',
        'from public.effective_personal_schedule s where s.user_id = me.id');
end;
$migration$;

-- Copy the activities and end the request in one transaction. Existing overlap
-- constraints apply to the new personal activities, so a conflict leaves the request intact.
create function private.end_schedule_follow(request_id uuid, keep_all boolean)
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

    if request.approved and private.connection_shares(request.leader_id, 'schedule') then
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

create function public.end_schedule_follow(request_id uuid, keep_all boolean)
returns void language sql security invoker set search_path = '' as $$
    select private.end_schedule_follow(request_id, keep_all);
$$;

revoke all on function private.end_schedule_follow(uuid, boolean),
    public.end_schedule_follow(uuid, boolean) from public, anon, authenticated;
grant execute on function private.end_schedule_follow(uuid, boolean),
    public.end_schedule_follow(uuid, boolean) to authenticated;
create function private.change_followed_occurrence(activity_id uuid, original_date date, change jsonb)
returns void language plpgsql security definer set search_path = '' as $$
declare
    activity record;
    target_date date;
    starts time;
    ends time;
    overnight boolean;
    date_key text := to_char(original_date, 'YYYY-MM-DD');
begin
    select s.day_of_week, s.one_off_date into activity from public.effective_personal_schedule s
    where s.id = activity_id and s.user_id = auth.uid() and s.source_id is not null
        and private.connection_shares(s.source_owner_id, 'schedule');
    if not found then raise exception 'Followed activity unavailable'; end if;
    if activity.day_of_week <> extract(isodow from original_date)::int
        or activity.one_off_date is not null then
        raise exception 'Invalid occurrence date' using errcode = 'FM004';
    end if;
    if change is null or change = 'null'::jsonb then
        update public.followed_schedule_activities
        set occurrence_changes = occurrence_changes - date_key where id = activity_id;
        return;
    end if;
    if (change->>'cancelled')::boolean is true then
        change := jsonb_build_object('cancelled', true);
    else
        target_date := (change->>'date')::date;
        starts := (change->>'start_time')::time;
        ends := (change->>'end_time')::time;
        overnight := coalesce((change->>'ends_next_day')::boolean, false);
        if target_date is null or starts is null or ends is null
            or (overnight and ends >= starts) or (not overnight and ends <= starts) then
            raise exception 'Invalid occurrence change' using errcode = 'FM004';
        end if;
        change := jsonb_build_object('date', to_char(target_date, 'YYYY-MM-DD'),
            'start_time', starts, 'end_time', ends, 'ends_next_day', overnight);
    end if;
    update public.followed_schedule_activities
    set occurrence_changes = occurrence_changes || jsonb_build_object(date_key, change)
    where id = activity_id;
end;
$$;
create function public.change_followed_occurrence(activity_id uuid, original_date date, change jsonb)
returns void language sql security invoker set search_path = '' as $$
    select private.change_followed_occurrence(activity_id, original_date, change);
$$;
revoke all on function private.change_followed_occurrence(uuid, date, jsonb),
    public.change_followed_occurrence(uuid, date, jsonb) from public, anon, authenticated;
grant execute on function private.change_followed_occurrence(uuid, date, jsonb),
    public.change_followed_occurrence(uuid, date, jsonb) to authenticated;

commit;
