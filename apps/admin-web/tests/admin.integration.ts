import { account, approveLocalCentre } from "./auth-client.mjs";
const identity=await account({approved:true});
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readdirSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { curriculum, curriculumFor } from '../lib/grading';
// Local-only fixture: never sends an identity header or accesses a hosted database.
const dir='.wrangler/state/v3/d1/miniflare-D1DatabaseObject';
const database=new DatabaseSync(`${dir}/${readdirSync(dir).find(f=>f.endsWith('.sqlite')&&f!=='metadata.sqlite')}`);
database.exec('PRAGMA busy_timeout=5000');
const root='http://localhost:5173', owner=`qa-${randomUUID()}`, actor=identity.userId, email=identity.email;
async function call(path:string,body?:unknown,options:{auth?:boolean;space?:string;method?:string;origin?:string}={}){
 const r=await fetch(`${root}/api/${path}`,{method:options.method??(body?'POST':'GET'),headers:{'Content-Type':'application/json',...(options.auth===false?{}:{Cookie:`${identity.cookie}; suraksha_workspace=${encodeURIComponent(options.space??owner)}`}),...(options.origin?{Origin:options.origin}:{})},body:body?JSON.stringify(body):undefined});
 const text=await r.text();let data:any;try{data=JSON.parse(text)}catch{data={error:text}};return {status:r.status,data,cookie:r.headers.get('set-cookie')};
}
function member(role:string,active=1){database.prepare('UPDATE team_members SET role=?,active=? WHERE owner=?').run(role,active,owner);}
try {
 approveLocalCentre(owner);
 database.prepare('INSERT INTO team_members(owner,email,user_id,role,active,updated_at) VALUES(?,?,?,?,1,?)').run(owner,email,actor,'admin',Date.now());
 assert.equal((await call('admin/session',undefined,{auth:false})).status,401);
 const forged=await fetch(`${root}/api/admin/session`,{headers:{Cookie:'__sites_local_auth=1'}});assert.equal(forged.status,401);
 assert.equal((await call('admin/manage',undefined,{auth:false})).status,401);
 const spoof=await fetch(`${root}/api/admin/session`,{headers:{'oai-authenticated-user-id':actor,'oai-authenticated-user-email':email}});assert.equal(spoof.status,401);
 assert.equal((await call('records',undefined,{space:'other-private-space'})).status,403);
 assert.equal((await call('admin/session',{owner:'other-private-space'})).status,403);
 const accepted=await call('admin/session',{owner});assert.equal(accepted.status,200,JSON.stringify(accepted));assert.match(accepted.cookie!,/HttpOnly/);assert.match(accepted.cookie!,/SameSite=Lax/);
 assert.equal((await call('admin/session')).data.current.role,'admin');
 assert.equal((await call('admin/manage',{action:'settings',name:'QA training centre',site:'Fixture only'})).status,200);
 assert.equal((await call('admin/manage',{action:'member',email:'invited@example.test',role:'viewer',active:true})).status,200);
 const worker=await call('admin/manage',{action:'worker',name:'Demo worker · admin integration',sector:'Mining'});assert.equal(worker.status,200);const workerId=worker.data.id;
 assert.equal((await call('admin/manage',{action:'worker',id:randomUUID(),name:'Wrong workspace',sector:'Mining'})).status,400);
 const phoneId=randomUUID();assert.equal((await call('admin/manage',{action:'worker',androidId:phoneId,name:'Demo phone-linked worker',sector:'Mica'})).data.id,phoneId);
 assert.equal((await call('admin/manage',{action:'worker',androidId:phoneId,name:'Duplicate',sector:'Mica'})).status,400);
 const assigned=await call('admin/manage',{action:'assign',workerIds:[workerId],moduleId:'fire',dueAt:Date.now()+86400000,note:'QA assignment'});assert.equal(assigned.status,200,JSON.stringify(assigned));
 const getAssignment=async()=>((await call('admin/manage')).data.assignments as any[]).find(a=>a.id===assigned.data.id);
 assert.equal((await getAssignment()).status,'assigned');
 const created=(await getAssignment()).created_at;
 async function attempt(start:number,kind='assessment',failed=false,contentVersion=curriculum.version){
  const module=curriculumFor(contentVersion)!.modules.find(m=>m.id==='fire')!;
  const questions=failed?[module.questions.find(q=>q.critical)!]:module.questions;
  const a={id:randomUUID(),workerId,moduleId:'fire',contentVersion,kind,mode:'screen',finished:true,startedAt:start,endedAt:start+9000,events:questions.map((q,i)=>({type:'answer',sequence:i+1,questionId:q.id,optionId:q.options.find(o=>failed?!o.correct:o.correct)!.id,time:start+(i+1)*1000})),result:{score:0,passed:false}};
  const result=await call('import',{schemaVersion:1,worker:{id:workerId,name:'Demo worker · admin integration',sector:'Mining'},attempts:[a]});assert.equal(result.status,200,JSON.stringify(result));return a;
 }
 await attempt(created-20000);assert.equal((await getAssignment()).status,'assigned','old pass cannot complete new assignment');
 await attempt(created+1,'assessment',false,'0.3.0');assert.equal((await getAssignment()).status,'assigned','old content cannot satisfy new curriculum assignment');
 await attempt(created+1,'practice');assert.equal((await getAssignment()).status,'assigned','practice cannot complete assignment');
 const passed=await attempt(created+2);assert.equal((await getAssignment()).status,'complete');
 await attempt(created+20000,'assessment',true);assert.equal((await getAssignment()).status,'assigned','later failure must not be hidden by old pass');
 database.prepare('UPDATE training_assignments SET due_at=? WHERE owner=?').run(Date.now()-1,owner);assert.equal((await getAssignment()).status,'overdue');
 assert.equal((await call('admin/manage',{action:'cancel',id:assigned.data.id,reason:'Fixture cancellation'})).status,200);assert.equal((await getAssignment()).status,'cancelled');
 member('viewer');assert.equal((await call('records')).status,200);assert.equal((await call('admin/manage')).data.team.length,0);
 for(const path of ['import','room-journals','credentials'])assert.equal((await call(path,path==='credentials'?{action:'request',attemptId:passed.id,expiresAt:Date.now()+86400000,note:'Viewer cannot submit requests'}:{attemptId:passed.id})).status,403,`${path} viewer writes`);
 assert.equal((await call('credentials',{id:randomUUID(),reason:'Fixture revoke'},{method:'PATCH'})).status,403);
 assert.equal((await call('admin/manage',{action:'worker',name:'Denied',sector:'Other'})).status,403);
 member('trainer');assert.equal((await call('admin/manage',{action:'worker',id:workerId,name:'Demo worker · edited',sector:'Steel'})).status,200);
 assert.equal((await call('admin/manage',{action:'settings',name:'Denied',site:''})).status,403);
 assert.equal((await call('admin/manage',{action:'member',email:'no@example.test',role:'admin',active:true})).status,403);
 member('admin');assert([400,403].includes((await call('admin/manage',{action:'settings',name:'CSRF',site:''},{origin:'https://other.example'})).status));
 const activity=(await call('admin/manage')).data.audit;assert(activity.some((r:any)=>r.action==='assessment.import'));assert(activity.every((r:any)=>r.actor_email===email));
 member('viewer',0);assert.equal((await call('records')).status,403);assert.equal((await call('admin/session')).data.current,null);
 assert.equal((await call('admin/session',{owner})).status,403);
 assert.equal((await call('admin/session',{owner:actor})).status,200,'revoked member can recover personal workspace');
 console.log('Admin API integration passed: login boundary, header spoofing, invitations, isolated workspaces, all roles, revoked access, worker management, assignment evidence/overdue/cancellation, audit and request origin.');
} finally {
 for(const table of ['centre_approvals','certification_requests','credentials','attempts','workers','training_assignments','audit_log','team_members','training_centres'])database.prepare(`DELETE FROM ${table} WHERE owner=?`).run(owner);
 database.close();
}
