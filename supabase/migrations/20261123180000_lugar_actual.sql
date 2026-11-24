-- Where each person is now, from their saved places («En casa», «En la calle»): shown next to their
-- activity or status, never instead of it. Until now it was saved as a shared status, so it hid the
-- schedule, counted as busy for availability and «avisar cuando esté libre», and sent a push on every move.
-- Here it is only a label: it makes nobody busy and sends no notifications.
begin;
set local lock_timeout = '5s';

create table public.current_place (
    user_id uuid primary key references public.account_profiles(id) on delete cascade,
    label text not null check (length(trim(label)) between 1 and 60),
    expires_at timestamptz not null,
    updated_at timestamptz not null default now()
);
-- The app keeps a place for at most 72 hours if the phone never reports leaving it.
-- NOT VALID, like the status limits: every new write is checked against the time it is made.
alter table public.current_place
    add constraint current_place_max_lifetime check (expires_at <= now() + interval '73 hours') not valid;

alter table public.current_place enable row level security;
revoke all on public.current_place from anon, authenticated;
grant select, insert, update, delete on public.current_place to authenticated;

-- Same audience as the shared status: yourself, and connections you share your status with.
create policy place_read on public.current_place for select to authenticated using (
    user_id = (select auth.uid()) or private.connection_shares(user_id, 'status'));
create policy place_insert on public.current_place for insert to authenticated
    with check (user_id = (select auth.uid()));
create policy place_update on public.current_place for update to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy place_delete on public.current_place for delete to authenticated
    using (user_id = (select auth.uid()));

-- RLS still controls which rows each subscriber can see.
do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
       and not exists (select 1 from pg_publication_tables
                       where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'current_place') then
        alter publication supabase_realtime add table public.current_place;
    end if;
end;
$$;
commit;
