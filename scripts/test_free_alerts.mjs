// node scripts/test_free_alerts.mjs
// "Tell me when they are free": who may ask, when the push is sent and that it is sent once.
import { freshDatabase, a, b, c, d, ad } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const run = async () => { await t.admin(); await db.exec('select private.push_free_alerts();'); return (await t.sent()).filter((call) => call.type === 'free'); };
try {
  // A shares nothing with D; B and C see A's schedule.
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, share_schedule) values ('${ad}', '${a}', false)`);
  // A has an activity in progress (two minutes around now, in UTC).
  const placed = (await rows(`insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title)
    select '${a}', extract(isodow from now() at time zone 'UTC')::int,
           (date_trunc('minute', now() at time zone 'UTC') - interval '1 minute')::time,
           (date_trunc('minute', now() at time zone 'UTC') + interval '2 minute')::time, 'Clase'
    where (date_trunc('minute', now() at time zone 'UTC') + interval '2 minute')::time
        > (date_trunc('minute', now() at time zone 'UTC') - interval '1 minute')::time
    returning id`)).length;

  await t.as(b);
  await db.exec(`insert into public.free_alerts(target_id) values ('${a}')`);
  check((await rows(`select watcher_id from public.free_alerts`))[0].watcher_id === b, 'The watcher comes from the session');
  await t.rejects(db.exec(`insert into public.free_alerts(watcher_id, target_id) values ('${c}', '${a}')`), '42501', 'Nobody asks on behalf of another person');
  await t.rejects(db.exec(`insert into public.free_alerts(target_id) values ('${b}')`), '42501', 'Nobody watches themself');
  await t.as(d);
  await t.rejects(db.exec(`insert into public.free_alerts(target_id) values ('${a}')`), '42501', 'Only people who see the schedule may ask');
  await t.as(a);
  check((await rows(`select * from public.free_alerts`)).length === 0, 'The watched person does not see who asked');

  if (placed) {
    check((await run()).length === 0, 'No push while the activity is in progress');
    await t.admin();
    await db.exec(`delete from public.personal_schedule`);
  }
  // A shared status other than «Disponible» still counts as busy.
  await db.exec(`insert into public.personal_status(id, user_id, custom_location, expires_at) values ('${a}', '${a}', 'En reunión', now() + interval '1 hour')`);
  check((await run()).length === 0, 'A status such as «En reunión» is busy');
  await db.exec(`update public.personal_status set custom_location = 'Disponible' where id = '${a}'`);
  const calls = await run();
  check(calls.length === 1 && calls[0].recipients.join() === b && calls[0].data.user_id === a && calls[0].data.name === 'Ana',
    'Once free, the watcher gets one push about that person');
  check((await run()).length === 0, 'And only one');
  check((await rows(`select count(*)::int n from public.free_alerts`))[0].n === 0, 'The request is removed after the push');

  // Requests that can no longer be answered are removed: expired, or the schedule stopped being shared.
  await db.exec(`update public.personal_status set custom_location = 'Ocupado' where id = '${a}'`);
  await t.as(b);
  await db.exec(`insert into public.free_alerts(target_id) values ('${a}')`);
  await t.as(c);
  await db.exec(`insert into public.free_alerts(target_id) values ('${a}')`);
  await t.admin();
  await db.exec(`update public.free_alerts set expires_at = now() - interval '1 minute' where watcher_id = '${b}';
    insert into public.connection_preferences(connection_id, owner_id, share_schedule) values ('10000000-0000-0000-0000-000000000002', '${a}', false)`);
  await run();
  check((await rows(`select count(*)::int n from public.free_alerts`))[0].n === 0, 'Expired and unanswerable requests are removed');
  await db.exec(`delete from public.personal_status where user_id = '${a}'`);
  const [activity] = await rows(`insert into public.personal_schedule
    (user_id, day_of_week, start_time, end_time, title)
    values ('${b}', extract(isodow from now() at time zone 'UTC')::int, '00:00', '24:00', 'Actividad sincronizada')
    returning id`);
  await t.as(a);
  const [follow] = await rows(`insert into public.schedule_follow_requests(follower_id, leader_id)
    values ('${a}', '${b}') returning id`);
  await t.as(b);
  await db.exec(`update public.schedule_follow_requests set approved = true where id = '${follow.id}'`);
  await t.as(a);
  await db.exec(`insert into public.followed_schedule_activities(request_id, source_id) values ('${follow.id}', '${activity.id}')`);
  await t.as(b);
  await db.exec(`insert into public.free_alerts(target_id) values ('${a}')`);
  check((await run()).length === 0, 'A followed activity postpones the free alert');
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, share_schedule)
    select id, '${b}', false from public.person_connections where person_a = '${a}' and person_b = '${b}'`);
  check((await run()).length === 1, 'A hidden source schedule no longer blocks the free alert');
  console.log(`Free alerts: ${t.checks} checks passed.`);
} finally { await db.close(); }
