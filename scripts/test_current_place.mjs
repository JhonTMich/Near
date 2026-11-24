// node scripts/test_current_place.mjs
// The current place («En casa»): only its owner writes it, connections that see the status read it, it
// never makes anyone busy and it sends no push, unlike a shared status.
import { freshDatabase, a, b, c, ab } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const place = (label, hours = 1) => `insert into public.current_place(user_id, label, expires_at)
  values ('${a}', '${label}', now() + interval '${hours} hours')
  on conflict (user_id) do update set label = excluded.label, expires_at = excluded.expires_at`;
try {
  await t.sent();
  await t.as(a);
  await db.exec(place('En casa'));
  check((await rows(`select label from public.current_place`))[0].label === 'En casa', 'The owner shares their place');
  await db.exec(place('En la calle', 72));
  check((await rows(`select label from public.current_place`))[0].label === 'En la calle', 'Sharing again replaces it');
  await t.rejects(db.exec(place('Muy lejos', 80)), '23514', 'A place lasts at most 72 hours');
  await t.rejects(db.exec(place(' ')), '23514', 'A place needs a label');
  await t.rejects(db.exec(`insert into public.current_place(user_id, label, expires_at)
    values ('${b}', 'En casa', now() + interval '1 hour')`), '42501', 'Nobody shares the place of another person');

  await t.admin();
  check((await t.sent()).length === 0, 'Sharing a place sends no push');
  check((await rows(`select private.person_busy('${a}') busy`))[0].busy === false, 'Being at a place does not make you busy');

  await t.as(b);
  check((await rows(`select label from public.current_place`)).length === 1, 'A connection sees the place');
  await db.exec(`update public.current_place set label = 'Cambiado'`);
  await db.exec(`delete from public.current_place`);
  await t.as(c);
  check((await rows(`select label from public.current_place where user_id = '${a}'`))[0].label === 'En la calle',
    'Another person can neither change nor remove it');
  await t.as(b);
  await db.exec(`insert into public.current_place(user_id, label, expires_at) values ('${b}', 'En el trabajo', now() + interval '1 hour')`);
  await t.as(c);
  check((await rows(`select * from public.current_place where user_id = '${b}'`)).length === 0, 'Someone not connected does not see it');

  // A hides their status from B: the place goes with it.
  await t.as(a);
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, share_status) values ('${ab}', '${a}', false)`);
  await t.as(b);
  check((await rows(`select * from public.current_place where user_id = '${a}'`)).length === 0, 'Hiding the status hides the place');

  await t.as(a);
  await db.exec(`delete from public.current_place`);
  check((await rows(`select * from public.current_place where user_id = '${a}'`)).length === 0, 'The owner removes it');
  await t.admin();
  await db.exec(`insert into public.current_place(user_id, label, expires_at) values ('${a}', 'En casa', now() + interval '1 hour')`);
  await db.exec(`delete from public.account_profiles where id = '${a}'`);
  check((await rows(`select count(*)::int n from public.current_place where user_id = '${a}'`))[0].n === 0,
    'Deleting the account removes the place');
  console.log(`Current place: ${t.checks} checks passed.`);
} finally { await db.close(); }
