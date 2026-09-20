import {db,access,audit,failure,json,signingKey} from '@/lib/server';
import {freshUser} from '@/lib/auth';
import {sign,trust,credentialView} from '@/lib/credentials';
import {requestedExpiry} from '@/lib/validity';
import {eligibleAssessment} from '@/lib/certification';
import {z} from 'zod';
const command=z.discriminatedUnion('action',[
 z.object({action:z.literal('request'),attemptId:z.string().uuid(),expiresAt:z.number().int(),note:z.string().trim().min(10).max(500)}).strict(),
 z.object({action:z.enum(['approve','reject']),requestId:z.string().uuid(),reason:z.string().trim().min(10).max(500)}).strict()
]);
type Review={id:string;owner:string;attempt_id:string;evidence_digest:string;expires_at:number;requested_by:string;requested_at:number;request_note:string;status:string;credential_id:string|null};
export async function GET(){try{
 const a=await access();
 const rows=await db().prepare("SELECT q.*,w.name AS worker_name,json_extract(p.payload,'$.moduleId') AS module_id,json_extract(p.payload,'$.result.score') AS score FROM certification_requests q JOIN attempts p ON p.owner=q.owner AND p.id=q.attempt_id JOIN workers w ON w.owner=p.owner AND w.id=p.worker_id WHERE q.owner=? ORDER BY q.requested_at DESC LIMIT 500").bind(a.owner).all();
 return Response.json({requests:rows.results},{headers:{'Cache-Control':'no-store'}});
}catch(e){return failure(e)}}
export async function POST(request:Request){try{
 const input=command.safeParse(await json(request));if(!input.success)throw new Error('Invalid certification action. Submit a request with evidence notes; a separate certifier must approve it. Update older clients.');
 const c=input.data,a=await access(c.action==='request'?'write':'certify'),now=Date.now();
 if(c.action==='request'){
  const evidence=await eligibleAssessment(a.owner,c.attemptId),expires=requestedExpiry(c.expiresAt,now);
  if(expires>now+366*86400000)throw new Error('Invalid expiry: pilot credentials may cover at most one year.');
  if(await db().prepare('SELECT id FROM credentials WHERE owner=? AND attempt_id=?').bind(a.owner,c.attemptId).first())throw new Error('Already issued: reassessment is required for renewal.');
  const id=crypto.randomUUID();
  await db().batch([db().prepare("INSERT INTO certification_requests(id,owner,attempt_id,evidence_digest,expires_at,requested_by,requested_at,request_note,status) VALUES(?,?,?,?,?,?,?,?, 'pending') ON CONFLICT(owner,attempt_id) DO NOTHING").bind(id,a.owner,c.attemptId,evidence.digest,expires,a.user.userId,now,c.note),audit(a,'certification.request',id,{attemptId:c.attemptId,expiresAt:expires,note:c.note},true)]);
  const actual=await db().prepare('SELECT * FROM certification_requests WHERE owner=? AND attempt_id=?').bind(a.owner,c.attemptId).first<Review>();
  if(!actual||actual.expires_at!==expires||actual.requested_by!==a.user.userId||actual.request_note!==c.note)throw new Error('Record conflict: this assessment already has a different certification request.');
  if(actual.status!=='pending')throw new Error('Already reviewed: complete a new assessment before submitting again.');
  return Response.json({request:actual},{status:202});
 }
 await freshUser();
 const q=await db().prepare('SELECT * FROM certification_requests WHERE owner=? AND id=?').bind(a.owner,c.requestId).first<Review>();
 if(!q)throw new Error('No certification request found.');
 if(q.requested_by===a.user.userId)throw new Error('Forbidden: a different person must review this request.');
 if(q.status!=='pending')throw new Error('Already reviewed: refresh the queue.');
 if(c.action==='reject'){
  const result=await db().batch([db().prepare("UPDATE certification_requests SET status='rejected',reviewed_by=?,reviewed_at=?,review_reason=? WHERE owner=? AND id=? AND status='pending'").bind(a.user.userId,now,c.reason,a.owner,q.id),audit(a,'certification.reject',q.id,{reason:c.reason},true)]);
  if(!result[0].meta.changes)throw new Error('Already reviewed: refresh the queue.');return Response.json({rejected:true});
 }
 const evidence=await eligibleAssessment(a.owner,q.attempt_id);
 if(evidence.digest!==q.evidence_digest)throw new Error('Record conflict: evidence changed after the request.');
 requestedExpiry(q.expires_at,now);
 const centre=await db().prepare('SELECT name,site FROM training_centres WHERE owner=?').bind(a.owner).first<{name:string;site:string}>();
 if(!centre)throw new Error('No approved centre record.');
 const id=crypto.randomUUID(),attempt=evidence.attempt;
 const token=await sign({iss:trust.issuer,id,attemptId:attempt.id,workerRef:attempt.workerId,workerName:evidence.workerName,moduleId:attempt.moduleId,contentVersion:attempt.contentVersion,score:attempt.result.score,kind:'pilot-simulation',mode:attempt.mode,practical:'not-assessed',iat:now,expiresAt:q.expires_at,governanceVersion:1,centreRef:a.owner,centreName:centre.name,centreSite:centre.site,requestId:q.id,requestedBy:q.requested_by,approvedBy:a.user.userId,evidenceDigest:q.evidence_digest},signingKey());
 // Recheck authority, queue state and latest evidence in the atomic insertion.
 const inserted=await db().batch([
 db().prepare(`INSERT INTO credentials(owner,id,attempt_id,token,issued_at) SELECT ?,?,?,?,? WHERE EXISTS(SELECT 1 FROM certification_requests WHERE owner=? AND id=? AND status='pending') AND EXISTS(SELECT 1 FROM centre_approvals WHERE owner=? AND status='approved') AND EXISTS(SELECT 1 FROM team_members WHERE owner=? AND user_id=? AND active=1 AND role='certifier') AND ?=(SELECT id FROM attempts WHERE owner=? AND worker_id=? AND json_extract(payload,'$.moduleId')=? AND json_extract(payload,'$.kind')='assessment' ORDER BY json_extract(payload,'$.endedAt') DESC,id DESC LIMIT 1) ON CONFLICT(owner,attempt_id) DO NOTHING`).bind(a.owner,id,attempt.id,token,now,a.owner,q.id,a.owner,a.owner,a.user.userId,attempt.id,a.owner,attempt.workerId,attempt.moduleId),
 db().prepare("UPDATE certification_requests SET status='approved',reviewed_by=?,reviewed_at=?,review_reason=?,credential_id=? WHERE owner=? AND id=? AND status='pending' AND EXISTS(SELECT 1 FROM credentials WHERE owner=? AND id=?)").bind(a.user.userId,now,c.reason,id,a.owner,q.id,a.owner,id),
 audit(a,'credential.issue',id,{requestId:q.id,attemptId:attempt.id,expiresAt:q.expires_at,reason:c.reason,evidenceDigest:q.evidence_digest},true)]);
 if(!inserted[0].meta.changes)throw new Error('Record conflict: request, authority or latest evidence changed. Refresh before reviewing.');
 return Response.json(credentialView({id,attempt_id:attempt.id,token,issued_at:now,revoked_at:null,reason:null}));
}catch(e){return failure(e)}}
export async function PATCH(request:Request){try{
 const a=await access('revoke');await freshUser();
 const input=z.object({id:z.string().uuid(),reason:z.string().trim().min(10).max(500)}).strict().safeParse(await json(request));
 if(!input.success)throw new Error('Invalid revocation reason. Use 10–500 characters.');
 const c=input.data,result=await db().batch([db().prepare('UPDATE credentials SET revoked_at=?,reason=? WHERE owner=? AND id=? AND revoked_at IS NULL').bind(Date.now(),c.reason,a.owner,c.id),audit(a,'credential.revoke',c.id,{reason:c.reason},true)]);
 if(!result[0].meta.changes)throw new Error('No active credential found.');return Response.json({revoked:true});
}catch(e){return failure(e)}}
