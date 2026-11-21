// node scripts/test_person_groups.mjs
// Private groups: only the owner sees them, only connections can be members, limits, and leaving
// the groups when a connection ends.
import { freshDatabase, a, b, c, d, ab } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const stranger = '00000000-0000-0000-0000-000000000009';
try {
  await t.admin();
  await db.exec(`set session_replication_role = replica; insert into auth.users(id) values ('${stranger}'); set session_replication_role = origin;
    insert into public.account_profiles(id, display_name) values ('${stranger}', 'Sin conexión')`);
  await t.as(a);
  const [{ id: family }] = (await db.query(`insert into public.person_groups(name) values ('Familia') returning id`)).rows;
  check((await rows(`select owner_id from public.person_groups`))[0].owner_id === a, 'The owner comes from the session');
  await db.exec(`insert into public.person_group_members(group_id, member_id) values ('${family}', '${b}'), ('${family}', '${c}')`);
  check((await rows(`select count(*)::int n from public.person_group_members`))[0].n === 2, 'Connections can be added');
  await t.rejects(db.exec(`insert into public.person_group_members(group_id, member_id) values ('${family}', '${stranger}')`),
    '42501', 'Someone without a connection cannot be added');
  await t.rejects(db.exec(`insert into public.person_groups(owner_id, name) values ('${b}', 'Ajeno')`), '42501', 'Nobody creates a group for another person');
  await db.exec(`update public.person_groups set name = 'Mi familia' where id = '${family}'`);
  check((await rows(`select name from public.person_groups`))[0].name === 'Mi familia', 'The owner renames the group');

  await t.as(b);
  check((await rows(`select * from public.person_groups`)).length === 0, 'Members do not see the group');
  check((await rows(`select * from public.person_group_members`)).length === 0, 'Nor its members');
  await t.rejects(db.exec(`insert into public.person_group_members(group_id, member_id) values ('${family}', '${b}')`),
    '42501', 'Nobody adds people to a group they do not own');
  await db.exec(`delete from public.person_group_members where group_id = '${family}'`);
  await t.admin();
  check((await rows(`select count(*)::int n from public.person_group_members`))[0].n === 2, 'Nor removes them');

  // Limits: ten groups per person.
  await t.as(a);
  await db.exec(`insert into public.person_groups(name) select 'Grupo ' || n from generate_series(1, 10) n`)
    .then(() => { throw Object.assign(new Error('no limit'), { code: 'NONE' }); })
    .catch((error) => check(error.code === 'FM001', 'More than ten groups are rejected'));

  // Disconnecting removes B from A's groups; C stays.
  await t.admin();
  await db.exec(`delete from public.person_connections where id = '${ab}'`);
  check((await rows(`select member_id from public.person_group_members`)).map((row) => row.member_id).join() === c,
    'A person leaves the groups when the connection ends');
  void d;
  console.log(`Person groups: ${t.checks} checks passed.`);
} finally { await db.close(); }
