import {seedCourseForAttempt} from './auth-client.mjs';
import assert from 'node:assert/strict';
import{readFileSync,readdirSync}from'node:fs';
import{DatabaseSync}from'node:sqlite';
import{randomUUID}from'node:crypto';
import{curriculum}from'../lib/grading';
const {users}=JSON.parse(readFileSync('.sites-runtime/governance-qa.json','utf8'));
const {operator,owner,certifier,trainer}=users;
const base='http://localhost:5173';
async function call(user:any,path:string,body?:unknown,space?:string,method=body?'POST':'GET'){
 if((body as {action?:string})?.action==='request'&&path==='credentials')seedCourseForAttempt(space??user.userId,(body as {attemptId:string}).attemptId);
 const r=await fetch(base+'/api/'+path,{method,headers:{'Content-Type':'application/json',Origin:base,Cookie:user.cookie+(space?`; suraksha_workspace=${encodeURIComponent(space)}`:'')},body:body?JSON.stringify(body):undefined});return {status:r.status,data:await r.json() as any};
}
const dir='.wrangler/state/v3/d1/miniflare-D1DatabaseObject',db=new DatabaseSync(`${dir}/${readdirSync(dir).find(f=>f.endsWith('.sqlite')&&f!=='metadata.sqlite')}`);db.exec('PRAGMA busy_timeout=5000');
// Reset only the four explicitly synthetic accounts' training fixtures for repeatable local QA.
for(const u of Object.values(users) as any[]){
 assert(u.email.endsWith('@example.test'),'Never reset non-synthetic accounts');
 for(const table of ['certification_requests','credentials','attempts','workers','training_assignments','team_members','audit_log','centre_approvals','training_centres'])db.prepare(`DELETE FROM ${table} WHERE owner=?`).run(u.userId);
 const r=await fetch(base+'/api/auth/sign-in/email',{method:'POST',headers:{'Content-Type':'application/json',Origin:base},body:JSON.stringify({email:u.email,password:u.password})});assert.equal(r.status,200,'Fresh synthetic session');
 u.cookie=r.headers.getSetCookie().filter(v=>!/Max-Age=0(?:;|$)/i.test(v)).map(v=>v.split(';')[0]).join('; ');await r.arrayBuffer();
}
const state=await call(owner,'admin/session');assert.equal(state.status,200);assert.equal(state.data.current,null);assert.equal(state.data.workspaces.length,0,'signup grants no workspace');
for(const endpoint of ['records','admin/manage','room-journals','credentials'])assert.equal((await call(owner,endpoint)).status,403,endpoint+' requires approved centre');
assert.equal((await call(operator,'governance',{action:'request',name:'Operator own centre · QA',site:'Synthetic site'})).status,200);
assert.equal((await call(operator,'governance',{action:'approve',owner:operator.userId,reason:'Operator self review is forbidden'})).status,403);
assert.equal((await call(owner,'governance',{action:'request',name:'Government demonstration centre · QA',site:'Synthetic training site'})).status,200);
assert.equal((await call(owner,'governance',{action:'approve',owner:owner.userId,reason:'Self approval must fail'})).status,403);
assert.equal((await call(operator,'governance',{action:'reject',owner:owner.userId,reason:'Synthetic application needs corrected site details'})).status,200);
assert.equal((await call(owner,'governance',{action:'request',name:'Government demonstration centre · QA',site:'Synthetic training site'})).status,200);
assert.equal((await call(operator,'governance',{action:'approve',owner:owner.userId,reason:'Synthetic centre reviewed for integration QA'})).status,200);
assert.equal((await call(owner,'admin/session')).data.current.role,'admin');
for(const [user,role]of[[trainer,'trainer'],[certifier,'certifier']] as const){
 const invitation=await call(owner,'admin/manage',{action:'member',email:user.email,role,active:true});assert.equal(invitation.status,200,JSON.stringify(invitation));
 assert.equal((await call(user,'admin/session',{owner:owner.userId})).status,403,'membership needs code');
 assert.equal((await call(user,'admin/session',{invitation:invitation.data.invitation})).status,200);
}
assert.equal((await call(certifier,'admin/manage',{action:'worker',name:'Forbidden writer',sector:'Mining'},owner.userId)).status,403);
assert.equal((await call(trainer,'admin/manage',{action:'member',email:'no@example.test',role:'admin',active:true},owner.userId)).status,403);
const t=(path:string,body?:unknown)=>call(trainer,path,body,owner.userId),c=(path:string,body?:unknown)=>call(certifier,path,body,owner.userId);
const worker={id:randomUUID(),name:'Governance learner · synthetic',sector:'Mining'},m=curriculum.modules[0];let serial=0;
async function assessment(passed=true,version=curriculum.version){const start=Date.now()+serial++*20000;const questions=passed?m.questions:[m.questions[0]];const a={id:randomUUID(),workerId:worker.id,moduleId:m.id,contentVersion:version,kind:'assessment',mode:'screen',finished:true,startedAt:start,endedAt:start+9000,events:questions.map((q,i)=>({type:'answer',sequence:i+1,questionId:q.id,optionId:q.options.find(o=>passed?o.correct:!o.correct)!.id,time:start+i+1}))};const r=await t('import',{schemaVersion:1,worker,attempts:[a]});assert.equal(r.status,200,JSON.stringify(r));return a;}
const a=await assessment();const expiry=Date.now()+86400000;
assert.equal((await t('credentials',{attemptId:a.id,expiresAt:expiry})).status,400,'old direct signing contract is rejected');
const request=await t('credentials',{action:'request',attemptId:a.id,expiresAt:expiry,note:'Reviewed classroom simulation record for independent QA'});assert.equal(request.status,202,JSON.stringify(request));const id=request.data.request.id;
assert.equal((await t('credentials',{action:'request',attemptId:a.id,expiresAt:expiry,note:'Reviewed classroom simulation record for independent QA'})).data.request.id,id);
assert.equal((await t('credentials',{action:'request',attemptId:a.id,expiresAt:expiry,note:'Changed note must not silently replace existing evidence'})).status,400);
assert.equal((await t('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:id,reason:'Trainer cannot approve'})).status,403);
assert.equal((await call(owner,'credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:id,reason:'Centre admin cannot sign'})).status,403);
// Switching the requester to certifier must not allow self-approval.
db.prepare('UPDATE team_members SET role=? WHERE owner=? AND user_id=?').run('certifier',owner.userId,trainer.userId);
assert.equal((await t('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:id,reason:'Self approval after role change'})).status,403);
db.prepare('UPDATE team_members SET role=? WHERE owner=? AND user_id=?').run('trainer',owner.userId,trainer.userId);
const approvals=await Promise.all([c('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:id,reason:'Independent answers and evidence reviewed by QA'}),c('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:id,reason:'Concurrent duplicate review must not duplicate issuance'})]);assert.equal(approvals.filter(r=>r.status===200).length,1,JSON.stringify(approvals));const signed=approvals.find(r=>r.status===200)!.data;
const verified=await c('verify',{token:signed.token});assert.equal(verified.status,200);assert.equal(verified.data.governanceVersion,1);assert.equal(verified.data.centreRef,owner.userId);assert.equal(verified.data.approvedBy,certifier.userId);assert.equal(verified.data.requestedBy,trainer.userId);assert(verified.data.evidenceDigest);
assert.equal((await call(trainer,'credentials',{id:signed.id,reason:'Trainer cannot revoke'},owner.userId,'PATCH')).status,403);
assert.equal((await call(owner,'credentials',{id:signed.id,reason:'Documented QA revocation reason'},undefined,'PATCH')).status,200);
assert.equal((await c('verify',{token:signed.token})).data.status,'revoked');
// New failure blocks both old-pass submission and approval of a pending pass.
const b=await assessment();const pending=await t('credentials',{action:'request',attemptId:b.id,expiresAt:expiry,note:'Before later failure arrives'});assert.equal(pending.status,202);
await assessment(false);
assert.equal((await t('credentials',{action:'request',attemptId:b.id,expiresAt:expiry,note:'Must not use an older pass'})).status,400);
assert.equal((await c('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:pending.data.request.id,reason:'Later failure means reject approval'})).status,400);
assert.equal((await c('credentials',{action:'reject',requestId:pending.data.request.id,reason:'Later failure requires fresh training and assessment'})).status,200);
const next=await assessment();const pending2=await t('credentials',{action:'request',attemptId:next.id,expiresAt:expiry,note:'Suspension and role freshness test'});assert.equal(pending2.status,202);
assert.equal((await call(operator,'governance',{action:'suspend',owner:owner.userId,reason:'Synthetic suspension while approval pending'})).status,200);
assert.equal((await c('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:pending2.data.request.id,reason:'Suspended centre must fail'})).status,403);assert.equal((await t('records')).status,403);
assert.equal((await call(operator,'governance',{action:'approve',owner:owner.userId,reason:'Reviewed remediation and restored pilot centre'})).status,200);
assert.equal((await call(owner,'admin/manage',{action:'member',email:certifier.email,role:'certifier',active:false})).status,200);
assert.equal((await c('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:pending2.data.request.id,reason:'Revoked certifier must fail'})).status,403);
assert.equal((await call(owner,'admin/manage',{action:'member',email:certifier.email,role:'certifier',active:true})).status,200);
const logs=(await call(owner,'admin/manage')).data.audit;
assert.equal(logs.filter((r:any)=>r.action==='credential.issue'&&r.target===signed.id).length,1);
assert(logs.some((r:any)=>r.action==='centre.suspend'));assert(logs.some((r:any)=>r.action==='certification.reject'));
assert.equal((await call(operator,'records',undefined,owner.userId)).status,403,'operator governance does not grant private training access');
assert.equal((await c('records?attemptId='+a.id)).data.id,a.id);
// A stale session cannot change governance or sign credentials.
db.prepare('UPDATE auth_session SET created_at=? WHERE user_id=?').run(Date.now()-16*60*1000,certifier.userId);
assert.equal((await c('credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:pending2.data.request.id,reason:'Stale session cannot approve'})).status,403);
db.prepare('UPDATE auth_session SET created_at=? WHERE user_id=?').run(Date.now(),certifier.userId);
assert.equal((await c('credentials',{action:'reject',requestId:pending2.data.request.id,reason:'Independent review requires a new supervised assessment'})).status,200);
assert.equal((await t('credentials',{action:'request',attemptId:next.id,expiresAt:expiry,note:'Suspension and role freshness test'})).status,400,'Rejected request must not pretend to be resubmitted');
console.log('Governance integration PASS: no implicit admin, operator approval/suspension/restoration, private invitations, all roles, no direct issuance/self-approval, atomic concurrent approval, signed provenance, latest-failure gates, rejection, revocation, role freshness, recent sign-in, audit and tenant isolation.');db.close();
