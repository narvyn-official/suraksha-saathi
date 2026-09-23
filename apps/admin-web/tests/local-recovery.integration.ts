import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {account,localDatabase} from './auth-client.mjs';
const origin='http://localhost:5173',user=await account();
const key=readFileSync('.dev.vars','utf8').match(/^RECOVERY_MAIL_KEY=(.*)$/m)![1].trim();
async function call(path:string,body:unknown,cookie=user.cookie){const r=await fetch(origin+'/api/auth/'+path,{method:'POST',headers:{Origin:origin,'Content-Type':'application/json',Cookie:cookie},body:JSON.stringify(body)});return {status:r.status,data:await r.json() as any}}
async function mail(subject:string){const r=await fetch('http://127.0.0.1:5188/messages',{headers:{Authorization:'Bearer '+key}});assert.equal(r.status,200);const values=await r.json() as {to:string;subject:string;text:string}[];const value=values.findLast(m=>m.to===user.email&&m.subject.includes(subject));assert(value);return value}
try{
 assert.equal((await call('request-password-reset',{email:user.email})).status,200);
 const reset=await mail('Reset'),link=new URL(reset.text.match(/http:\/\/localhost:5173\/[^\s]+/)![0]);assert.equal(link.pathname,'/reset-password');assert.equal(link.search,'');const token=new URLSearchParams(link.hash.slice(1)).get('token');assert(token);
 const newPassword='Recovered-'+user.password;assert.equal((await call('reset-password',{token,newPassword})).status,200);
 assert.equal((await fetch(origin+'/api/learner',{headers:{Cookie:user.cookie}})).status,401,'Reset must revoke previous sessions');
 assert((await call('reset-password',{token,newPassword})).status>=400,'Reset link must be single-use');
 assert((await call('sign-in/email',{email:user.email,password:user.password},'')).status>=400);
 assert.equal((await call('sign-in/email',{email:user.email,password:newPassword},'')).status,200);
 assert.equal((await call('send-verification-email',{email:user.email,callbackURL:'/learn'},'')).status,200);
 const verification=await mail('Verify'),url=new URL(verification.text.match(/http:\/\/localhost:5173\/[^\s]+/)![0]);assert.equal(url.origin,origin);
 const verified=await fetch(url,{redirect:'manual'});assert([200,302].includes(verified.status));
 const db=localDatabase();try{assert.equal(db.prepare('SELECT email_verified FROM auth_user WHERE id=?').get(user.userId)?.email_verified,1)}finally{db.close()}
 console.log('Local recovery PASS: loopback inbox delivery, token fragment, one-use reset, old-session revocation, new-password login and email verification.');
}finally{const db=localDatabase();db.prepare('DELETE FROM auth_user WHERE id=?').run(user.userId);db.close()}
