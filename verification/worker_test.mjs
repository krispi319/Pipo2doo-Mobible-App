import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
const js=fs.readFileSync('.verify140/worker-js/index.js','utf8').replace(/^export \{\};?$/m,'');
let passed=0;
async function test(env,request,status,network=0,authorized=true){let handler,calls=0;vm.runInNewContext(js,{Response,Request,TextEncoder,Uint8Array,URLSearchParams,AbortSignal,crypto:globalThis.crypto,btoa,atob,Deno:{env:{get:k=>env[k]},serve:fn=>handler=fn},fetch:async(url)=>{calls++;assert.ok(url.endsWith('/task_worker_authorized'));return new Response(JSON.stringify(authorized));}});let r=await handler(new Request('https://test',request));assert.equal(r.status,status);assert.equal(calls,network);passed++;}
const post={method:'POST',headers:{'X-Task-Secret':'a'.repeat(64),'X-Pipo-Health':'1'}};
const env={SUPABASE_URL:'https://project.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'server-only',FCM_SERVICE_ACCOUNT_JSON:JSON.stringify({type:'service_account',project_id:'test-project',client_email:'test@test',private_key:'test'})};
await test({}, {method:'GET'},405);await test({}, {method:'POST'},401);await test({},post,503);
await test({...env,TASK_NOTIFICATION_SECRET:'b'.repeat(64)},post,401);
await test({...env,TASK_NOTIFICATION_SECRET:'a'.repeat(64)},post,200);
await test(env,post,200,1,true);await test(env,post,401,1,false);
await test({...env,FCM_SERVICE_ACCOUNT_JSON:'bad'},post,503,1,true);
console.log(`${passed} worker authorization/configuration/health tests passed; health never sends FCM or claims jobs.`);
