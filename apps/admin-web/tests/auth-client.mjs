import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
export async function account(){
 const email=`qa-${randomUUID()}@example.test`, password=`Test-${randomUUID()}`;
 const r=await fetch('http://localhost:5173/api/auth/sign-up/email',{method:'POST',headers:{'Content-Type':'application/json',Origin:'http://localhost:5173'},body:JSON.stringify({name:'QA account',email,password})});
 const data=await r.json();assert.equal(r.status,200,JSON.stringify(data));
 const cookie=r.headers.getSetCookie().filter(c=>!/Max-Age=0(?:;|$)/i.test(c)).map(c=>c.split(';')[0]).join('; ');
 assert(cookie.includes('session_token'),'Expected independent session cookie');
 return {email,password,cookie,userId:data.user.id};
}
