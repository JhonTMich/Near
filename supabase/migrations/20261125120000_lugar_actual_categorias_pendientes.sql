-- The activity categories («university», «work») the person has a saved place for but is not in now.
-- With it, an in-person class in progress shows «Yendo a …» until the phone reaches its place, instead of
-- «En clase» next to «En la calle». Only the category travels: coordinates still never leave the phone.
-- Null for apps that do not send it yet: then the activity shows as before.
begin;
set local lock_timeout = '5s';

alter table public.current_place add column away_from text[];
alter table public.current_place add constraint current_place_away_from_categories
    check (away_from is null or away_from <@ array['university', 'work']::text[]);

commit;
