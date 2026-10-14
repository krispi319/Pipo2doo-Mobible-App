'use strict';
// MUST point at local Auth + Functions + Firestore emulators, never production.
const test=require('node:test');const assert=require('node:assert/strict');
const host=process.env.FIREBASE_AUTH_EMULATOR_HOST;
const url=process.env.PIPO_CALLABLE_EMULATOR_URL;
const enabled=Boolean(host&&url&&/localhost|127\.0\.0\.1/.test(url));
async function user(){const r=await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({returnSecureToken:true})});const d=await r.json();assert.ok(d.idToken);return d;}
async function call(u,action,data={}){const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',...(u?{Authorization:`Bearer ${u.idToken}`}:{})},body:JSON.stringify({data:{action,...data}})});return r.json();}
test('Emulator auth, concurrent join, version, health consent and unpair regression',{skip:!enabled},async()=>{
 const [a,b,c]=await Promise.all([user(),user(),user()]);assert.ok((await call(null,'snapshot')).error);
 const created=(await call(a,'create')).result;assert.ok(created.code);
 const joins=await Promise.all([call(b,'join',{code:created.code}),call(c,'join',{code:created.code})]);assert.equal(joins.filter(v=>v.result).length,1);
 const partner=joins[0].result?b:c,outsider=joins[0].result?c:b;
 assert.ok((await call(outsider,'snapshot')).error);
 const list=(await call(a,'saveList',{id:'list_a',expectedVersion:0,value:{name:'Shared list',emoji:'🧺'}})).result;assert.equal(list.version,1);
 const conflicting=await Promise.all([call(a,'saveList',{id:'list_a',expectedVersion:1,value:{name:'One',emoji:'🧺'}}),call(partner,'saveList',{id:'list_a',expectedVersion:1,value:{name:'Two',emoji:'🧺'}})]);assert.equal(conflicting.filter(v=>v.result).length,1);
 assert.ok((await call(partner,'partnerHealth')).error);
 const consent=(await call(a,'consent',{categories:['Mood'],history:true,fromDay:20000,notesOptIn:false})).result;assert.ok(consent.revision);
 assert.ok((await call(a,'uploadHealth',{day:20000,revision:consent.revision,fields:{mood:3,note:'Never shared'}})).error);
 assert.ok((await call(a,'uploadHealth',{day:20000,revision:consent.revision,fields:{mood:3}})).result);
 const read=(await call(partner,'partnerHealth')).result;assert.equal(read.entries[0].fields.mood,3);
 assert.ok((await call(partner,'uploadHealth',{day:20000,revision:consent.revision,fields:{mood:0}})).error);
 await call(a,'revoke');assert.ok((await call(partner,'partnerHealth')).error);assert.ok((await call(a,'uploadHealth',{day:20000,revision:consent.revision,fields:{mood:1}})).error);
 await call(a,'leave');assert.ok((await call(a,'snapshot')).error);assert.ok((await call(partner,'partnerHealth')).error);
});
