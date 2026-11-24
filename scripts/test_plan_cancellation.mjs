// node scripts/test_plan_cancellation.mjs
import { readFile } from 'node:fs/promises';
import { freshDatabase, a, b, c } from './schema_harness.mjs';

const migration = '20261124154439_cancelar_planes_aceptados.sql';
const t = await freshDatabase();
const { db, rows, check } = t;
let day = 1;
async function propose() {
  await t.as(a);
  return (await rows(`insert into public.plan_proposals(recipient_id,title,starts_at,ends_at)
    values ('${b}','Vernos',date_trunc('day',now())+interval '${day} days 18 hours',
    date_trunc('day',now())+interval '${day++} days 19 hours') returning *`))[0];
}
async function accept() {
  const p = await propose();
  await t.as(b);
  return (await rows(`select * from public.answer_plan('${p.id}',true)`))[0];
}
try {
  const p = await accept();
  check(p.schedule_links_ready && p.sender_schedule_id && p.recipient_schedule_id,
    'Acceptance records both exact activity ids');
  await t.as(c);
  await t.rejects(db.query(`select * from public.cancel_plan('${p.id}')`), '42501', 'An outsider cannot cancel another pair plan');
  await t.as(a);
  await t.rejects(db.exec(`update public.plan_proposals set sender_schedule_id=recipient_schedule_id where id='${p.id}'`),
    '42501', 'Clients cannot forge the activity links');
  await db.exec(`update public.personal_schedule set title='Vernos editado' where id='${p.sender_schedule_id}'`);
  const [unrelated] = await rows(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title,location,category,one_off_date)
    values ('${a}',extract(isodow from current_date+10)::int,'18:00','19:00','Vernos editado','','free',current_date+10) returning id`);
  await t.as(b);
  await db.exec(`delete from public.personal_schedule where id='${p.recipient_schedule_id}'`);
  await t.sent();
  await t.as(b);
  const [cancelled] = await rows(`select * from public.cancel_plan('${p.id}')`);
  check(cancelled.status === 'cancelled' && cancelled.cancelled_by === b, 'Either participant can cancel an accepted plan');
  await t.admin();
  check((await rows(`select id from public.personal_schedule where id in ('${p.sender_schedule_id}','${p.recipient_schedule_id}')`)).length === 0,
    'Cancelling removes the remaining copy even after edits and a prior local deletion');
  check((await rows(`select id from public.personal_schedule where id='${unrelated.id}'`)).length === 1,
    'An unrelated activity with the same title is preserved');
  let calls = (await t.sent()).filter(x => x.type === 'plan_cancelled');
  check(calls.length === 1 && calls[0].recipients.join() === a && calls[0].data.user_id === b && calls[0].data.was_accepted,
    'Only the other participant receives the cancellation with its true author');
  await t.as(b);
  await rows(`select * from public.cancel_plan('${p.id}')`);
  check((await t.sent()).length === 0, 'A retry neither repeats the alert nor deletes other activities');
  await t.as(a);
  await t.rejects(db.exec(`insert into public.personal_schedule(id,user_id,day_of_week,start_time,end_time,title,location,category,one_off_date)
    values ('${p.sender_schedule_id}','${a}',extract(isodow from current_date+1)::int,'18:00','19:00','Vernos','','free',current_date+1)`),
    'FM004', 'A delayed local save cannot resurrect the cancelled copy');

  const atomic = await accept();
  await t.admin();
  await db.exec(`update public.plan_proposals set schedule_links_ready=false where id='${atomic.id}'`);
  await t.as(a);
  await t.rejects(db.query(`select * from public.cancel_plan('${atomic.id}')`), 'FM008',
    'An ambiguous legacy plan cannot delete activities by guessing');
  await t.admin();
  check((await rows(`select id from public.personal_schedule where id in ('${atomic.sender_schedule_id}','${atomic.recipient_schedule_id}')`)).length === 2,
    'Unlinked copies are preserved for review');
  await db.exec(`update public.plan_proposals set schedule_links_ready=true where id='${atomic.id}'`);
  await db.exec(`create function public.reject_test_plan_delete() returns trigger language plpgsql as $$
    begin if old.user_id='${b}' then raise exception 'Test deletion failure'; end if; return old; end; $$;
    create trigger test_plan_delete before delete on public.personal_schedule for each row execute function public.reject_test_plan_delete()`);
  await t.as(a);
  await t.rejects(db.query(`select * from public.cancel_plan('${atomic.id}')`), 'P0001', 'A failure in one deletion aborts cancellation');
  await t.admin();
  check((await rows(`select id from public.personal_schedule where id in ('${atomic.sender_schedule_id}','${atomic.recipient_schedule_id}')`)).length === 2,
    'Both copies remain when cancellation fails');
  check((await rows(`select status from public.plan_proposals where id='${atomic.id}'`))[0].status === 'accepted',
    'A failed cancellation keeps the accepted status');
  await db.exec(`drop trigger test_plan_delete on public.personal_schedule; drop function public.reject_test_plan_delete()`);
  await t.as(a);
  await rows(`select * from public.cancel_plan('${atomic.id}')`);
  check((await rows(`select status from public.plan_proposals where id='${atomic.id}'`))[0].status === 'cancelled',
    'The organizer can cancel after the temporary failure is resolved');

  const pending = await propose();
  await t.as(b);
  await t.rejects(db.query(`select * from public.cancel_plan('${pending.id}')`), 'FM004', 'A recipient declines pending proposals rather than cancelling them');
  await t.as(a);
  await rows(`select * from public.cancel_plan('${pending.id}')`);
  check((await rows(`select status from public.plan_proposals where id='${pending.id}'`))[0].status === 'cancelled',
    'Pending proposals can still be withdrawn');
  console.log(`Plan cancellation: ${t.checks} checks passed.`);
} finally { await db.close(); }

// Upgrade an existing database: one participant already removed their local copy.
const legacy = await freshDatabase({ skipMigrations: [migration] });
try {
  await legacy.db.exec(`update public.account_profiles set timezone='America/Lima' where id='${b}'`);
  await legacy.as(a);
  const [p] = await legacy.rows(`insert into public.plan_proposals(recipient_id,title,starts_at,ends_at)
    values ('${b}','Plan anterior',date_trunc('day',now())+interval '1 day 23 hours 30 minutes',
    date_trunc('day',now())+interval '2 days 30 minutes') returning id`);
  await legacy.as(b);
  await legacy.rows(`select * from public.answer_plan('${p.id}',true)`);
  await legacy.db.exec(`delete from public.personal_schedule where user_id='${b}'`);
  await legacy.admin();
  await legacy.db.exec(await readFile(new URL(`../supabase/migrations/${migration}`, import.meta.url), 'utf8'));
  const [linked] = await legacy.rows(`select * from public.plan_proposals where id='${p.id}'`);
  legacy.check(linked.schedule_links_ready && linked.sender_schedule_id && linked.recipient_schedule_id === null,
    'The migration reconnects an existing plan even when one copy was already removed');
  await legacy.as(b);
  await legacy.rows(`select * from public.cancel_plan('${p.id}')`);
  await legacy.admin();
  legacy.check((await legacy.rows(`select id from public.personal_schedule`)).length === 0,
    'Cancellation removes the other participant remaining legacy copy');
  console.log(`Legacy plan cancellation: ${legacy.checks} checks passed.`);
} finally { await legacy.db.close(); }
