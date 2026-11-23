import { freshDatabase, a, b, c, ab } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
const monday = "((date_trunc('week', now() at time zone 'UTC')::date) + 7)";
const nextDay = `(${monday} + 1)`;
const request = async () => (await rows(`insert into public.schedule_follow_requests(follower_id, leader_id)
    values ('${a}', '${b}') returning id`))[0].id;
const select = (id, source) => rows(`insert into public.followed_schedule_activities(request_id, source_id)
    values ('${id}', '${source}') returning id`);

try {
  await t.as(b);
  const [lesson, other] = await rows(`insert into public.personal_schedule
      (user_id, day_of_week, start_time, end_time, title) values
      ('${b}', 1, '09:00', '10:00', 'Clase'),
      ('${b}', 1, '11:00', '12:00', 'Trabajo') returning id`);
  await t.as(a);
  const id = await request();
  check((await rows(`update public.schedule_follow_requests set approved = true where id = '${id}' returning id`)).length === 0,
    'Follower cannot approve their own request');
  await t.rejects(select(id, lesson.id), '42501', 'Selection requires approval');
  await t.as(b);
  await db.exec(`update public.schedule_follow_requests set approved = true where id = '${id}'`);
  await t.as(a);
  const [chosen] = await select(id, lesson.id);
  const effective = async () => (await rows(`select * from public.effective_personal_schedule where user_id = '${a}'`));
  check((await effective()).length === 1, 'Only selected activities enter the follower schedule');
  await t.rejects(db.exec(`update public.followed_schedule_activities
      set customization = '{"day_of_week":0}' where id = '${chosen.id}'`),
    'FM004', 'Invalid customization cannot corrupt the effective schedule');
  await db.exec(`update public.followed_schedule_activities
      set customization = '{"location":"Mi aula"}' where id = '${chosen.id}'`);
  check((await effective())[0].location === 'Mi aula', 'The follower can personalize a selected activity');
  await t.as(b);
  await db.exec(`update public.personal_schedule set title = 'Clase actualizada' where id = '${lesson.id}'`);
  await t.as(a);
  check((await effective())[0].title === 'Clase actualizada' && (await effective())[0].location === 'Mi aula',
    'Changes to unedited fields still propagate');
  await t.as(c);
  check((await effective()).length === 0, 'A contact of the follower cannot see the source activity');
  await t.as(b);
  await db.exec(`select public.change_schedule_occurrence('${lesson.id}', ${monday}, 'cancel')`);
  await t.as(a);
  check(Object.values((await effective())[0].occurrence_exceptions)[0].cancelled, 'Cancellation flows through to the follower');
  await db.exec(`select public.change_followed_occurrence('${chosen.id}', ${monday},
      jsonb_build_object('date', ${nextDay}, 'start_time', '12:00', 'end_time', '13:00'))`);
  check(!Object.values((await effective())[0].occurrence_exceptions)[0].cancelled, 'Personal changes take priority on that date');
  const [availability] = await rows(`select viewer from public.shared_schedules_for_availability('${c}')`);
  check(availability.viewer.some(entry => entry.id === chosen.id), 'Followed activity affects the follower availability');
  await select(id, other.id);
  await db.exec(`select public.end_schedule_follow('${id}', false)`);
  const [copied] = await effective();
  check((await effective()).length === 1 && copied.id === chosen.id && copied.source_id === null,
    'Detaching with discard keeps the customized activity only');

  const nextRequest = await request();
  await t.as(b);
  await db.exec(`update public.schedule_follow_requests set approved = true where id = '${nextRequest}'`);
  await t.as(a);
  await select(nextRequest, lesson.id);
  await t.rejects(db.exec(`select public.end_schedule_follow('${nextRequest}', true)`), '23P01',
    'A copy with an overlap fails without discarding the request');
  await t.as(b);
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, share_schedule)
    values ('${ab}', '${b}', false)`);
  await t.as(a);
  await t.rejects(db.exec(`select public.end_schedule_follow('${nextRequest}', true)`), 'FM006',
    'Copying fails explicitly after sharing is revoked');
  check((await rows(`select id from public.schedule_follow_requests where id = '${nextRequest}'`)).length === 1,
    'Failed copying leaves the follow request intact');
  await t.as(b);
  await db.exec(`delete from public.person_connections where id = '${ab}'`);
  await t.as(a);
  check((await rows('select * from public.schedule_follow_requests')).length === 0, 'Disconnecting removes the request and selection');
  check((await effective()).length === 1, 'Disconnecting leaves personal activities intact');
  console.log(`Schedule following: ${t.checks} checks passed.`);
} finally {
  await db.close();
}
