-- Attendance to your own activities (and the ones you follow): one row per activity and original date,
-- visible only to its owner. Missed dates are not stored: the app counts an activity it tracks as missed
-- when its date has no row. Rows older than about a year are removed as new ones arrive, so a person
-- keeps at most a few thousand small rows.
begin;
set local lock_timeout = '5s';

create table public.activity_attendance (
    user_id uuid not null default auth.uid() references public.account_profiles(id) on delete cascade,
    -- An id of public.effective_personal_schedule: your own activity or one you follow.
    schedule_id uuid not null,
    occurrence_date date not null,
    status text not null check (status in ('present', 'absent')),
    source text not null check (source in ('auto', 'manual')),
    updated_at timestamptz not null default now(),
    primary key (user_id, schedule_id, occurrence_date)
);
alter table public.activity_attendance enable row level security;
revoke all on public.activity_attendance from public, anon, authenticated;
grant select, delete on public.activity_attendance to authenticated;

create policy attendance_read on public.activity_attendance for select to authenticated
    using (user_id = (select auth.uid()));
create policy attendance_delete on public.activity_attendance for delete to authenticated
    using (user_id = (select auth.uid()));

-- Writes go through record_attendance: the activity must be yours or one you follow, the date cannot be
-- in the future, and an automatic mark (from a saved place) never replaces one set by hand.
create function private.record_attendance(activity_id uuid, on_date date, mark text, origin text)
returns setof public.activity_attendance
language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    today date;
begin
    if caller is null then raise insufficient_privilege; end if;
    if mark is null or mark not in ('present', 'absent') or origin is null or origin not in ('auto', 'manual') then
        raise exception 'Invalid attendance' using errcode = 'FM004';
    end if;
    if not exists (select 1 from public.effective_personal_schedule e where e.id = activity_id and e.user_id = caller) then
        raise insufficient_privilege;
    end if;
    select (now() at time zone p.timezone)::date into today from public.account_profiles p where p.id = caller;
    if on_date is null or on_date > today or on_date < today - 366 then
        raise exception 'Invalid attendance date' using errcode = 'FM004';
    end if;
    delete from public.activity_attendance where user_id = caller and occurrence_date < today - 400;
    return query
    insert into public.activity_attendance as a (user_id, schedule_id, occurrence_date, status, source)
    values (caller, activity_id, on_date, mark, origin)
    on conflict (user_id, schedule_id, occurrence_date) do update
        set status = excluded.status, source = excluded.source, updated_at = now()
        where a.source = 'auto' or excluded.source = 'manual'
    returning a.*;
end;
$$;
revoke all on function private.record_attendance(uuid, date, text, text) from public, anon;
grant execute on function private.record_attendance(uuid, date, text, text) to authenticated;

create function public.record_attendance(activity_id uuid, on_date date, mark text, origin text)
returns setof public.activity_attendance
language sql security invoker set search_path = '' as $$
    select * from private.record_attendance(activity_id, on_date, mark, origin);
$$;
revoke all on function public.record_attendance(uuid, date, text, text) from public, anon;
grant execute on function public.record_attendance(uuid, date, text, text) to authenticated;

-- Deleting an activity, or no longer following it, removes its attendance.
create function private.forget_attendance() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    delete from public.activity_attendance where schedule_id = old.id;
    return old;
end;
$$;
create trigger personal_schedule_forget_attendance after delete on public.personal_schedule
    for each row execute function private.forget_attendance();
create trigger followed_activity_forget_attendance after delete on public.followed_schedule_activities
    for each row execute function private.forget_attendance();

commit;
