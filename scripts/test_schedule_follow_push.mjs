// node scripts/test_schedule_follow_push.mjs
// Real trigger execution against local PostgreSQL; pg_net only records requests, never sends them.
import { freshDatabase, a, b, c, ab } from './schema_harness.mjs';

const t = await freshDatabase();
const { db, rows, check } = t;
try {
  await t.as(a);
  const [{ id }] = await rows(`insert into public.schedule_follow_requests(follower_id,leader_id)
    values ('${a}','${b}') returning id`);
  let calls = await t.sent();
  check(calls.length === 1 && calls[0].type === 'schedule_follow', 'A new synchronization request queues a push');
  check(calls[0].recipients.join() === b && calls[0].data.user_id === a && calls[0].data.id === id,
    'Only the schedule owner receives the request, with the actual follower and request id');
  check(calls[0].data.name === 'Ana', 'The requester name comes from the profile');

  await t.as(a);
  check((await rows(`update public.schedule_follow_requests set approved=true where id='${id}' returning id`)).length === 0,
    'A follower cannot approve their own request');
  check((await t.sent()).length === 0, 'A rejected update sends nothing');
  await t.as(c);
  await t.rejects(db.exec(`insert into public.schedule_follow_requests(follower_id,leader_id)
    values ('${a}','${c}')`), '42501', 'A third party cannot impersonate a requester');
  check((await t.sent()).length === 0, 'Impersonation cannot generate pushes');

  await t.as(b);
  await db.exec(`update public.schedule_follow_requests set approved=true where id='${id}'`);
  calls = await t.sent();
  check(calls.length === 1 && calls[0].type === 'schedule_follow_answer' && calls[0].recipients.join() === a,
    'Approval notifies only the follower who requested it');
  check(calls[0].data.user_id === b && calls[0].data.name === 'Beto', 'The approval names the owner of the source schedule');
  await db.exec(`update public.schedule_follow_requests set approved=true where id='${id}'`);
  check((await t.sent()).length === 0, 'Saving an already approved request does not repeat the alert');

  await db.exec(`delete from public.schedule_follow_requests where id='${id}';
    insert into public.connection_preferences(connection_id,owner_id,muted) values ('${ab}','${b}',true)`);
  await t.as(a);
  const [{ id: muted }] = await rows(`insert into public.schedule_follow_requests(follower_id,leader_id)
    values ('${a}','${b}') returning id`);
  check((await t.sent()).length === 0, 'A silenced person cannot cause an incoming request alert');
  await t.as(b);
  await db.exec(`update public.schedule_follow_requests set approved=true where id='${muted}'`);
  check((await t.sent()).some((call) => call.type === 'schedule_follow_answer' && call.recipients.join() === a),
    'The requester still hears the answer they explicitly asked for');
  const [permissions] = await rows(`select
    has_function_privilege('anon','private.push_on_schedule_follow()','execute') as anon,
    has_function_privilege('authenticated','private.push_on_schedule_follow()','execute') as authenticated`);
  check(!permissions.anon && !permissions.authenticated, 'The privileged trigger cannot be called by API roles');
  console.log(`Schedule follow push: ${t.checks} checks passed.`);
} finally { await db.close(); }
