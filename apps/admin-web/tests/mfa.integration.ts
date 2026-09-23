import assert from 'node:assert/strict';
import {account,localDatabase} from './auth-client.mjs';
import {base32} from '@better-auth/utils/base32';
import {createOTP} from '@better-auth/utils/otp';
const user=await account();const base='http://localhost:5173';const jar=new Map(user.cookie.split('; ').map((v:string)=>{const i=v.indexOf('=');return [v.slice(0,i),v.slice(i+1)]}));
async function call(path:string,body?:unknown){const r=await fetch(base+'/api/auth/'+path,{method:body?'POST':'GET',headers:{Origin:base,'Content-Type':'application/json',Cookie:[...jar].map(([k,v])=>k+'='+v).join('; ')},body:body?JSON.stringify(body):undefined});for(const c of r.headers.getSetCookie()){const [kv]=c.split(';'),i=kv.indexOf('=');if(/Max-Age=0(?:;|$)/.test(c))jar.delete(kv.slice(0,i));else jar.set(kv.slice(0,i),kv.slice(i+1))}return {status:r.status,data:await r.json() as any}}
try{
 const enabled=await call('two-factor/enable',{password:user.password});assert.equal(enabled.status,200);assert(enabled.data.backupCodes.length>0);const uri=new URL(enabled.data.totpURI);const secret=uri.searchParams.get('secret')!;
 const otp=await createOTP(new TextDecoder().decode(base32.decode(secret))).totp();const verify=await call('two-factor/verify-totp',{code:otp});assert.equal(verify.status,200,JSON.stringify(verify));
 await call('sign-out',{});jar.clear();const first=await call('sign-in/email',{email:user.email,password:user.password,rememberMe:false});assert.equal(first.data.twoFactorRedirect,true);
 assert.equal((await call('get-session')).data,null,'Password alone must not grant a session');
 const invalid=await call('two-factor/verify-totp',{code:'invalid'});assert(invalid.status>=400);
 const backup=await call('two-factor/verify-backup-code',{code:enabled.data.backupCodes[0]});assert.equal(backup.status,200,JSON.stringify(backup));assert.equal((await call('get-session')).data.user.id,user.userId);
 await call('sign-out',{});jar.clear();await call('sign-in/email',{email:user.email,password:user.password});assert((await call('two-factor/verify-backup-code',{code:enabled.data.backupCodes[0]})).status>=400,'Recovery code must be single-use');
 console.log('MFA integration PASS: verified enrolment, password-only session denial, invalid factor rejection, recovery sign-in and recovery replay rejection.');
}finally{const db=localDatabase();db.prepare('DELETE FROM auth_two_factor WHERE user_id=?').run(user.userId);db.prepare('DELETE FROM auth_user WHERE id=?').run(user.userId);db.close()}
