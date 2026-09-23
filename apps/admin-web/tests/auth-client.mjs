import assert from 'node:assert/strict';
import {setTimeout as wait} from 'node:timers/promises';
import {randomUUID} from 'node:crypto';
export async function account({approved=false}={}){
 const email=`qa-${randomUUID()}@example.test`, password=`Test-${randomUUID()}`;
 const signup=()=>fetch('http://localhost:5173/api/auth/sign-up/email',{method:'POST',headers:{'Content-Type':'application/json',Origin:'http://localhost:5173'},body:JSON.stringify({name:'QA account',email,password})});
 let r=await signup();
 // Suites share localhost. Honor the real limiter instead of disabling it for QA.
 if(r.status===429){const seconds=Math.min(65,Math.max(1,Number(r.headers.get('retry-after'))||60));await r.arrayBuffer();await wait((seconds+1)*1000);r=await signup();}
 const data=await r.json();assert.equal(r.status,200,JSON.stringify(data));
 const cookie=r.headers.getSetCookie().filter(c=>!/Max-Age=0(?:;|$)/i.test(c)).map(c=>c.split(';')[0]).join('; ');
 assert(cookie.includes('session_token'),'Expected independent session cookie');
 if(approved) approveLocalCentre(data.user.id);
 return {email,password,cookie,userId:data.user.id};
}

// Explicit synthetic fixture, never a production signup path or HTTP bypass.
import {DatabaseSync} from 'node:sqlite';
import {readdirSync,readFileSync} from 'node:fs';
export function localDatabase(){
 const dir='.wrangler/state/v3/d1/miniflare-D1DatabaseObject';
 const file=readdirSync(dir).find(f=>f.endsWith('.sqlite')&&f!=='metadata.sqlite');
 if(!file)throw new Error('Migrate the local QA database first');
 const db=new DatabaseSync(`${dir}/${file}`);db.exec('PRAGMA busy_timeout=5000');return db;
}
export function approveLocalCentre(id){
 const db=localDatabase();try{
 db.prepare("INSERT INTO centre_approvals(owner,status,requested_at,reviewed_at,reviewed_by,reason) VALUES(?,'approved',?,?,'local-test-fixture','Synthetic regression fixture')").run(id,Date.now(),Date.now());
 db.prepare("INSERT INTO training_centres(owner,name,site,updated_at) VALUES(?,'QA approved centre','Synthetic site',?) ON CONFLICT(owner) DO NOTHING").run(id,Date.now());
 }finally{db.close()}
}
export async function certifierFor(owner){
 const person=await account(),db=localDatabase();try{
 db.prepare("INSERT INTO team_members(owner,email,user_id,role,active,updated_at) VALUES(?,?,?,'certifier',1,?)").run(owner.userId,person.email,person.userId,Date.now());
 }finally{db.close()}
 return {...person,cookie:person.cookie+'; suraksha_workspace='+encodeURIComponent(owner.userId)};
}

/** Synthetic curriculum prerequisites for legacy API regression fixtures; never exposed over HTTP. */
export function seedCourseForAttempt(owner,id){
 const db=localDatabase();try{
  const user=db.prepare('SELECT email FROM auth_user WHERE id=?').get(owner);assert(user?.email.endsWith('@example.test'),'Only synthetic QA accounts may be seeded');
  const row=db.prepare('SELECT worker_id,payload FROM attempts WHERE owner=? AND id=?').get(owner,id);if(!row)return;
  const attempt=JSON.parse(row.payload),catalog=JSON.parse(readFileSync(new URL('../lib/scenario-catalog.json',import.meta.url),'utf8'))['2'],steps=catalog[attempt.moduleId];if(!steps)return;
  const marker='course-qa-'+attempt.moduleId;if(db.prepare('SELECT 1 FROM learning_snapshots WHERE owner=? AND worker_id=? AND device_id=?').get(owner,row.worker_id,marker))return;
  db.prepare('INSERT INTO learning_snapshots VALUES(?,?,?,?,?,?)').run(owner,row.worker_id,marker,1,JSON.stringify({contentVersion:'0.4.0',lessons:[attempt.moduleId]}),Date.now());
  for(const guided of [true,false]){let at=Date.now()-10000;const createdAt=at,events=[],flags={};for(const step of steps){events.push({type:'action',sequence:events.length+1,step,action:step,presentation:'description',correct:true,time:++at});flags[step]=true;events.push({type:'advance',sequence:events.length+1,step,time:++at})}
   const record={schemaVersion:1,catalogVersion:2,id:randomUUID(),workerId:row.worker_id,module:attempt.moduleId,guided,createdAt,updatedAt:at,index:steps.length-1,finished:true,feedback:false,lastCorrect:true,flags,events,criticalFailures:[],completedAt:at,result:{complete:true,stopped:false,criticalFailures:[],practical:'not-assessed',certifiable:false}};
   db.prepare('INSERT INTO procedure_evidence VALUES(?,?,?,?,?)').run(owner,record.id,row.worker_id,JSON.stringify(record),at);
  }
 }finally{db.close()}
}
