"use client";
import {SecuritySettings} from "./SecuritySettings";
import {useRef,useState} from 'react';
import {Button} from '@/components/ui/button';
import {PasswordInput} from './PasswordInput';
import {requestJson} from '@/lib/client-api';
export async function signOut(){await requestJson('/api/auth/sign-out','POST',{});window.location.assign('/login');}
export function AccountActions(){
 const [error,setError]=useState(''),[busy,setBusy]=useState(false),[message,setMessage]=useState('');const pending=useRef(false);
 return <><form className="admin-form" aria-busy={busy} onSubmit={async e=>{e.preventDefault();if(pending.current)return;pending.current=true;
 const form=e.currentTarget,values=new FormData(form);setBusy(true);setError('');setMessage('');
 try{await requestJson('/api/auth/change-password','POST',{currentPassword:values.get('current'),newPassword:values.get('new'),revokeOtherSessions:true});form.reset();setMessage('Password updated. Other sessions were signed out.');}
 catch(e){setError(e instanceof Error?e.message:'Could not update password.');}finally{pending.current=false;setBusy(false)}}}>
 <h3>Account security</h3><div><label htmlFor="current-password">Current password</label><PasswordInput id="current-password" name="current" autoComplete="current-password" required disabled={busy}/></div>
 <div><label htmlFor="new-password">New password</label><PasswordInput id="new-password" name="new" autoComplete="new-password" minLength={15} maxLength={128} required disabled={busy}/></div><p className="muted">Use at least 15 characters. Other sessions will be signed out.</p>
 {error&&<p role="alert">{error}</p>}{message&&<p role="status">{message}</p>}<Button disabled={busy} type="submit">{busy?'Updating…':'Update password'}</Button>
 <Button type="button" variant="outline" disabled={busy} onClick={async()=>{if(pending.current)return;pending.current=true;setBusy(true);try{await signOut()}catch(e){setError(e instanceof Error?e.message:'Could not sign out.')}finally{pending.current=false;setBusy(false)}}}>Sign out</Button></form><SecuritySettings/></>;
}
