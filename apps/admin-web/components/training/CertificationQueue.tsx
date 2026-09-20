'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import {requestJson} from '@/lib/client-api';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import type{AdminSession}from './AdminPanel';
type Item={id:string;attempt_id:string;worker_name:string;module_id:string;score:number;expires_at:number;requested_by:string;request_note:string;status:string;review_reason:string|null;credential_id:string|null};
export function CertificationQueue({session,refreshVersion,onChange,onReview}:{session:AdminSession;refreshVersion:number;onChange:()=>Promise<void>;onReview:(id:string)=>void}){
 const [rows,setRows]=useState<Item[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false),[loaded,setLoaded]=useState(false),[message,setMessage]=useState('');const pending=useRef(false);
 const load=useCallback(async()=>{const d=await requestJson<{requests:Item[]}>('/api/credentials');setRows(d.requests);setLoaded(true);setError('')},[]);
 useEffect(()=>{void Promise.resolve().then(load).catch(e=>{setRows([]);setError(e.message)})},[load,refreshVersion]);
 async function decide(id:string,form:FormData){if(pending.current)return;pending.current=true;setBusy(true);setError('');setMessage('');try{await requestJson('/api/credentials','POST',{action:form.get('action'),requestId:id,reason:form.get('reason')});await load();await onChange();setMessage(form.get('action')==='approve'?'Pilot credential approved and issued.':'Request rejected. The reason is saved below.')}catch(e){setError(e instanceof Error?e.message:'Review failed')}finally{pending.current=false;setBusy(false)}}
 return <section className="panel p-6 mb-6"><h2>Certification review queue</h2><p>Trainer request → independent certifier review → signed pilot credential. Review the assessment answers before deciding. Practical competence and identity are not established by imported app events.</p>
 {message&&<p role="status" className="notice success">{message}</p>}
 {error&&<p className="notice error" role="alert">{error}<Button variant="outline" onClick={()=>void Promise.resolve().then(load).catch(e=>setError(e.message))}>Retry</Button></p>}
 {!loaded&&!error&&<p role="status">Loading review queue…</p>}{loaded&&!rows.length&&<p>No certification requests. Trainers submit a latest passed assessment from its review screen.</p>}
 {rows.map(r=><article className="rounded-xl border p-4 my-4" key={r.id}><h3>{r.worker_name} · {r.module_id}</h3><p>{r.score}% · <strong>{r.status}</strong> · Requested validity ends {new Date(r.expires_at).toLocaleString()}</p><p>Evidence note: {r.request_note}</p>{r.review_reason&&<p>Decision: {r.review_reason}</p>}<Button variant="outline" onClick={()=>onReview(r.attempt_id)}>Review assessment answers</Button>
 {r.status==='pending'&&session.current?.role==='certifier'&&r.requested_by!==session.user.userId&&<form className="admin-form" onSubmit={e=>{e.preventDefault();void decide(r.id,new FormData(e.currentTarget))}}><label>Review decision<select name="action"><option value="reject">Reject request</option><option value="approve">Approve and issue pilot credential</option></select></label><label>Evidence reviewed / decision reason<Input name="reason" minLength={10} maxLength={500} required/></label><p className="fine">Approval rechecks current centre access, your role and the latest assessment. A recent sign-in is required. A rejected request is retained; the learner needs a new eligible assessment.</p><Button disabled={busy}>Confirm review decision</Button></form>}
 {r.status==='pending'&&r.requested_by===session.user.userId&&<p className="fine">Waiting for a different authorized certifier. You cannot approve your own request.</p>}
 </article>)}
 </section>
}
