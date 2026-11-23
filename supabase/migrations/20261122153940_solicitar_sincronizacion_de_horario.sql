create table public.schedule_follow_requests (
    id uuid primary key default gen_random_uuid(),
    follower_id uuid not null unique,
    leader_id uuid not null,
    person_a uuid generated always as (least(follower_id, leader_id)) stored,
    person_b uuid generated always as (greatest(follower_id, leader_id)) stored,
    approved boolean not null default false,
    created_at timestamptz not null default now(),
    check (follower_id <> leader_id),
    foreign key (person_a, person_b)
        references public.person_connections (person_a, person_b) on delete cascade
);

create index schedule_follow_requests_leader on public.schedule_follow_requests (leader_id);
create index schedule_follow_requests_connection on public.schedule_follow_requests (person_a, person_b);

alter table public.schedule_follow_requests enable row level security;
revoke all on public.schedule_follow_requests from public, anon, authenticated;
grant select, delete on public.schedule_follow_requests to authenticated;
grant insert (follower_id, leader_id), update (approved)
    on public.schedule_follow_requests to authenticated;

create policy schedule_follow_read on public.schedule_follow_requests
    for select to authenticated
    using ((select auth.uid()) in (follower_id, leader_id));

create policy schedule_follow_request on public.schedule_follow_requests
    for insert to authenticated
    with check (follower_id = (select auth.uid()) and not approved);

create policy schedule_follow_approve on public.schedule_follow_requests
    for update to authenticated
    using (leader_id = (select auth.uid()) and not approved)
    with check (leader_id = (select auth.uid()) and approved);

create policy schedule_follow_end on public.schedule_follow_requests
    for delete to authenticated
    using ((select auth.uid()) in (follower_id, leader_id));
