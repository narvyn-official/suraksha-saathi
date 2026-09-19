"use client";
import { useRef, useState } from "react";
import { ShieldCheck, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "./PasswordInput";
import { requestJson, RequestError } from "@/lib/client-api";
type Mode = "login" | "signup" | "forgot" | "reset";
export default function Login({mode="login"}:{mode?:Mode}) {
 const [signup,setSignup]=useState(mode==='signup'),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 const pending=useRef(false);
 const forgot=mode==='forgot',reset=mode==='reset';
 async function submit(e:React.FormEvent<HTMLFormElement>) {
  e.preventDefault();if(pending.current)return;pending.current=true;
  const form=e.currentTarget,values=new FormData(form);setBusy(true);setError('');setMessage('');
  try {
   if(forgot){await requestJson('/api/auth/request-password-reset','POST',{email:values.get('email')});setMessage('Request received. If this address has an account, check your inbox for a reset link. If it does not arrive, retry or contact your training centre.');}
   else if(reset){const token=new URLSearchParams(window.location.hash.slice(1)).get('token');if(!token)throw new Error('This reset link is missing or invalid. Request a new link.');
    await requestJson('/api/auth/reset-password','POST',{token,newPassword:values.get('password')});window.history.replaceState(null,'','/reset-password');form.reset();setMessage('Password updated. All previous sessions have ended. Sign in with your new password.');}
   else {await requestJson(`/api/auth/${signup?'sign-up':'sign-in'}/email`,'POST',{email:values.get('email'),password:values.get('password'),...(signup?{name:values.get('name')}:{rememberMe:values.get('remember')==='on'})});window.location.assign('/');}
  } catch(e){setError(e instanceof RequestError&&e.status===401?'Check your email and password, then try again.':e instanceof Error?e.message:'Could not connect. Try again.');}
  finally{pending.current=false;setBusy(false);}
 }
 const heading=forgot?'Reset your password':reset?'Choose a new password':signup?'Create your account':'Welcome back';
 return <main className="account-page"><div className="account-brand"><span className="brand-mark"><ShieldCheck size={26}/></span><span>SurakshaAr<small>TRAINING CENTRE</small></span></div>
 <section className="account-card" aria-labelledby="account-title"><div className="account-intro"><span className="eyebrow">YOUR WORKSPACE</span><h1 id="account-title">{heading}</h1><p>{forgot?'Enter your account email to request a private reset link.':reset?'Use at least 15 characters. Resetting your password signs out all existing sessions.':signup?'Set up your own training centre or join your team with an invitation code.':'Sign in to manage your team’s training.'}</p></div>
 {!(reset&&message)&&<form className="account-form" onSubmit={submit} aria-busy={busy}>
 {signup&&!forgot&&!reset&&<label htmlFor="account-name">Full name<Input id="account-name" name="name" autoComplete="name" required maxLength={80} disabled={busy}/></label>}
 {!reset&&<label htmlFor="account-email">Email address<Input id="account-email" name="email" type="email" autoComplete="username" required maxLength={254} placeholder="you@organisation.in" disabled={busy}/></label>}
 {!forgot&&<div><label htmlFor="account-password">Password</label><PasswordInput id="account-password" name="password" autoComplete={signup||reset?'new-password':'current-password'} required minLength={signup||reset?15:1} maxLength={128} placeholder={signup||reset?'At least 15 characters':'Enter your password'} disabled={busy}/></div>}
 {!forgot&&!reset&&!signup&&<label className="remember-choice"><input name="remember" type="checkbox" disabled={busy}/> Keep me signed in on this private device</label>}
 {error&&<p className="notice error" role="alert">{error}</p>}
 <Button className="account-submit" type="submit" disabled={busy}>{busy?'Please wait…':forgot?'Request reset link':reset?'Save new password':signup?'Create account':'Sign in'}{!busy&&<ArrowRight size={18}/>}</Button>
 </form>}
 {message&&<p className="notice success" role="status">{message}</p>}
 <div className="account-switch">{forgot||reset?<a href="/login">Back to sign in</a>:<><span>{signup?'Already have an account?':'New to SurakshaAr?'}</span><Button variant="link" disabled={busy} onClick={()=>{setSignup(!signup);setError('');setMessage('')}}>{signup?'Sign in':'Create an account'}</Button></>}</div>
 {!forgot&&(!signup||reset)&&!(reset&&message)&&<p><a href="/forgot-password">{reset?'Request a new reset link':'Forgot password?'}</a></p>}
 </section><p className="account-offline">Use the Android app for offline lessons and AR practice.<br/>Your training centre account requires a connection.</p></main>;
}
