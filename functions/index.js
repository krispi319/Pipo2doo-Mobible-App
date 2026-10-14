'use strict';
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { getStorage } = require('firebase-admin/storage');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const crypto = require('node:crypto');
const P = require('./policies');
initializeApp();
const db = getFirestore();
let inviteKey;
// Server-only key, generated once transactionally. Deny-all rules prevent client access.
async function ensureKey() {
  if(inviteKey) return;
  inviteKey=await db.runTransaction(async tx=>{const rr=db.doc('_private/pairingKey'),sn=await tx.get(rr);if(sn.exists)return sn.data().key;const key=crypto.randomBytes(32).toString('hex');tx.create(rr,{key});return key;});
}
const ref = p => db.doc(p);
const uuid = () => crypto.randomUUID();
const hash = s => crypto.createHmac('sha256', inviteKey).update(s).digest('hex');
function auth(r) { if(!r.auth) P.fail('unauthenticated','Sign in first.'); return r.auth.uid; }
async function membership(tx, uid, paired=false) {
  const user=await tx.get(ref(`users/${uid}`)); const sid=user.data()?.spaceId;
  if(!sid) P.fail('failed-precondition','Create or join a space first.');
  const space=await tx.get(ref(`spaces/${sid}`)); const s=space.data();
  if(!s || s.active!==true || !s.members.includes(uid) || (paired && s.members.length!==2)) P.fail('permission-denied','Space unavailable.');
  return {sid,s,partner:s.members.find(x=>x!==uid)};
}
async function limit(uid, action, max, now, ip) {
  // UID and hashed network quotas survive rejected requests; no raw IP is stored/logged.
  const keys=[`uid_${uid}_${action}`,`net_${hash(ip||'unknown')}_${action}`];
  await db.runTransaction(async tx=> { const snaps=await tx.getAll(...keys.map(k=>ref(`limits/${k}`))); snaps.forEach((sn,i)=>{const old=sn.data();const same=old?.window===Math.floor(now/900000); const count=same?old.count:0; if(count >= (i===0?max:max*20)) P.fail('resource-exhausted','Please try again later.');});snaps.forEach((sn,i)=>{const old=sn.data();tx.set(ref(`limits/${keys[i]}`),{window:Math.floor(now/900000),count:(old?.window===Math.floor(now/900000)?old.count:0)+1});}); });
}
async function currentConsent(tx, uid, owner=uid) {
  const m=await membership(tx,uid,true); if(!m.s.members.includes(owner)) P.fail('permission-denied','Health view unavailable.');
  const c=(await tx.get(ref(`consents/${owner}`))).data();
  if(!c?.active || c.spaceId!==m.sid || c.generation!==m.s.generation || c.recipient!==m.s.members.find(v=>v!==owner)) P.fail('permission-denied','Sharing is private.');
  return {...m,c};
}
async function validEvent(tx, uid, eid, now) {
  const e=(await tx.get(ref(`pokes/${eid}`))).data();
  if(!e || e.recipient!==uid || e.expiresAt<=now || !['accepted','provider_accepted'].includes(e.status)) P.fail('not-found','Poke unavailable.');
  const m=await membership(tx,uid,true);
  const t=(await tx.get(ref(`spaces/${m.sid}/tasks/${e.taskId}`))).data();
  const pref=(await tx.get(ref(`preferences/${uid}`))).data();
  if(m.sid!==e.spaceId||m.s.generation!==e.generation||!m.s.members.includes(e.sender)||!t||t.status==='Done'||t.version!==e.taskVersion||P.quiet(pref,now)) P.fail('not-found','Poke unavailable.');
  return e;
}
// All data access is through this authenticated callable, including health reads.
// Deny-all Firestore rules intentionally prevent direct SDK/cache access.
exports.pipoApi = onCall({region:'europe-west1', maxInstances:10, timeoutSeconds:60},async request=> {
  const uid=auth(request),d=request.data||{},action=d.action,now=Date.now();
  if(typeof action!=='string') P.fail('invalid-argument','Action required.');
  await ensureKey();
  await limit(uid, ['join'].includes(action)?'join':'api', action==='join'?5:120, now, request.rawRequest.ip);
  if(action==='readiness') return {version:2,auth:true,pairing:true,sync:true,health:false,poke:true};
  if(['regenerate','invite'].includes(action)) {
    await limit(uid,'invite',5,now,request.rawRequest.ip);
    let code='';const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';for(let i=0;i<6;i++)code+=alphabet[crypto.randomInt(alphabet.length)];
    return db.runTransaction(async tx=>{const m=await membership(tx,uid);if(m.s.members.length!==1)P.fail('failed-precondition','Already paired.');const nr=ref(`invites/${hash(code)}`),candidate=await tx.get(nr);const old=m.s.inviteHash?ref(`invites/${m.s.inviteHash}`):null;if(candidate.exists)P.fail('aborted','Please regenerate again.');if(old)tx.delete(old);tx.create(nr,{spaceId:m.sid,creator:uid,generation:m.s.generation,expiresAt:now+86400000,used:false});tx.update(ref(`spaces/${m.sid}`),{inviteHash:hash(code)});return {spaceId:m.sid,code,expiresAt:now+86400000};});
  }
  if(action==='create') {
    let code='';const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';for(let i=0;i<6;i++) code+=alphabet[crypto.randomInt(alphabet.length)];
    const sid=uuid(),generation=uuid(),invite=ref(`invites/${hash(code)}`);
    await db.runTransaction(async tx=> {const u=await tx.get(ref(`users/${uid}`)); const old=await tx.get(invite);if(u.data()?.spaceId) P.fail('failed-precondition','Leave your existing space first.');if(old.exists) P.fail('aborted','Try creating again.');tx.set(ref(`users/${uid}`),{spaceId:sid});tx.set(ref(`spaces/${sid}`),{active:true,members:[uid],generation,inviteHash:hash(code),listsCount:1,tasksCount:0,notesCount:0,createdAt:now});tx.create(ref(`spaces/${sid}/lists/home`),{name:'Home',emoji:'🏠',pinned:true,assigneeUid:null,pictures:[],version:1,updatedAt:now});tx.set(invite,{spaceId:sid,creator:uid,generation,expiresAt:now+86400000,used:false}); });
    return {spaceId:sid,code,expiresAt:now+86400000};
  }
  if(action==='join') {
    const code=P.text(d.code,6).toUpperCase();if(!/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/.test(code)) P.fail('not-found','Code unavailable.');
    return db.runTransaction(async tx=> {const ir=ref(`invites/${hash(code)}`),iv=await tx.get(ir),i=iv.data(),u=await tx.get(ref(`users/${uid}`));if(!i||i.used||i.expiresAt<=now||i.creator===uid||u.data()?.spaceId) P.fail('not-found','Code unavailable.');const sr=ref(`spaces/${i.spaceId}`),ss=await tx.get(sr),s=ss.data();if(!s?.active||s.members.length!==1||s.members[0]!==i.creator||s.generation!==i.generation||s.inviteHash!==hash(code)) P.fail('not-found','Code unavailable.');tx.update(sr,{members:[i.creator,uid]});tx.set(ref(`users/${uid}`),{spaceId:i.spaceId});tx.update(ir,{used:true});return {spaceId:i.spaceId};});
  }
  if(action==='leave') {
    return db.runTransaction(async tx=> {const m=await membership(tx,uid);tx.update(ref(`spaces/${m.sid}`),{members:m.s.members.filter(v=>v!==uid),active:m.s.members.length>1,generation:uuid()});tx.set(ref(`users/${uid}`),{spaceId:null});tx.set(ref(`consents/${uid}`),{active:false,revision:uuid()});if(m.partner)tx.set(ref(`consents/${m.partner}`),{active:false,revision:uuid()});return {left:true};});
  }
  if(action==='snapshot') return db.runTransaction(async tx=> {const m=await membership(tx,uid); const lists=await tx.get(db.collection(`spaces/${m.sid}/lists`).limit(200));const tasks=await tx.get(db.collection(`spaces/${m.sid}/tasks`).limit(500));const notes=await tx.get(db.collection(`spaces/${m.sid}/notes`).limit(200));const profiles=await tx.getAll(...m.s.members.map(v=>ref(`profiles/${v}`)));return {spaceId:m.sid,generation:m.s.generation,uid,partnerUid:m.partner||null,profiles:profiles.map((x,i)=>({uid:m.s.members[i],...x.data()})),notes:notes.docs.map(x=>({id:x.id,...x.data()})),lists:lists.docs.map(x=>({id:x.id,...x.data()})),tasks:tasks.docs.map(x=>({id:x.id,...x.data()}))};});
  if(action==='profile') {const v=d.value||{};const photo=typeof v.photo==='string'?v.photo:'';await db.runTransaction(async tx=>{const m=await membership(tx,uid);if(photo)await mediaAccess(tx,m,photo);tx.set(ref(`profiles/${uid}`),{name:P.text(v.name,20),avatar:P.integer(v.avatar,-1,10),photo,togetherSince:P.integer(v.togetherSince,-36500,73000)});});return {saved:true};}
  if(action==='saveNote'||action==='deleteNote')return db.runTransaction(async tx=>{const m=await membership(tx,uid),rr=ref(`spaces/${m.sid}/notes/${P.id(d.id)}`),old=await tx.get(rr);if(old.exists&&old.data().sender!==uid)P.fail('permission-denied','Only the sender can change this note.');if(action==='deleteNote'){if(old.exists){tx.delete(rr);tx.update(ref(`spaces/${m.sid}`),{notesCount:Math.max(0,(m.s.notesCount||0)-1)});}return {deleted:true};}const v=d.value||{},photo=v.picture||'';if(photo)await mediaAccess(tx,m,photo);if(!old.exists){if((m.s.notesCount||0)>=200)P.fail('resource-exhausted','Space has 200 notes. Remove one before adding more.');tx.update(ref(`spaces/${m.sid}`),{notesCount:(m.s.notesCount||0)+1});}tx.set(rr,{sender:uid,kind:P.text(v.kind,40),message:typeof v.message==='string'?v.message.slice(0,4000):'',picture:photo,taskTitle:typeof v.taskTitle==='string'?v.taskTitle.slice(0,80):'',at:old.data()?.at||now});return {saved:true};});
  if(action==='uploadPhoto') {
    await limit(uid,'photo',10,now,request.rawRequest.ip);
    const data=P.text(d.base64,524288);if(!/^[A-Za-z0-9+/]+={0,2}$/.test(data))P.fail('invalid-argument','Invalid picture.');const bytes=Buffer.from(data,'base64');if(bytes.length>393216||bytes[0]!==255||bytes[1]!==216||bytes[bytes.length-2]!==255||bytes[bytes.length-1]!==217)P.fail('invalid-argument','Choose a JPEG picture under 384 KB.');
    const m=await db.runTransaction(tx=>membership(tx,uid)),id=uuid(),path=`spaces/${m.sid}/${m.s.generation}/${id}.jpg`;await getStorage().bucket().file(path).save(bytes,{resumable:false,metadata:{contentType:'image/jpeg',cacheControl:'private,no-store'}});
    try {await db.runTransaction(async tx=>{const fresh=await membership(tx,uid);if(fresh.sid!==m.sid||fresh.s.generation!==m.s.generation)P.fail('permission-denied','Connection changed.');tx.create(ref(`spaces/${m.sid}/media/${id}`),{path,generation:m.s.generation,owner:uid,createdAt:now});});}catch(e){await getStorage().bucket().file(path).delete().catch(()=>{});throw e;}return {photo:`cloud:${id}`};
  }
  if(action==='readPhoto') {const photo=P.text(d.photo,100);const metadata=await db.runTransaction(async tx=>mediaAccess(tx,await membership(tx,uid),photo));const [bytes]=await getStorage().bucket().file(metadata.path).download();await db.runTransaction(async tx=>mediaAccess(tx,await membership(tx,uid),photo));return {base64:bytes.toString('base64')};}

  if(['saveTask','saveList','deleteTask','deleteList'].includes(action)) {
    const tid=P.id(d.id);return db.runTransaction(async tx=> {const m=await membership(tx,uid);const type=action.endsWith('Task')?'tasks':'lists',rr=ref(`spaces/${m.sid}/${type}/${tid}`),old=await tx.get(rr);const previous=old.data();if((previous?.version||0)!==d.expectedVersion)P.fail('aborted','Changed on another device. Refresh before editing.');
      let value;
      if(action==='saveTask') {value=P.task(d.value,m.s.members);if(value.status==='Done'&&previous?.status==='Done')value.completedAt=previous.completedAt;value.parentId=d.value.parentId?P.id(d.value.parentId):'';const ls=await tx.get(ref(`spaces/${m.sid}/lists/${value.listId}`));if(!ls.exists)P.fail('not-found','List unavailable.');}
      if(action==='saveList') {const v=d.value||{};value={name:P.text(v.name),emoji:P.text(v.emoji,32),pinned:v.pinned===true,assigneeUid:v.assigneeUid==null?null:P.id(v.assigneeUid)};if(value.assigneeUid&&!m.s.members.includes(value.assigneeUid))P.fail('permission-denied','Invalid assignment.');const pics=v.pictures||[];if(!Array.isArray(pics)||pics.length>12)P.fail('invalid-argument','Up to 12 pictures per list.');for(const photo of pics)await mediaAccess(tx,m,photo);value.pictures=pics;}
      if(action==='deleteList') {const tasks=await tx.get(db.collection(`spaces/${m.sid}/tasks`).where('listId','==',tid).limit(1));if(!tasks.empty)P.fail('failed-precondition','Delete or move its tasks first.');}
      let occurrence;
      if(action==='saveTask'&&value.parentId&&!previous)P.fail('permission-denied','Occurrences are created by the server.');
      if(action==='saveTask'&&previous?.status!=='Done'&&value.status==='Done'&&value.repeat!=='Once') {
        const zone=typeof d.timeZone==='string'?d.timeZone:'UTC';let parts;try{parts=new Intl.DateTimeFormat('en',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(now));}catch(_){P.fail('invalid-argument','Invalid time zone.');}const part=k=>Number(parts.find(v=>v.type===k).value),today=Math.floor(Date.UTC(part('year'),part('month')-1,part('day'))/86400000),step=value.repeat==='Daily'?1:7;let day=value.day+step;if(day<today)day+=Math.ceil((today-day)/step)*step;if(day===today&&(value.minute??540)<=part('hour')*60+part('minute'))day+=step;
        const nextId=crypto.createHash('sha256').update(`${m.sid}:${tid}:next`).digest('hex'),nr=ref(`spaces/${m.sid}/tasks/${nextId}`),sn=await tx.get(nr);if(!sn.exists)occurrence={nr,value:{...value,day,status:'Todo',completedAt:0,parentId:tid,version:1,updatedAt:now}};
      }
      if(action==='saveTask'&&previous?.status==='Done'&&value.status!=='Done') {const nextId=crypto.createHash('sha256').update(`${m.sid}:${tid}:next`).digest('hex'),nr=ref(`spaces/${m.sid}/tasks/${nextId}`),sn=await tx.get(nr);if(sn.data()?.version===1&&sn.data()?.status==='Todo')occurrence={nr,remove:true};}
      const counter=type==='tasks'?'tasksCount':'listsCount',count=m.s[counter]||0,delta=action.startsWith('delete')?(old.exists?-1:0):(!old.exists?1:0)+(occurrence?(occurrence.remove?-1:1):0);if(count+delta>(type==='tasks'?500:200))P.fail('resource-exhausted','Space capacity reached. Remove old items first.');if(delta)tx.update(ref(`spaces/${m.sid}`),{[counter]:Math.max(0,count+delta)});
      if(action.startsWith('delete')) {tx.delete(rr);return {deleted:true};}if(occurrence){if(occurrence.remove)tx.delete(occurrence.nr);else tx.create(occurrence.nr,occurrence.value);}value.version=(previous?.version||0)+1;value.updatedAt=now;tx.set(rr,value);return {id:tid,...value}; });
  }
  if(action==='preferences') {
    const v=d.value||{};if(typeof v.timeZone!=='string')P.fail('invalid-argument','Time zone required.');try {new Intl.DateTimeFormat('en',{timeZone:v.timeZone});}catch(_){P.fail('invalid-argument','Invalid time zone.');}
    const value={pokeOptIn:v.pokeOptIn===true,quietEnabled:v.quietEnabled===true,quietStart:P.integer(v.quietStart,0,1439),quietEnd:P.integer(v.quietEnd,0,1439),timeZone:v.timeZone};if(value.quietEnabled&&value.quietStart===value.quietEnd)P.fail('invalid-argument','Quiet hours must have different times.');await ref(`preferences/${uid}`).set(value);return {saved:true};
  }
  if(action==='device') {
    const did=P.id(d.deviceId),token=P.text(d.token,4096),tr=ref(`_tokens/${hash(token)}`),dr=ref(`users/${uid}/devices/${did}`);await db.runTransaction(async tx=>{const owner=await tx.get(tr),prior=await tx.get(dr);if(owner.exists&&(owner.data().uid!==uid||owner.data().did!==did))P.fail('permission-denied','Device already registered.');if(prior.data()?.token&&prior.data().token!==token)tx.delete(ref(`_tokens/${hash(prior.data().token)}`));tx.set(tr,{uid,did});tx.set(dr,{token,eligible:d.eligible===true,updatedAt:now});});return {registered:true};
  }
  if(action==='revokeDevice'){const did=P.id(d.deviceId);await db.runTransaction(async tx=>{const rr=ref(`users/${uid}/devices/${did}`),sn=await tx.get(rr);if(sn.data()?.token)tx.delete(ref(`_tokens/${hash(sn.data().token)}`));tx.delete(rr);});return {revoked:true};
  }
  if(['consent','revoke','uploadHealth','deleteHealth','partnerHealth'].includes(action))P.fail('failed-precondition','Health sharing is not released. Records remain private.');
  if(action==='consent') {
    return db.runTransaction(async tx=> {const m=await membership(tx,uid,true);const cats=d.categories;if(!Array.isArray(cats)||!cats.length||cats.some(c=>!P.categories.includes(c))||cats.includes('Notes')&&d.notesOptIn!==true)P.fail('invalid-argument','Choose categories and separately consent to notes.');const revision=uuid();const fromDay=d.history===true?P.integer(d.fromDay,-36500,73000):Math.floor(now/86400000);tx.set(ref(`consents/${uid}`),{active:true,recipient:m.partner,spaceId:m.sid,generation:m.s.generation,categories:[...new Set(cats)],fromDay,revision,at:now});return {revision,fromDay,recipient:m.partner};});
  }
  if(action==='revoke') {await ref(`consents/${uid}`).set({active:false,revision:uuid()});return {private:true};}
  if(['uploadHealth','deleteHealth'].includes(action)) {
    const day=P.integer(d.day,-36500,73000);return db.runTransaction(async tx=> {const m=await currentConsent(tx,uid);if(m.c.revision!==d.revision||day<m.c.fromDay)P.fail('permission-denied','Consent has changed or date is outside scope.');const hr=ref(`health/${uid}/entries/${day}`);if(action==='deleteHealth')tx.delete(hr);else tx.set(hr,{day,revision:m.c.revision,fields:P.projection(d.fields,m.c)});return {synced:true};});
  }
  if(action==='partnerHealth') return db.runTransaction(async tx=> {const m=await membership(tx,uid,true),c=await currentConsent(tx,uid,m.partner);const logs=await tx.get(db.collection(`health/${m.partner}/entries`).where('revision','==',c.c.revision).limit(200));return {owner:m.partner,revision:c.c.revision,entries:logs.docs.map(x=>x.data()).filter(x=>x.day>=c.c.fromDay)};});
  if(action==='validatePoke') return db.runTransaction(async tx=> {const e=await validEvent(tx,uid,P.id(d.eventId),now);return {taskId:e.taskId,spaceId:e.spaceId,eventId:d.eventId,expiresAt:e.expiresAt};});
  if(action==='poke') {
    const taskId=P.id(d.taskId),nonce=P.id(d.nonce),eid=hash(`${uid}:${nonce}`),er=ref(`pokes/${eid}`);
    const result=await db.runTransaction(async tx=> {const previous=await tx.get(er);if(previous.exists) {const e=previous.data();if(e.taskId!==taskId||e.taskVersion!==d.expectedVersion)P.fail('already-exists','Nonce already used.');return {eventId:eid,status:e.status,expiresAt:e.expiresAt};}
      const m=await membership(tx,uid,true),t=(await tx.get(ref(`spaces/${m.sid}/tasks/${taskId}`))).data(),p=(await tx.get(ref(`preferences/${m.partner}`))).data();if(!t||t.status==='Done'||t.assigneeUid===uid||t.version!==d.expectedVersion)P.fail('failed-precondition','Task is not available for a Poke.');if(P.quiet(p,now))P.fail('failed-precondition','Partner Poke unavailable.');const devices=await tx.get(db.collection(`users/${m.partner}/devices`).where('eligible','==',true).limit(10));if(devices.empty)P.fail('failed-precondition','Partner Poke unavailable.');
      const cr=ref(`cooldowns/${hash(`${m.partner}:${m.sid}:${taskId}`)}`),cd=(await tx.get(cr)).data();const sender=ref(`quotas/${uid}`),recipient=ref(`quotas/${m.partner}`),qs=await tx.get(sender),qr=await tx.get(recipient);if(cd?.until>now)P.fail('resource-exhausted','Try again after the cooldown.');const window=Math.floor(now/3600000);const sc=qs.data()?.window===window?qs.data().sent||0:0,rc=qr.data()?.window===window?qr.data().received||0:0;if(sc>=10||rc>=6)P.fail('resource-exhausted','Poke limit reached.');
      tx.set(sender,{window,sent:sc+1,received:qs.data()?.window===window?qs.data().received||0:0});tx.set(recipient,{window,received:rc+1,sent:qr.data()?.window===window?qr.data().sent||0:0});tx.set(cr,{until:now+900000});tx.set(er,{sender:uid,recipient:m.partner,spaceId:m.sid,generation:m.s.generation,taskId,taskVersion:t.version,status:'accepted',expiresAt:now+600000});return {eventId:eid,status:'accepted',expiresAt:now+600000};});
    // Private worker sends asynchronously; callable acceptance never asserts delivery.
    return {eventId:eid,status:result.status,expiresAt:result.expiresAt,cooldownUntil:now+900000};
  }
  P.fail('invalid-argument','Unknown action.');
});
async function mediaAccess(tx,m,photo) {
  if(typeof photo!=='string'||!/^cloud:[A-Za-z0-9_-]{1,100}$/.test(photo))P.fail('invalid-argument','Upload the picture first.');
  const sn=await tx.get(ref(`spaces/${m.sid}/media/${photo.substring(6)}`)),v=sn.data();if(!v||v.generation!==m.s.generation)P.fail('permission-denied','Picture unavailable.');return v;
}
async function dispatch(eid) {
  const er=ref(`pokes/${eid}`),lease=uuid();let e;
  try {e=await db.runTransaction(async tx=>{const sn=await tx.get(er),v=sn.data();if(!v||v.status!=='accepted')return null;if(v.expiresAt<=Date.now()){tx.update(er,{status:'expired'});return null;}if(v.leaseUntil>Date.now())throw new Error('Lease busy');await validEvent(tx,v.recipient,eid,Date.now());tx.update(er,{lease,leaseUntil:Date.now()+120000});return v;});}
  catch(err){if(['not-found','permission-denied','failed-precondition'].includes(err.code)){await er.update({status:'canceled'});return;}throw err;}
  if(!e)return;
  let failed=false;
  try {
    const devices=await db.collection(`users/${e.recipient}/devices`).where('eligible','==',true).limit(10).get();
    for(const device of devices.docs) {
      let token,key;
      try {
        const route=await db.runTransaction(async tx=>{await validEvent(tx,e.recipient,eid,Date.now());const event=await tx.get(er),fresh=await tx.get(device.ref);const v=fresh.data();if(!v?.eligible||event.data().lease!==lease)return null;const k=hash(v.token),owned=await tx.get(ref(`_tokens/${k}`));if(owned.data()?.uid!==e.recipient||owned.data()?.did!==device.id||event.data().sent?.[k])return null;return {token:v.token,key:k};});
        if(!route)continue;token=route.token;key=route.key;
        await getMessaging().send({token,data:{type:'partner_poke',event_id:eid},android:{priority:'high',ttl:Math.max(0,e.expiresAt-Date.now())}});
        await er.update({[`sent.${key}`]:true});
      } catch(err) {
        if(['not-found','permission-denied','failed-precondition'].includes(err.code)){await er.update({status:'canceled'});return;}
        if(['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(err.code)) {
          await db.runTransaction(async tx=>{const sn=await tx.get(device.ref);if(sn.data()?.token===token){tx.update(device.ref,{eligible:false});tx.delete(ref(`_tokens/${hash(token)}`));}});
        } else failed=true;
      }
    }
    if(failed)throw new Error('Transient delivery failure; retry the same event');
    await er.update({status:devices.empty?'canceled':'provider_accepted'});
  } finally {await db.runTransaction(async tx=>{const sn=await tx.get(er);if(sn.data()?.lease===lease)tx.update(er,{leaseUntil:0});});}
}
// Private Firestore outbox; retry events until accepted/canceled/expired. Client deduplicates eid.
const { onDocumentCreated } = require('firebase-functions/v2/firestore');
exports.deliverPoke = onDocumentCreated({document:'pokes/{eventId}',region:'europe-west1',retry:true,maxInstances:10,timeoutSeconds:60},async event=>{await ensureKey();await dispatch(event.params.eventId);});
