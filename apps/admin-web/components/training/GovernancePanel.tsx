'use client';
import {useCallback,useEffect,useRef,useState} from 'react';
import {requestJson} from '@/lib/client-api';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
type Centre={owner:string;name:string;site:string;status:string;reason:string};
export function GovernancePanel({onChange}:{onChange:()=>Promise<void>}){
 const [rows,setRows]=useState<Centre[]>([]),[operator,setOperator]=useState(false),[error,setError]=useState(''),[busy,setBusy]=useState(false),[loaded,setLoaded]=useState(false),[message,setMessage]=useState('');const pending=useRef(false);
 const load=useCallback(async()=>{const d=await requestJson<{operator:boolean;centres:Centre[]}>('/api/governance');setOperator(d.operator);setRows(d.centres);setLoaded(true)},[]);
 useEffect(()=>{void Promise.resolve().then(load).catch(e=>setError(e.message))},[load]);
 async function save(body:unknown){if(pending.current)return;pending.current=true;setBusy(true);setError('');setMessage('');try{await requestJson('/api/governance','POST',body);await load();await onChange();setMessage('Centre status saved. Review the status below.')}catch(e){setError(e instanceof Error?e.message:'Could not save')}finally{pending.current=false;setBusy(false)}}
 return <section className="panel p-6"><h2>Centre access and approval</h2><p>Accounts do not grant administration or certification authority. Approved centres invite staff with explicit roles. Offline learning remains available without centre access.</p>
 {error&&<p className="notice error" role="alert">{error} <Button variant="outline" onClick={()=>void Promise.resolve().then(load).catch(e=>setError(e.message))}>Retry</Button></p>}{message&&<p className="notice success" role="status">{message}</p>}
 {!loaded?<p role="status">Loading centre status…</p>:<>
 {operator&&<p className="notice">Platform operator · review applications from other owners. Approval authorizes this pilot workspace; it does not represent government accreditation. Sensitive changes require signing in within the last 15 minutes.</p>}
 {!operator&&(rows.length===0||rows.every(r=>r.status==='rejected'))&&<form className="admin-form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);void save({action:'request',name:f.get('name'),site:f.get('site')})}}><label>Centre name<Input name="name" required minLength={2} maxLength={100}/></label><label>Site / organisation<Input name="site" required minLength={2} maxLength={150}/></label><Button disabled={busy}>Request centre approval</Button><p className="fine">Already invited? Use the account menu → Join a centre. An operator reviews new centres before any administrative access is granted.</p></form>}
 {rows.map(r=><article className="rounded-xl border p-4 my-4" key={r.owner}><h3>{r.name}</h3><p>{r.site}</p><p><strong>Status: {r.status}</strong></p>{r.reason&&<p>Review note: {r.reason}</p>}{operator&&<><p className="fine break-all">Applicant account: {r.owner}</p><form className="admin-form" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);void save({action:f.get('action'),owner:r.owner,reason:f.get('reason')})}}><label>Decision<select name="action">{r.status==='approved'?<option value="suspend">Suspend centre access</option>:r.status==='pending'?<><option value="approve">Approve centre</option><option value="reject">Reject application</option></>:r.status==='suspended'?<option value="approve">Restore approved centre</option>:<option value="reject">Await a new application</option>}</select></label><label>Review reason<Input name="reason" required minLength={10} maxLength={500}/></label><Button disabled={busy||r.status==='rejected'}>Record decision</Button></form></>}</article>)}
 {!rows.length&&operator&&<p>No centre applications yet.</p>}
 </>}
 </section>
}
