import {PGlite} from '../.verify139/node_modules/@electric-sql/pglite/dist/index.js';import fs from 'node:fs';import assert from 'node:assert/strict';
const db=new PGlite();try{
await db.exec(`create role anon;create role authenticated;create role service_role;create schema auth;create function auth.jwt() returns jsonb language sql as $$select jsonb_build_object('role',current_setting('request.jwt.role',true))$$;create schema vault;create table vault.secrets(name text,decrypted_secret text);create view vault.decrypted_secrets as select * from vault.secrets;create function vault.create_secret(s text,n text) returns void language sql as $$insert into vault.secrets values(n,s)$$;`);
const sql=fs.readFileSync('app140/backend/supabase/v140/configure_worker.sql','utf8');await db.exec(sql);await db.exec(sql);
assert.equal((await db.query('select count(*)::int as n from vault.secrets')).rows[0].n,2);
assert.equal((await db.query("select has_function_privilege('anon','public.task_worker_authorized(text)','execute') as a,has_function_privilege('authenticated','public.task_worker_authorized(text)','execute') as u,has_function_privilege('service_role','public.task_worker_authorized(text)','execute') as s")).rows[0].a,false);
const perms=(await db.query("select has_function_privilege('authenticated','public.task_worker_authorized(text)','execute') as u,has_function_privilege('service_role','public.task_worker_authorized(text)','execute') as s")).rows[0];assert.equal(perms.u,false);assert.equal(perms.s,true);
await db.exec("select set_config('request.jwt.role','service_role',false)");
assert.equal((await db.query("select task_worker_authorized((select decrypted_secret from vault.secrets where name='task_notification_secret')) as ok")).rows[0].ok,true);
assert.equal((await db.query("select task_worker_authorized(repeat('x',64)) as ok")).rows[0].ok,false);
await db.exec("select set_config('request.jwt.role','authenticated',false)");assert.equal((await db.query("select task_worker_authorized((select decrypted_secret from vault.secrets where name='task_notification_secret')) as ok")).rows[0].ok,false);
console.log('7 worker SQL assertions passed: idempotency, role grants, correct/wrong secret, role check.');
}finally{await db.close()}
