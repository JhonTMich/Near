// node scripts/test_schedule_pauses.mjs
// Pauses of the weekly schedule: who can read and write them, their limits, the availability
// snapshot and the activity pushes on paused dates.
import { freshDatabase, a, b, c, d } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const today = `(now() at time zone 'UTC')::date`;
try {
  await t.as(a);
  await db.exec(`insert into public.schedule_pauses(title, start_date, end_date) values ('Vacaciones', ${today}, ${today} + 6)`);
  check((await rows(`select user_id from public.schedule_pauses`))[0].user_id === a, 'The owner is filled in from the session');
  await t.rejects(db.exec(`insert into public.schedule_pauses(user_id, title, start_date, end_date) values ('${b}', 'Ajena', ${today}, ${today})`),
    '42501', 'Nobody pauses another person');
  await t.rejects(db.exec(`insert into public.schedule_pauses(title, start_date, end_date) values ('Pasada', ${today} - 9, ${today} - 2)`),
    'FM004', 'A pause that already ended is rejected');
  await t.rejects(db.exec(`insert into public.schedule_pauses(title, start_date, end_date) values ('Lejana', ${today} + 400, ${today} + 401)`),
    'FM004', 'A pause more than a year ahead is rejected');
  await t.rejects(db.exec(`insert into public.schedule_pauses(title, start_date, end_date) values ('Al revés', ${today} + 2, ${today} + 1)`),
    '23514', 'The end cannot be before the start');
  await t.rejects(db.exec(`update public.schedule_pauses set title = 'Otro'`), '42501', 'Pauses are replaced, not edited');

  // B sees it (shares the schedule), D does not (A does not share with D), C sees it too.
  await t.admin();
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, share_schedule)
    values ('10000000-0000-0000-0000-000000000003', '${a}', false)`);
  await t.as(b);
  check((await rows(`select title from public.schedule_pauses`)).length === 1, 'A connection that sees the schedule sees the pause');
  await t.as(d);
  check((await rows(`select title from public.schedule_pauses`)).length === 0, 'Someone the schedule is not shared with does not');
  await t.rejects(db.exec(`delete from public.schedule_pauses where user_id = '${a}'`).then(async () => {
    await t.admin();
    if ((await rows(`select count(*)::int n from public.schedule_pauses`))[0].n !== 1) throw new Error('deleted');
    const error = new Error('blocked'); error.code = 'NOOP'; throw error;
  }), 'NOOP', 'Another person cannot delete the pause');

  await t.as(b);
  const [pair] = await rows(`select * from public.shared_schedules_for_availability('${a}')`);
  check(pair.contact_pauses.length === 1 && pair.contact_pauses[0].title === 'Vacaciones', 'The availability snapshot carries the contact pauses');
  check(Array.isArray(pair.viewer_pauses) && pair.viewer_pauses.length === 0, 'And the viewer pauses');

  // Activity pushes: a regular occurrence on a paused date is not announced.
  await t.admin();
  await db.exec(`insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title)
    select '${a}', extract(isodow from now() at time zone 'UTC')::int,
           date_trunc('minute', now() at time zone 'UTC')::time,
           (date_trunc('minute', now() at time zone 'UTC') + interval '1 minute')::time, 'Clase'
    where (date_trunc('minute', now() at time zone 'UTC') + interval '1 minute')::time
        > date_trunc('minute', now() at time zone 'UTC')::time;`);
  const scheduled = (await rows(`select count(*)::int n from public.personal_schedule`))[0].n;
  await t.sent();
  await db.exec(`select private.push_due_events();`);
  if (scheduled) {
    check((await t.sent()).filter((call) => call.type === 'activity').length === 0, 'No activity push on a paused date');
    await db.exec(`delete from public.schedule_pauses`);
    await db.exec(`select private.push_due_events();`);
    check((await t.sent()).some((call) => call.type === 'activity' && call.recipients.includes(b)), 'Without the pause the push is sent');
  }

  // Limit: twenty pauses per person.
  await t.as(a);
  await db.exec(`insert into public.schedule_pauses(title, start_date, end_date) select 'Feriado ' || n, ${today} + n, ${today} + n from generate_series(1, 21) n`)
    .then(() => { throw Object.assign(new Error('no limit'), { code: 'NONE' }); })
    .catch((error) => check(error.code === 'FM001', 'More than twenty pauses are rejected'));
  void c;
  console.log(`Schedule pauses: ${t.checks} checks passed.`);
} finally { await db.close(); }
