// node scripts/test_plan_proposals.mjs
// Plans: who can propose and answer, the activity added to both schedules in each zone, clashes
// and the pushes for the proposal and the answer.
import { freshDatabase, a, b, c, ac } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
// Tomorrow at 18:00 UTC, one hour.
const start = `(date_trunc('day', now()) + interval '1 day 18 hours')`;
const propose = (to, title = 'Vernos', from = start, minutes = 60) => db.query(`insert into public.plan_proposals
  (recipient_id, title, starts_at, ends_at) values ('${to}', '${title}', ${from}, ${from} + interval '${minutes} minutes') returning id`);
try {
  await db.exec(`update public.account_profiles set timezone = 'America/Lima' where id = '${b}'`);
  await t.as(a);
  const [{ id: plan }] = (await propose(b)).rows;
  check((await rows(`select sender_id from public.plan_proposals`))[0].sender_id === a, 'The sender comes from the session');
  let calls = (await t.sent()).filter((call) => call.type === 'plan');
  check(calls.length === 1 && calls[0].recipients.join() === b && calls[0].data.name === 'Ana' && calls[0].data.title === 'Vernos',
    'The recipient gets a push with the plan');

  await t.as(a);
  await t.rejects(propose(b, 'Ayer', `(now() - interval '1 hour')`), 'FM004', 'A plan in the past is rejected');
  await t.rejects(propose(b, 'Larguísimo', start, 13 * 60), '23514', 'A plan lasts at most twelve hours');
  await t.rejects(db.exec(`insert into public.plan_proposals(sender_id, recipient_id, title, starts_at, ends_at)
    values ('${b}', '${a}', 'Suplantado', ${start}, ${start} + interval '1 hour')`), '42501', 'Nobody proposes on behalf of another person');
  await t.as(b);
  await t.rejects(propose('00000000-0000-0000-0000-000000000003'), '42501', 'Only connected people can receive a plan');
  await t.as(c);
  check((await rows(`select * from public.plan_proposals`)).length === 0, 'Other people do not see the plan');
  await t.rejects(db.query(`select * from public.answer_plan('${plan}', true)`), '42501', 'Only the recipient answers');
  await t.as(a);
  await t.rejects(db.query(`select * from public.answer_plan('${plan}', true)`), '42501', 'The sender cannot accept it');
  await t.rejects(db.exec(`update public.plan_proposals set status = 'accepted'`), '42501', 'Nobody changes the status directly');

  // B accepts: both get a one-off activity, each in their own zone.
  await t.as(b);
  const [answered] = (await db.query(`select status from public.answer_plan('${plan}', true)`)).rows;
  check(answered.status === 'accepted', 'The plan is accepted');
  await t.admin();
  const added = await rows(`select s.user_id, s.start_time::text start, s.one_off_date::text date, s.category
    from public.personal_schedule s order by s.user_id`);
  check(added.length === 2 && added.every((row) => row.category === 'free'), 'Both schedules get the plan');
  check(added.find((row) => row.user_id === a).start === '18:00:00', 'At 18:00 for the sender, in UTC');
  check(added.find((row) => row.user_id === b).start === '13:00:00', 'At 13:00 for the recipient, in Lima');
  calls = (await t.sent()).filter((call) => call.type === 'plan_answer');
  check(calls.length === 1 && calls[0].recipients.join() === a && calls[0].data.accepted === true, 'The sender hears the answer');
  await t.as(b);
  await t.rejects(db.query(`select * from public.answer_plan('${plan}', false)`), 'FM004', 'A plan is answered once');

  // Clashes: the same hour again clashes in both schedules; the sender's clash has its own code.
  await t.as(a);
  const [{ id: again }] = (await propose(b, 'Otra vez')).rows;
  await t.admin();
  await db.exec(`delete from public.personal_schedule where user_id = '${b}'`);
  await t.as(b);
  await t.rejects(db.query(`select * from public.answer_plan('${again}', true)`), 'FM005', 'A clash in the sender schedule is FM005');
  await t.admin();
  check((await rows(`select count(*)::int n from public.personal_schedule where user_id = '${b}'`))[0].n === 0,
    'Nothing is added when the plan cannot be accepted');
  await t.as(b);
  const [declined] = (await db.query(`select status from public.answer_plan('${again}', false)`)).rows;
  check(declined.status === 'declined', 'Declining is always possible');
  check((await t.sent()).some((call) => call.type === 'plan_answer' && call.data.accepted === false), 'The sender hears the refusal');

  // Cancelling, and the silenced sender.
  await t.as(a);
  const [{ id: third }] = (await propose(b, 'Tercero', `(${start} + interval '3 hours')`)).rows;
  check((await db.query(`select status from public.cancel_plan('${third}')`)).rows[0].status === 'cancelled', 'The sender cancels a pending plan');
  check((await db.query(`select status from public.cancel_plan('${plan}')`)).rows[0].status === 'cancelled',
    'An accepted plan can also be cancelled');
  await t.admin();
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, muted) values ('${ac}', '${c}', true)`);
  await t.sent();
  await t.as(a);
  await propose(c, 'Silenciado');
  check((await t.sent()).filter((call) => call.type === 'plan').length === 0, 'A silenced sender does not notify');
  await t.as(a);
  const [{ id: disconnected }] = (await propose(b, 'Conexión terminada')).rows;
  await db.exec(`delete from public.person_connections where person_a = '${a}' and person_b = '${b}'`);
  check((await rows(`select status from public.plan_proposals where id = '${disconnected}'`))[0].status === 'cancelled',
    'Disconnect revokes pending proposals');
  await t.as(b);
  await t.rejects(db.query(`select * from public.answer_plan('${disconnected}', true)`), 'FM007',
    'A disconnected recipient cannot add a pending plan to either schedule');
  check((await rows(`select count(*)::int n from public.personal_schedule where user_id = '${b}'`))[0].n === 0,
    'No activity is created upon accepting after disconnecting');
  console.log(`Plan proposals: ${t.checks} checks passed.`);
} finally { await db.close(); }
