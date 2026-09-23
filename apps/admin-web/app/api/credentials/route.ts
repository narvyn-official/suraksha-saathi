import {db,access,audit,failure,json,signingKey} from '@/lib/server';
import {freshUser} from '@/lib/auth';
import {sign,trust,credentialView} from '@/lib/credentials';
import {requestedExpiry} from '@/lib/validity';
import {eligibleAssessment} from '@/lib/certification';
import {z} from 'zod';
import {courseEvidence} from '@/lib/course-readiness';
import {reviewRubric} from '@/lib/journey';
const command=z.discriminatedUnion('action',[
 z.object({action:z.literal('request'),attemptId:z.string().uuid(),expiresAt:z.number().int(),note:z.string().trim().min(10).max(500)}).strict(),
 z.object({action:z.literal('clarify'),requestId:z.string().uuid(),reason:z.string().trim().min(10).max(500)}).strict(),
 z.object({action:z.enum(['approve','reject','needs-information']),requestId:z.string().uuid(),reason:z.string().trim().min(10).max(500),rubric:reviewRubric.optional()}).strict()
]);
type Review={id:string;owner:string;attempt_id:string;evidence_digest:string;expires_at:number;requested_by:string;requested_at:number;request_note:string;status:string;credential_id:string|null;workflow_version:number;learning_digest:string|null};
export async function GET(request:Request){try{
 const a=await access();
 const requestId=new URL(request.url).searchParams.get('requestId');
 if(requestId){
 const record=await db().prepare('SELECT p.worker_id,p.payload FROM certification_requests q JOIN attempts p ON p.owner=q.owner AND p.id=q.attempt_id WHERE q.owner=? AND q.id=?').bind(a.owner,requestId).first<{worker_id:string;payload:string}>();if(!record)throw new Error('No certification request found.');
 const moduleId=JSON.parse(record.payload).moduleId;
 const [procedures,observations]=await Promise.all([db().prepare("SELECT payload FROM procedure_evidence WHERE owner=? AND worker_id=? AND json_extract(payload,'$.module')=? ORDER BY updated_at DESC,id DESC LIMIT 100").bind(a.owner,record.worker_id,moduleId).all<{payload:string}>(),db().prepare('SELECT o.*,u.name AS instructor_name FROM practical_observations o LEFT JOIN auth_user u ON u.id=o.instructor_id WHERE o.owner=? AND o.worker_id=? AND o.module_id=? ORDER BY o.observed_at DESC LIMIT 100').bind(a.owner,record.worker_id,moduleId).all()]);
 const events=await db().prepare('SELECT action,note,at,actor FROM certification_events WHERE owner=? AND request_id=? ORDER BY at,id').bind(a.owner,requestId).all();return Response.json({events:events.results,procedures:procedures.results.map(r=>JSON.parse(r.payload)),observations:observations.results},{headers:{'Cache-Control':'no-store'}})}
 const rows=await db().prepare("SELECT q.*,w.name AS worker_name,json_extract(p.payload,'$.moduleId') AS module_id,json_extract(p.payload,'$.result.score') AS score FROM certification_requests q JOIN attempts p ON p.owner=q.owner AND p.id=q.attempt_id JOIN workers w ON w.owner=p.owner AND w.id=p.worker_id WHERE q.owner=? ORDER BY q.requested_at DESC LIMIT 500").bind(a.owner).all();
 return Response.json({requests:rows.results},{headers:{'Cache-Control':'no-store'}});
}catch(e){return failure(e)}}
export async function POST(request:Request){try{
 const input=command.safeParse(await json(request));if(!input.success)throw new Error('Invalid certification action. Submit a request with evidence notes; a separate certifier must approve it. Update older clients.');
 const c=input.data,a=await access(c.action==='request'||c.action==='clarify'?'write':'certify'),now=Date.now();
 if(c.action==='request'){
  const evidence=await eligibleAssessment(a.owner,c.attemptId),expires=requestedExpiry(c.expiresAt,now);
  if(expires>now+366*86400000)throw new Error('Invalid expiry: pilot credentials may cover at most one year.');
  if(await db().prepare('SELECT id FROM credentials WHERE owner=? AND attempt_id=?').bind(a.owner,c.attemptId).first())throw new Error('Already issued: reassessment is required for renewal.');
  const course=await courseEvidence(a.owner,evidence.attempt.workerId,evidence.attempt.moduleId);
  const id=crypto.randomUUID();
  await db().batch([db().prepare("INSERT INTO certification_requests(id,owner,attempt_id,evidence_digest,expires_at,requested_by,requested_at,request_note,status,workflow_version,learning_digest) VALUES(?,?,?,?,?,?,?,?, 'pending',2,?) ON CONFLICT(owner,attempt_id) DO NOTHING").bind(id,a.owner,c.attemptId,evidence.digest,expires,a.user.userId,now,c.note,course.digest),audit(a,'certification.request',id,{attemptId:c.attemptId,expiresAt:expires,note:c.note},true)]);
  const actual=await db().prepare('SELECT * FROM certification_requests WHERE owner=? AND attempt_id=?').bind(a.owner,c.attemptId).first<Review>();
  if(!actual||actual.expires_at!==expires||actual.requested_by!==a.user.userId||actual.request_note!==c.note)throw new Error('Record conflict: this assessment already has a different certification request.');
  if(actual.id===id)await db().prepare('INSERT INTO certification_events VALUES(?,?,?,?,?,?,?)').bind(crypto.randomUUID(),id,a.owner,a.user.userId,'submitted',c.note,now).run();
  if(actual.status!=='pending')throw new Error('Already reviewed: complete a new assessment before submitting again.');
  return Response.json({request:actual},{status:202});
 }
 if(c.action==='clarify'){
 const q=await db().prepare("SELECT * FROM certification_requests WHERE owner=? AND id=? AND status='needs-information' AND requested_by=?").bind(a.owner,c.requestId,a.user.userId).first<Review>();if(!q)throw new Error('No request awaiting information from this requester.');
 const evidence=await eligibleAssessment(a.owner,q.attempt_id),course=await courseEvidence(a.owner,evidence.attempt.workerId,evidence.attempt.moduleId);
 const changed=await db().batch([db().prepare("UPDATE certification_requests SET status='pending',learning_digest=?,workflow_version=2,request_note=?,reviewed_by=NULL,reviewed_at=NULL WHERE owner=? AND id=? AND status='needs-information'").bind(course.digest,c.reason,a.owner,q.id),audit(a,'certification.clarification',q.id,{note:c.reason},true)]);if(!changed[0].meta.changes)throw new Error('Record conflict: refresh the queue.');
 await db().prepare('INSERT INTO certification_events VALUES(?,?,?,?,?,?,?)').bind(crypto.randomUUID(),q.id,a.owner,a.user.userId,'clarification',c.reason,now).run();return Response.json({submitted:true});
 }
 await freshUser();
 const q=await db().prepare('SELECT * FROM certification_requests WHERE owner=? AND id=?').bind(a.owner,c.requestId).first<Review>();
 if(!q)throw new Error('No certification request found.');
 if(q.requested_by===a.user.userId)throw new Error('Forbidden: a different person must review this request.');
 if(q.status!=='pending')throw new Error('Already reviewed: refresh the queue.');
 if(c.action==='reject'||c.action==='needs-information'){
  const result=await db().batch([db().prepare("UPDATE certification_requests SET status=?,reviewed_by=?,reviewed_at=?,review_reason=? WHERE owner=? AND id=? AND status='pending'").bind(c.action==='reject'?'rejected':'needs-information',a.user.userId,now,c.reason,a.owner,q.id),audit(a,'certification.'+c.action,q.id,{reason:c.reason},true)]);
  if(!result[0].meta.changes)throw new Error('Already reviewed: refresh the queue.');await db().prepare('INSERT INTO certification_events VALUES(?,?,?,?,?,?,?)').bind(crypto.randomUUID(),q.id,a.owner,a.user.userId,c.action,c.reason,now).run();return Response.json({status:c.action==='reject'?'rejected':'needs-information'});
 }
 if(!c.rubric)throw new Error('Invalid review: complete the evidence and scope checklist.');
 if(c.rubric.practical==='separate-observation'){const observed=await db().prepare("SELECT o.id FROM practical_observations o JOIN attempts p ON p.owner=o.owner AND p.worker_id=o.worker_id AND json_extract(p.payload,'$.moduleId')=o.module_id WHERE o.id=? AND o.owner=? AND p.id=? AND o.outcome='demonstrated'").bind(c.rubric.observationId??'',a.owner,q.attempt_id).first();if(!observed)throw new Error('Invalid practical observation reference.');}
 const evidence=await eligibleAssessment(a.owner,q.attempt_id);
 const course=q.workflow_version>=2?await courseEvidence(a.owner,evidence.attempt.workerId,evidence.attempt.moduleId):null;
 if(course&&course.digest!==q.learning_digest)throw new Error('Record conflict: learning evidence changed. Request updated information before approval.');
 if(evidence.digest!==q.evidence_digest)throw new Error('Record conflict: evidence changed after the request.');
 requestedExpiry(q.expires_at,now);
 const centre=await db().prepare('SELECT name,site FROM training_centres WHERE owner=?').bind(a.owner).first<{name:string;site:string}>();
 if(!centre)throw new Error('No approved centre record.');
 const id=crypto.randomUUID(),attempt=evidence.attempt;
 const token=await sign({iss:trust.issuer,id,attemptId:attempt.id,workerRef:attempt.workerId,workerName:evidence.workerName,moduleId:attempt.moduleId,contentVersion:attempt.contentVersion,score:attempt.result.score,kind:'pilot-simulation',mode:attempt.mode,practical:'not-assessed',iat:now,expiresAt:q.expires_at,governanceVersion:1,workflowVersion:q.workflow_version,learningEvidence:course?.evidence??null,centreRef:a.owner,centreName:centre.name,centreSite:centre.site,requestId:q.id,requestedBy:q.requested_by,approvedBy:a.user.userId,evidenceDigest:q.evidence_digest},signingKey());
 // Recheck authority, queue state and latest evidence in the atomic insertion.
 const inserted=await db().batch([
 db().prepare(`INSERT INTO credentials(owner,id,attempt_id,token,issued_at) SELECT ?,?,?,?,? WHERE EXISTS(SELECT 1 FROM certification_requests WHERE owner=? AND id=? AND status='pending' AND workflow_version=? AND learning_digest IS ?) AND (? IS NULL OR ?=(SELECT id FROM procedure_evidence WHERE owner=? AND worker_id=? AND json_extract(payload,'$.module')=? AND json_extract(payload,'$.catalogVersion')=2 AND json_extract(payload,'$.guided')=0 ORDER BY updated_at DESC,id DESC LIMIT 1)) AND EXISTS(SELECT 1 FROM centre_approvals WHERE owner=? AND status='approved') AND EXISTS(SELECT 1 FROM team_members WHERE owner=? AND user_id=? AND active=1 AND role='certifier') AND ?=(SELECT id FROM attempts WHERE owner=? AND worker_id=? AND json_extract(payload,'$.moduleId')=? AND json_extract(payload,'$.kind')='assessment' ORDER BY json_extract(payload,'$.endedAt') DESC,id DESC LIMIT 1) ON CONFLICT(owner,attempt_id) DO NOTHING`).bind(a.owner,id,attempt.id,token,now,a.owner,q.id,q.workflow_version,q.learning_digest,course?.evidence.independentId??null,course?.evidence.independentId??null,a.owner,attempt.workerId,attempt.moduleId,a.owner,a.owner,a.user.userId,attempt.id,a.owner,attempt.workerId,attempt.moduleId),
 db().prepare("UPDATE certification_requests SET status='approved',reviewed_by=?,reviewed_at=?,review_reason=?,credential_id=?,rubric=? WHERE owner=? AND id=? AND status='pending' AND EXISTS(SELECT 1 FROM credentials WHERE owner=? AND id=?)").bind(a.user.userId,now,c.reason,id,JSON.stringify(c.rubric),a.owner,q.id,a.owner,id),
 audit(a,'credential.issue',id,{requestId:q.id,attemptId:attempt.id,expiresAt:q.expires_at,reason:c.reason,evidenceDigest:q.evidence_digest},true)]);
 if(!inserted[0].meta.changes)throw new Error('Record conflict: request, authority or latest evidence changed. Refresh before reviewing.');
 await db().prepare('INSERT INTO certification_events VALUES(?,?,?,?,?,?,?)').bind(crypto.randomUUID(),q.id,a.owner,a.user.userId,'approved',c.reason,now).run();
 return Response.json(credentialView({id,attempt_id:attempt.id,token,issued_at:now,revoked_at:null,reason:null}));
}catch(e){return failure(e)}}
export async function PATCH(request:Request){try{
 const a=await access('revoke');await freshUser();
 const input=z.object({id:z.string().uuid(),reason:z.string().trim().min(10).max(500)}).strict().safeParse(await json(request));
 if(!input.success)throw new Error('Invalid revocation reason. Use 10–500 characters.');
 const c=input.data,result=await db().batch([db().prepare('UPDATE credentials SET revoked_at=?,reason=? WHERE owner=? AND id=? AND revoked_at IS NULL').bind(Date.now(),c.reason,a.owner,c.id),audit(a,'credential.revoke',c.id,{reason:c.reason},true)]);
 if(!result[0].meta.changes)throw new Error('No active credential found.');return Response.json({revoked:true});
}catch(e){return failure(e)}}
