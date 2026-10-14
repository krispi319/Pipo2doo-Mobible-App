'use strict';
const { HttpsError } = require('firebase-functions/v2/https');
const fail = (code, message) => { throw new HttpsError(code, message); };
function text(value, max = 80) { if (typeof value !== 'string' || !value.trim() || value.length > max) fail('invalid-argument', 'Invalid text.'); return value.trim(); }
function id(value) { const v = text(value, 100); if (!/^[A-Za-z0-9_-]+$/.test(v)) fail('invalid-argument', 'Invalid identifier.'); return v; }
function integer(v, min, max) { if (!Number.isInteger(v) || v < min || v > max) fail('invalid-argument', 'Invalid number.'); return v; }
function quiet(p, now = Date.now()) {
  if (!p || p.pokeOptIn !== true || typeof p.timeZone !== 'string') return true;
  if (!p.quietEnabled) return false;
  try { const parts = new Intl.DateTimeFormat('en-GB', { timeZone:p.timeZone, hour:'2-digit', minute:'2-digit', hourCycle:'h23' }).formatToParts(new Date(now)); const n = Number(parts.find(x=>x.type==='hour').value)*60+Number(parts.find(x=>x.type==='minute').value); const a=p.quietStart,b=p.quietEnd; if(a===b) return true; return a<b ? n>=a&&n<b : n>=a||n<b; } catch (_) { return true; }
}
function task(raw, members) {
  const r = raw || {}; const owner = r.assigneeUid == null ? null : id(r.assigneeUid); if(owner && !members.includes(owner)) fail('permission-denied', 'Invalid assignment.');
  if (!['Once','Daily','Weekly'].includes(r.repeat) || !['Todo','Doing','Done'].includes(r.status)) fail('invalid-argument','Invalid task state.');
  return { title:text(r.title), listId:id(r.listId), assigneeUid:owner, day:integer(r.day,-36500,73000), minute:r.minute==null?null:integer(r.minute,0,1439), repeat:r.repeat,status:r.status, tags:(Array.isArray(r.tags)?r.tags:[]).slice(0,20).map(v=>text(v,40)), note:typeof r.note==='string'?r.note.slice(0,4000):'', completedAt:r.status==='Done'?Date.now():0 };
}
const categories = ['Period','Mood','Cravings','Symptoms','Notes'];
function projection(raw, consent) {
  const r=raw||{}, out={}; const fields={Period:['start','end','flow','cramps'],Mood:['mood'],Cravings:['cravings'],Symptoms:['symptoms'],Notes:['note']};
  const allowed=consent.categories.flatMap(c=>fields[c]||[]); if(Object.keys(r).some(k=>!allowed.includes(k))) fail('permission-denied','A field is outside consent.');
  for(const k of ['start','end']) if(k in r) {if(typeof r[k]!=='boolean') fail('invalid-argument','Invalid period mark.');out[k]=r[k];}
  for(const k of ['flow','cramps']) if(k in r) out[k]=text(r[k],30);
  if('mood' in r) out.mood=r.mood==null?null:integer(r.mood,0,4);
  for(const k of ['cravings','symptoms']) if(k in r) { if(!Array.isArray(r[k])||r[k].length>30) fail('invalid-argument','Invalid selections.');out[k]=r[k].map(v=>text(v,60)); }
  if('note' in r) {if(typeof r.note!=='string'||r.note.length>4000) fail('invalid-argument','Invalid note.');out.note=r.note;}
  return out;
}
module.exports={ fail,text,id,integer,quiet,task,categories,projection };
