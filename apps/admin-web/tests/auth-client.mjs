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
import {readdirSync} from 'node:fs';
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
