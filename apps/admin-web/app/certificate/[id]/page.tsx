import {curriculum} from '@/lib/grading';
import {getAppUser} from '@/lib/auth';
import {access,db} from '@/lib/server';
import {credentialView,verify} from '@/lib/credentials';
import {env} from 'cloudflare:workers';
import QRCode from 'qrcode';
import {PrintButton} from '@/components/training/PrintButton';
export const dynamic='force-dynamic';
export default async function Page({params}:{params:Promise<{id:string}>}){
 const {id}=await params;const user=await getAppUser();if(!user)return <main className="learner-page"><h1>Sign in to view this credential</h1><a href={'/login?next='+encodeURIComponent('/certificate/'+id)}>Sign in</a></main>;
 if(!/^[0-9a-f-]{36}$/i.test(id))return <p>Invalid credential reference.</p>;
 const row=await db().prepare('SELECT c.* FROM credentials c JOIN attempts p ON p.owner=c.owner AND p.id=c.attempt_id WHERE c.id=? AND EXISTS(SELECT 1 FROM learner_links l WHERE l.owner=c.owner AND l.worker_id=p.worker_id AND l.user_id=?)').bind(id,user.userId).first<{id:string;token:string;issued_at:number;revoked_at:number|null}>();
 let record=row;if(!record){try{const a=await access();record=await db().prepare('SELECT * FROM credentials WHERE owner=? AND id=?').bind(a.owner,id).first()}catch{/* No cross-workspace fallback. */}}
 if(!record)return <main className="learner-page"><h1>Credential unavailable</h1><p>This account does not have access to the requested record.</p></main>;
 const checked=await verify(record.token),c=credentialView(record);
 const origin=(env as unknown as Record<string,string>).BETTER_AUTH_URL;
 const url=new URL('/verify?id='+encodeURIComponent(id),origin).href;
 const qr=await QRCode.toDataURL(url,{width:240,margin:2});
 return <main className="certificate"><p>SURAKSHAAR · SIGNED PILOT RECORD</p><h1>Simulation training credential</h1><h2>{checked.payload.workerName}</h2><dl><dt>Module</dt><dd>{curriculum.modules.find(m=>m.id===c.moduleId)?.title[0]??c.moduleId}</dd><dt>Issuer / centre</dt><dd>{c.centreName}</dd><dt>Current status</dt><dd>{c.status}</dd><dt>Issued</dt><dd>{new Date(c.issued_at).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric',timeZone:'Asia/Kolkata'})}</dd><dt>Valid until</dt><dd>{c.expiresAt?new Date(c.expiresAt).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric',timeZone:'Asia/Kolkata'}):'Not recorded'}</dd><dt>Assessment score</dt><dd>{c.score}%</dd><dt>Credential reference</dt><dd>{id}</dd></dl>
 {/* Generated locally; contains only the verification URL, no third-party image request. */}
 {/* eslint-disable-next-line @next/next/no-img-element */}
 <img src={qr} alt="QR for current credential status"/><p><a href={'/verify?id='+id}>Verify current status</a></p><p>This signed record covers pilot simulation assessment. It is not a statutory safety qualification or permission to work. Practical competence is not certified by this document. Local verification requires the training server to be reachable.</p><p className="fine">Status checked {new Date().toLocaleString('en-IN',{timeZone:'Asia/Kolkata'})} IST. Scan again for later revocation or expiry changes.</p><PrintButton/></main>;
}
