begin;
set local lock_timeout = '5s';

-- Only the actual request and its first approval generate a push. Events are queued after commit
-- through the same authenticated pg_net -> send-push path used for quick messages.
create function private.push_on_schedule_follow() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if tg_op = 'INSERT' and not new.approved then
        if not exists (
            select 1 from public.person_connections c
            join public.connection_preferences p on p.connection_id = c.id and p.owner_id = new.leader_id
            where c.person_a = new.person_a and c.person_b = new.person_b and p.muted
        ) then
            perform private.queue_push('schedule_follow', array[new.leader_id], jsonb_build_object(
                'id', new.id, 'user_id', new.follower_id,
                'name', (select display_name from public.account_profiles where id = new.follower_id)));
        end if;
    elsif tg_op = 'UPDATE' and not old.approved and new.approved then
        perform private.queue_push('schedule_follow_answer', array[new.follower_id], jsonb_build_object(
            'id', new.id, 'user_id', new.leader_id,
            'name', (select display_name from public.account_profiles where id = new.leader_id)));
    end if;
    return null;
end;
$$;
revoke all on function private.push_on_schedule_follow() from public, anon, authenticated;
create trigger near_push_schedule_follow after insert or update of approved on public.schedule_follow_requests
    for each row execute function private.push_on_schedule_follow();

-- Reload invitations immediately when either side changes them, also after tapping a push.
do $$
declare source text;
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        foreach source in array array['plan_proposals', 'schedule_follow_requests'] loop
            if not exists (select 1 from pg_publication_tables
                where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = source) then
                execute format('alter publication supabase_realtime add table public.%I', source);
            end if;
        end loop;
    end if;
end;
$$;
commit;
