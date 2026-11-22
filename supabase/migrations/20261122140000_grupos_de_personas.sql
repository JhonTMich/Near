-- Private groups of connected people («Familia», «Amigos»). Only their owner sees them; the members
-- are not told. A group is a way to look at several people at once and write to all of them.
begin;
set local lock_timeout = '5s';

create table public.person_groups (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references public.account_profiles(id) on delete cascade,
    name text not null check (length(trim(name)) between 1 and 40),
    created_at timestamptz not null default now()
);
create index person_groups_owner on public.person_groups(owner_id);

create table public.person_group_members (
    group_id uuid not null references public.person_groups(id) on delete cascade,
    member_id uuid not null references public.account_profiles(id) on delete cascade,
    primary key (group_id, member_id)
);
create index person_group_members_member on public.person_group_members(member_id);

alter table public.person_groups enable row level security;
alter table public.person_group_members enable row level security;
revoke all on public.person_groups, public.person_group_members from anon, authenticated;
grant select, insert (id, owner_id, name), update (name), delete on public.person_groups to authenticated;
grant select, insert (group_id, member_id), delete on public.person_group_members to authenticated;

create policy groups_owner on public.person_groups for all to authenticated
    using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));

create policy group_members_read on public.person_group_members for select to authenticated using (
    exists (select 1 from public.person_groups g where g.id = group_id and g.owner_id = (select auth.uid())));
-- Only people the owner is connected with can be added.
create policy group_members_add on public.person_group_members for insert to authenticated with check (
    exists (select 1 from public.person_groups g
            join public.person_connections c on c.person_a = least(g.owner_id, member_id) and c.person_b = greatest(g.owner_id, member_id)
            where g.id = group_id and g.owner_id = (select auth.uid())));
create policy group_members_remove on public.person_group_members for delete to authenticated using (
    exists (select 1 from public.person_groups g where g.id = group_id and g.owner_id = (select auth.uid())));

-- Ten groups per person and twenty people per group, which keeps a group message under the hourly limit.
create function private.limit_person_groups() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if tg_table_name = 'person_groups' then
        if (select count(*) from public.person_groups where owner_id = new.owner_id) >= 10 then
            raise exception 'Group limit reached' using errcode = 'FM001';
        end if;
    elsif (select count(*) from public.person_group_members where group_id = new.group_id) >= 20 then
        raise exception 'Group member limit reached' using errcode = 'FM001';
    end if;
    return new;
end;
$$;
revoke all on function private.limit_person_groups() from public, anon, authenticated;
create trigger findme_limit_person_groups before insert on public.person_groups
    for each row execute function private.limit_person_groups();
create trigger findme_limit_person_group_members before insert on public.person_group_members
    for each row execute function private.limit_person_groups();

-- Disconnecting removes each person from the other's groups.
create function private.leave_groups_on_disconnect() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    delete from public.person_group_members m using public.person_groups g
    where m.group_id = g.id and (
        (g.owner_id = old.person_a and m.member_id = old.person_b) or
        (g.owner_id = old.person_b and m.member_id = old.person_a));
    return null;
end;
$$;
revoke all on function private.leave_groups_on_disconnect() from public, anon, authenticated;
create trigger findme_leave_groups_on_disconnect after delete on public.person_connections
    for each row execute function private.leave_groups_on_disconnect();

commit;
