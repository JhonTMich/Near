// node scripts/test_one_off_activities.mjs
// Activities on a single date: dates, overlaps with weekly and other one-off activities,
// exceptions, pauses and activity pushes.
import { freshDatabase, a, b } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const day = (offset) => `((now() at time zone 'UTC')::date + ${offset})`;
const insert = (offset, start, end, title, extra = '') => db.exec(`insert into public.personal_schedule
  (user_id, day_of_week, start_time, end_time, title, one_off_date${extra ? ', ' + extra.split('=')[0] : ''})
  values ('${a}', extract(isodow from ${day(offset)})::int, '${start}', '${end}', '${title}', ${day(offset)}${extra ? ', ' + extra.split('=')[1] : ''})`);
try {
  await t.as(a);
  await insert(7, '10:00', '11:00', 'Médico');
  check((await rows(`select count(*)::int n from public.personal_schedule where one_off_date is not null`))[0].n === 1, 'A one-off activity is saved');
  await t.rejects(db.exec(`insert into public.personal_schedule (user_id, day_of_week, start_time, end_time, title, one_off_date)
    values ('${a}', (extract(isodow from ${day(7)})::int % 7) + 1, '12:00', '13:00', 'Día equivocado', ${day(7)})`),
    '23514', 'The weekday must match the date');
  await t.rejects(insert(-2, '10:00', '11:00', 'Pasada'), 'FM004', 'A date in the past is rejected');
  await t.rejects(insert(400, '10:00', '11:00', 'Lejana'), 'FM004', 'A date more than a year ahead is rejected');

  // Overlaps: the same date clashes; the same weekday a week later does not.
  await t.rejects(insert(7, '10:30', '11:30', 'Choca'), '23P01', 'Two one-off activities on the same date and hours clash');
  await insert(14, '10:30', '11:30', 'Otra semana');
  check(true, 'The same hours a week later are free');
  await t.rejects(db.exec(`insert into public.personal_schedule (user_id, day_of_week, start_time, end_time, title)
    values ('${a}', extract(isodow from ${day(7)})::int, '09:30', '10:30', 'Semanal que choca')`),
    '23P01', 'A weekly activity cannot overlap an upcoming one-off activity');
  await db.exec(`insert into public.personal_schedule (user_id, day_of_week, start_time, end_time, title)
    values ('${a}', extract(isodow from ${day(7)})::int, '08:00', '09:00', 'Semanal antes')`);
  await t.rejects(insert(21, '08:30', '09:30', 'Choca con la semanal'), '23P01', 'A one-off activity cannot overlap a weekly one');
  await t.rejects(db.exec(`insert into public.personal_schedule (user_id, day_of_week, start_time, end_time, title, ends_next_day, one_off_date)
    values ('${a}', extract(isodow from ${day(20)})::int, '23:00', '08:30', 'Noche', true, ${day(20)})`),
    '23P01', 'An overnight one-off activity is checked against the next day');

  // Exceptions belong to weekly series only.
  const [{ id: medico }] = await rows(`select id from public.personal_schedule where title = 'Médico'`);
  await t.rejects(db.query(`select * from public.change_schedule_occurrence('${medico}', ${day(7)}, 'cancel')`),
    'FM004', 'A one-off activity cannot get a dated exception');

  // Old clients upsert without the column: the date is kept.
  await db.exec(`insert into public.personal_schedule (id, user_id, day_of_week, start_time, end_time, title)
    values ('${medico}', '${a}', extract(isodow from ${day(7)})::int, '10:00', '11:00', 'Médico (renombrado)')
    on conflict (id) do update set title = excluded.title`);
  check((await rows(`select one_off_date is not null kept from public.personal_schedule where id = '${medico}'`))[0].kept,
    'An upsert without one_off_date keeps the date');

  // The occurrence helper only returns the one-off activity on its date.
  await t.admin();
  const starts = async (offset) => (await rows(`select count(*)::int n from public.personal_schedule s
    cross join lateral private.schedule_starts_on(s, ${day(offset)}) o where s.id = '${medico}'`))[0].n;
  check(await starts(7) === 1 && await starts(14) === 0 && await starts(0) === 0, 'It occurs on its date and nowhere else');

  // Pushes: a one-off activity starting now is announced even inside a pause.
  await t.admin();
  await db.exec(`delete from public.personal_schedule;
    insert into public.schedule_pauses(user_id, title, start_date, end_date) values ('${a}', 'Vacaciones', ${day(0)}, ${day(3)});
    insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title, one_off_date)
    select '${a}', extract(isodow from now() at time zone 'UTC')::int,
           date_trunc('minute', now() at time zone 'UTC')::time,
           (date_trunc('minute', now() at time zone 'UTC') + interval '1 minute')::time, 'Viaje', ${day(0)}
    where (date_trunc('minute', now() at time zone 'UTC') + interval '1 minute')::time
        > date_trunc('minute', now() at time zone 'UTC')::time;`);
  if ((await rows(`select count(*)::int n from public.personal_schedule`))[0].n) {
    await t.sent();
    await db.exec(`select private.push_due_events();`);
    check((await t.sent()).some((call) => call.type === 'activity' && call.data.title === 'Viaje' && call.recipients.includes(b)),
      'A one-off activity is announced during a pause');
  }
  console.log(`One-off activities: ${t.checks} checks passed.`);
} finally { await db.close(); }
