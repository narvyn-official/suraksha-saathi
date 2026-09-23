import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {setTimeout as wait} from 'node:timers/promises';
import {readFileSync,readdirSync} from 'node:fs';
import {account,certifierFor,seedCourseForAttempt} from './auth-client.mjs';
import {curriculum} from '../lib/grading';
const base='http://localhost:5173',user=await account({approved:true});
async function call(path:string,body:unknown,cookie=user.cookie){
 if((body as {action?:string})?.action==='request')seedCourseForAttempt(user.userId,(body as {attemptId:string}).attemptId);
 let r=await fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',Origin:base,Cookie:cookie},body:JSON.stringify(body)});
 if(r.status===429){await wait((Math.max(1,Number(r.headers.get('retry-after'))||60)+1)*1000);r=await fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',Origin:base,Cookie:cookie},body:JSON.stringify(body)});}
 return {status:r.status,data:await r.json() as {imported:number;unchanged:number;id:string;request:{id:string};message:string},headers:r.headers};
}
const now=Date.now(),worker={id:randomUUID(),name:'Reliability QA',sector:'Mining'},m=curriculum.modules[0];
const attempt={id:randomUUID(),workerId:worker.id,moduleId:m.id,contentVersion:curriculum.version,kind:'assessment',mode:'screen',finished:true,startedAt:now,endedAt:now+10000,
 events:m.questions.map((q,i)=>({type:'answer',sequence:i+1,questionId:q.id,optionId:q.options.find(o=>o.correct)!.id,time:now+i+1}))};
const payload={schemaVersion:1,worker,attempts:[attempt]};
const imports=await Promise.all(Array.from({length:4},()=>call('/api/import',payload)));
assert(imports.every(r=>r.status===200),'Concurrent identical imports must all succeed');
assert.equal(imports.reduce((n,r)=>n+r.data.imported,0),1);
assert.equal(imports.reduce((n,r)=>n+r.data.unchanged,0),3);
const certifier=await certifierFor(user);
const expiresAt=now+86400000;
const issuance=await Promise.all(Array.from({length:4},()=>call('/api/credentials',{action:'request',note:'Concurrent synthetic request evidence',attemptId:attempt.id,expiresAt})));
assert(issuance.every(r=>r.status===202),'Concurrent requests must succeed');assert.equal(new Set(issuance.map(r=>r.data.request.id)).size,1);
assert.equal((await call('/api/credentials',{action:'approve',rubric:{evidenceReviewed:true,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'},requestId:issuance[0].data.request.id,reason:'Independent synthetic review for concurrency test'},certifier.cookie)).status,200);
const changed=structuredClone(payload);changed.attempts[0].mode='arcore';
assert.equal((await call('/api/import',changed)).status,400,'Conflicting attempt must not overwrite evidence');
const logs=await fetch(base+'/api/admin/manage',{headers:{Cookie:user.cookie}}).then(r=>r.json()) as {audit:{action:string}[]};
assert.equal(logs.audit.filter((r:{action:string})=>r.action==='credential.issue').length,1,'Retries do not duplicate issuance audit');
assert.equal(logs.audit.filter((r:{action:string})=>r.action==='assessment.import').length,1,'Retries do not duplicate import audit');
assert.equal((await call('/api/auth/sign-up/email',{email:`short-${randomUUID()}@example.test`,name:'Short password QA',password:'shortpassword'},'')).status,400);
const unknown=await call('/api/auth/request-password-reset',{email:`missing-${randomUUID()}@example.test`},'');
const requested=await call('/api/auth/request-password-reset',{email:user.email},'');
assert.equal(requested.status,200);assert.equal(unknown.status,200);assert.equal(requested.data.message,unknown.data.message,'No account disclosure');
const key=readFileSync('.dev.vars','utf8').match(/^RECOVERY_MAIL_KEY=(.*)$/m)?.[1].trim();
assert(key,'Run with the local synthetic mail stub configured; never use real delivery for this suite.');
let messages=await fetch('http://127.0.0.1:5188/messages',{headers:{Authorization:`Bearer ${key}`}}).then(r=>r.json()) as {to:string;text:string}[];
for(let i=0;i<30&&!messages.some((r:{to:string})=>r.to===user.email);i++){await new Promise(r=>setTimeout(r,100));messages=await fetch('http://127.0.0.1:5188/messages',{headers:{Authorization:`Bearer ${key}`}}).then(r=>r.json()) as {to:string;text:string}[];}
const email=messages.filter((r:{to:string})=>r.to===user.email).at(-1);assert(email,'Synthetic mail received');
const match=email.text.match(/http:\/\/localhost:5173\/reset-password#token=([^\s]+)/);assert(match,'Trusted reset fragment');
const token=decodeURIComponent(match[1]),newPassword=`Reset-${randomUUID()}`;
async function latestToken(previous:string){
 for(let i=0;i<50;i++){
  const rows=await fetch('http://127.0.0.1:5188/messages',{headers:{Authorization:`Bearer ${key}`}}).then(r=>r.json()) as {to:string;text:string}[];
  const last=rows.filter((r:{to:string})=>r.to===user.email).at(-1)?.text.match(/#token=([^\s]+)/)?.[1];
  if(last&&decodeURIComponent(last)!==previous)return decodeURIComponent(last);await wait(100);
 }throw new Error('Synthetic recovery message missing');
}
assert.equal((await call('/api/auth/request-password-reset',{email:user.email},'')).status,200);
const outstanding=await latestToken(token);
assert.equal((await call('/api/auth/reset-password',{token:'invalid-token' ,newPassword},'')).status,400);
const resets=await Promise.all([call('/api/auth/reset-password',{token,newPassword},''),call('/api/auth/reset-password',{token,newPassword},'')]);
assert.equal(resets.filter(r=>r.status===200).length,1,'Reset token consumed atomically');
assert.equal(resets.filter(r=>r.status===400).length,1,'Concurrent replay rejected');
assert.equal((await call('/api/auth/reset-password',{token,newPassword},'')).status,400,'Used token rejected');
assert.equal((await call('/api/auth/reset-password',{token:outstanding,newPassword},'')).status,400,'Outstanding links invalidated');
assert.equal((await fetch(base+'/api/admin/session',{headers:{Cookie:user.cookie}})).status,401,'Reset revokes existing session');
assert.equal((await call('/api/auth/sign-in/email',{email:user.email,password:user.password},'')).status,401,'Old password no longer valid');
assert.equal((await call('/api/auth/sign-in/email',{email:user.email,password:newPassword},'')).status,200,'New password signs in');
assert.equal((await call('/api/auth/request-password-reset',{email:user.email},'')).status,200);
const expired=await latestToken(outstanding);
let altered=false;
for(const name of readdirSync('.wrangler/state/v3/d1',{recursive:true})){
 if(typeof name!=='string'||!name.endsWith('.sqlite'))continue;
 const database=new DatabaseSync('.wrangler/state/v3/d1/'+name);
 try { const table=database.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='auth_verification'").get();
  if(table){const changed=database.prepare("UPDATE auth_verification SET expires_at=0 WHERE value=? AND identifier=?").run(user.userId,'reset-password:'+expired);altered ||= Number(changed.changes)>0;}
 }finally{database.close();}
}
assert(altered,'Expired only the synthetic reset fixture');
assert.equal((await call('/api/auth/reset-password',{token:expired,newPassword},'')).status,400,'Expired link rejected');
console.log('Reliability integration passed: concurrent imports and issuance, immutable conflicts, single audit events, password policy, neutral recovery, atomic reset/replay/expiry and outstanding-link invalidation, session revocation and new login. No external email sent.');
