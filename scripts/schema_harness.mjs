// Shared setup for the schema tests: an embedded PostgreSQL with every migration applied in order,
// four people (A is connected with B, C and D) and local stand-ins for Vault and pg_net that record
// each push request. Never connects to the remote project.
// npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { btree_gist } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

export const a = '00000000-0000-0000-0000-000000000001';
export const b = '00000000-0000-0000-0000-000000000002';
export const c = '00000000-0000-0000-0000-000000000003';
export const d = '00000000-0000-0000-0000-000000000004';
export const ab = '10000000-0000-0000-0000-000000000001';
export const ac = '10000000-0000-0000-0000-000000000002';
export const ad = '10000000-0000-0000-0000-000000000003';

export async function freshDatabase() {
  const db = new PGlite({ extensions: { btree_gist } });
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin;
    create schema auth; create schema extensions;
    create table auth.users(id uuid primary key, email text unique, raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth to anon, authenticated, supabase_auth_admin;
    grant insert on auth.users to supabase_auth_admin;`);
  const files = (await readdir(new URL('../supabase/migrations/', import.meta.url))).filter((n) => n.endsWith('.sql')).sort();
  for (const name of files) {
    if (name.includes('_programar_')) continue; // pg_cron / pg_net schedules
    await db.exec(await readFile(new URL(`../supabase/migrations/${name}`, import.meta.url), 'utf8'));
  }
  await db.exec(`set session_replication_role = replica;
    insert into auth.users(id) values ('${a}'),('${b}'),('${c}'),('${d}');
    set session_replication_role = origin;
    insert into public.account_profiles(id, display_name, timezone)
      values ('${a}','Ana','UTC'),('${b}','Beto','UTC'),('${c}','Caro','UTC'),('${d}','Dani','UTC');
    insert into public.person_connections(id, person_a, person_b) values ('${ab}','${a}','${b}'),('${ac}','${a}','${c}'),('${ad}','${a}','${d}');
    create schema vault; create table vault.decrypted_secrets(name text, decrypted_secret text);
    insert into vault.decrypted_secrets values ('near_push_url','https://example.test/functions/v1/send-push'),('near_push_secret','s3cret');
    create schema net; create table public.net_calls(n serial, url text, body jsonb, headers jsonb);
    create function net.http_post(url text, body jsonb, headers jsonb, timeout_milliseconds int) returns bigint
      language sql as $$ insert into public.net_calls(url, body, headers) values (url, body, headers) returning n $$;`);
  let checks = 0;
  return {
    db,
    rows: async (sql) => (await db.query(sql)).rows,
    check(condition, name) { assert.ok(condition, name); checks++; },
    async rejects(promise, code, name) { await assert.rejects(promise, { code }, name); checks++; },
    get checks() { return checks; },
    async as(id) {
      await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${id}', false);`);
    },
    async admin() { await db.exec('reset role;'); },
    /** Push requests since the last call, as { type, recipients (sorted), data }. */
    async sent() {
      await db.exec('reset role;');
      const calls = (await db.query(`select body from public.net_calls order by n`)).rows;
      await db.exec('truncate public.net_calls;');
      return calls.map(({ body }) => ({ ...body, recipients: [...body.recipients].sort() }));
    },
  };
}
