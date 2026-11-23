// node scripts/test_activity_attendance.mjs
// Attendance to activities: only the owner (or the follower of a followed activity) can mark it, never
// a future date, a manual mark wins over an automatic one, and deleting the activity forgets it.
import { freshDatabase, a, b } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const today = `(now() at time zone 'UTC')::date`;
const mark = (activity, date, status, source) =>
  rows(`select * from public.record_attendance('${activity}', ${date}, '${status}', '${source}')`);
try {
  await t.admin();
  const [own] = await rows(`insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title)
    values ('${a}', 1, '09:00', '11:00', 'Clase') returning id`);
  const [other] = await rows(`insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title)
    values ('${b}', 2, '09:00', '11:00', 'Turno') returning id`);

  await t.as(a);
  const [first] = await mark(own.id, today, 'present', 'auto');
  check(first.user_id === a && first.status === 'present' && first.source === 'auto', 'The owner marks an automatic attendance');
  check((await mark(own.id, today, 'absent', 'manual'))[0].status === 'absent', 'A manual mark replaces the automatic one');
  check((await mark(own.id, today, 'present', 'auto')).length === 0, 'An automatic mark never replaces a manual one');
  check((await rows(`select status from public.activity_attendance`))[0].status === 'absent', 'The manual mark is kept');
  check((await mark(own.id, `${today} - 3`, 'present', 'manual')).length === 1, 'Past dates can be marked by hand');
  await t.rejects(mark(own.id, `${today} + 1`, 'present', 'manual'), 'FM004', 'A future date is rejected');
  await t.rejects(mark(own.id, `${today} - 400`, 'present', 'manual'), 'FM004', 'A date more than a year ago is rejected');
  await t.rejects(mark(own.id, today, 'late', 'manual'), 'FM004', 'Only present or absent');
  await t.rejects(mark(other.id, today, 'present', 'manual'), '42501', 'Nobody marks the activity of another person');
  await t.rejects(db.exec(`insert into public.activity_attendance(schedule_id, occurrence_date, status, source)
    values ('${own.id}', ${today} - 1, 'present', 'manual')`), '42501', 'Rows are written only through record_attendance');

  await t.as(b);
  check((await rows(`select * from public.activity_attendance`)).length === 0, 'Attendance is private to its owner');
  await db.exec(`delete from public.activity_attendance`);
  await t.as(a);
  check((await rows(`select * from public.activity_attendance`)).length === 2, 'Another person cannot delete it');

  // A followed activity: B follows A's class and marks attendance with the followed id.
  await t.admin();
  const [request] = await rows(`insert into public.schedule_follow_requests(follower_id, leader_id, approved)
    values ('${b}', '${a}', true) returning id`);
  const [followed] = await rows(`insert into public.followed_schedule_activities(request_id, source_id)
    values ('${request.id}', '${own.id}') returning id`);
  await t.as(b);
  check((await mark(followed.id, today, 'present', 'auto')).length === 1, 'A follower marks a followed activity');
  await t.as(a);
  await t.rejects(mark(followed.id, today, 'present', 'manual'), '42501', 'The leader cannot mark the follower copy');

  // Deleting the activity forgets its attendance, also that of the people who followed it.
  await t.admin();
  await db.exec(`delete from public.personal_schedule where id = '${own.id}'`);
  check((await rows(`select count(*)::int n from public.activity_attendance`))[0].n === 0, 'Deleting the activity removes its attendance');
  console.log(`Activity attendance: ${t.checks} checks passed.`);
} finally { await db.close(); }
