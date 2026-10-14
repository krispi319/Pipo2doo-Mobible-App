'use strict';
const test=require('node:test');const assert=require('node:assert/strict');const P=require('../policies');
test('Health projection rejects private notes without separate scope',()=>{assert.throws(()=>P.projection({mood:2,note:'private'},{categories:['Mood']}));assert.deepEqual(P.projection({mood:2},{categories:['Mood']}),{mood:2});});
test('Scope narrowing rejects symptoms and craving fields',()=>{assert.throws(()=>P.projection({symptoms:['Fatigue']},{categories:['Mood']}));assert.throws(()=>P.projection({cravings:['Sweet']},{categories:['Period']}));});
test('Only an active member can be assigned a task',()=>{assert.throws(()=>P.task({assigneeUid:'outsider'},['a','b']));});
test('Overnight quiet hours, exact boundaries and opt-out fail closed',()=>{const p={pokeOptIn:true,quietEnabled:true,quietStart:1320,quietEnd:450,timeZone:'UTC'};assert.equal(P.quiet(p,Date.parse('2026-06-01T22:00Z')),true);assert.equal(P.quiet(p,Date.parse('2026-06-02T07:29Z')),true);assert.equal(P.quiet(p,Date.parse('2026-06-02T07:30Z')),false);assert.equal(P.quiet({...p,pokeOptIn:false}),true);assert.equal(P.quiet({...p,timeZone:'invalid'}),true);});
