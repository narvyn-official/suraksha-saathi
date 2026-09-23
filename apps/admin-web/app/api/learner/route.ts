import {z} from 'zod';
import {getAppUser} from '@/lib/auth';
import {db,digest,failure,json} from '@/lib/server';
import {learnerAccess} from '@/lib/learner';
import {curriculum,validateImport} from '@/lib/grading';
import {credentialView} from '@/lib/credentials';
import {learningState,readiness} from '@/lib/journey';
import {validateProcedure,assertProcedureSuccessor} from '@/lib/procedure-evidence';
import {courseEvidence} from '@/lib/course-readiness';
import {eligibleAssessment} from '@/lib/certification';
const command=z.discriminatedUnion('action',[
 z.object({action:z.literal('join'),invitation:z.string().min(40).max(200),workerId:z.string().uuid().optional()}).strict(),
 z.object({action:z.literal('sync'),workerId:z.string().uuid(),deviceId:z.string().uuid(),revision:z.number().int().positive().safe(),learning:learningState,bundle:z.unknown().optional(),procedures:z.array(z.unknown()).max(50)}).strict(),
 z.object({action:z.literal('request'),workerId:z.string().uuid(),attemptId:z.string().uuid(),note:z.string().trim().min(10).max(500)}).strict(),
 z.object({action:z.literal('clarify'),workerId:z.string().uuid(),requestId:z.string().uuid(),note:z.string().trim().min(10).max(500)}).strict()
]);
export async function GET(request:Request){try{
 const user=await getAppUser();if(!user)throw new Error('Sign in to continue.');
 const workerId=new URL(request.url).searchParams.get('workerId');
 const links=await db().prepare("SELECT l.worker_id,c.name AS centre,w.name,w.sector FROM learner_links l JOIN workers w ON w.owner=l.owner AND w.id=l.worker_id JOIN training_centres c ON c.owner=l.owner JOIN centre_approvals a ON a.owner=l.owner AND a.status='approved' WHERE l.user_id=?").bind(user.userId).all();
 if(!workerId)return Response.json({user,links:links.results},{headers:{'Cache-Control':'no-store'}});
 const a=await learnerAccess(workerId),bind=[a.owner,workerId];
 const [attempts,procedures,snapshots,requests,credentials,observations,events,assignments]=await Promise.all([
 db().prepare('SELECT payload FROM attempts WHERE owner=? AND worker_id=? ORDER BY json_extract(payload,\'$.endedAt\') DESC,id DESC LIMIT 1000').bind(...bind).all<{payload:string}>(),
 db().prepare('SELECT payload FROM procedure_evidence WHERE owner=? AND worker_id=? ORDER BY updated_at DESC LIMIT 200').bind(...bind).all<{payload:string}>(),
 db().prepare('SELECT payload FROM learning_snapshots WHERE owner=? AND worker_id=?').bind(...bind).all<{payload:string}>(),
 db().prepare("SELECT q.*,json_extract(p.payload,'$.moduleId') AS module_id FROM certification_requests q JOIN attempts p ON p.owner=q.owner AND p.id=q.attempt_id WHERE q.owner=? AND p.worker_id=? ORDER BY q.requested_at DESC LIMIT 200").bind(...bind).all(),
 db().prepare('SELECT c.* FROM credentials c JOIN attempts p ON p.owner=c.owner AND p.id=c.attempt_id WHERE c.owner=? AND p.worker_id=? ORDER BY c.issued_at DESC LIMIT 200').bind(...bind).all<{id:string;token:string;revoked_at:number|null}>(),
 db().prepare('SELECT * FROM practical_observations WHERE owner=? AND worker_id=? ORDER BY observed_at DESC LIMIT 100').bind(...bind).all(),
 db().prepare('SELECT e.* FROM certification_events e JOIN certification_requests q ON q.id=e.request_id AND q.owner=e.owner JOIN attempts p ON p.id=q.attempt_id AND p.owner=q.owner WHERE e.owner=? AND p.worker_id=? ORDER BY e.at DESC LIMIT 500').bind(...bind).all(),
 db().prepare('SELECT * FROM training_assignments WHERE owner=? AND worker_id=? AND cancelled_at IS NULL ORDER BY due_at LIMIT 200').bind(...bind).all()]);
 const history=attempts.results.map(r=>JSON.parse(r.payload)),practice=procedures.results.map(r=>JSON.parse(r.payload)),read=new Set<string>(snapshots.results.flatMap(r=>{const s=JSON.parse(r.payload);return s.contentVersion===curriculum.version?s.lessons:[]}));
 const courses=curriculum.modules.map(m=>{const latest=history.find(p=>p.moduleId===m.id&&p.kind==='assessment');const drills=practice.filter(p=>p.module===m.id&&p.catalogVersion===2);const guided=drills.some(p=>p.guided&&p.result?.complete),independent=drills.find(p=>!p.guided);return {id:m.id,title:m.title[0],attemptId:latest?.id??null,steps:readiness(read.has(m.id),guided,independent?.result?.complete===true,latest?.contentVersion===curriculum.version&&latest?.result?.passed===true)}});
 return Response.json({user,links:links.results,learner:a.name,centre:a.centre,courses,requests:requests.results,credentials:credentials.results.map(r=>credentialView(r)),observations:observations.results,events:events.results,assignments:assignments.results,procedures:practice,syncedAt:Date.now()},{headers:{'Cache-Control':'no-store'}});
}catch(e){return failure(e)}}
export async function POST(request:Request){try{
 const c=command.parse(await json(request)),user=await getAppUser();if(!user)throw new Error('Sign in to continue.');const now=Date.now();
 if(c.action==='join'){
  const hash=await digest(c.invitation),row=await db().prepare("SELECT l.* FROM learner_links l JOIN centre_approvals a ON a.owner=l.owner AND a.status='approved' WHERE invite_hash=? AND invite_expires_at>? AND user_id IS NULL AND email=?").bind(hash,now,user.email.toLowerCase()).first<{owner:string;worker_id:string}>();
  if(!row)throw new Error('Invalid or expired learner invitation. Use the invited email or ask for a new invitation.');
  if(c.workerId&&c.workerId!==row.worker_id)throw new Error('Invalid learner profile: this invitation is for another Android learner ID. Ask your trainer to correct the roster; records have not been merged.');
  const r=await db().prepare('UPDATE learner_links SET user_id=?,linked_at=?,invite_hash=NULL,invite_expires_at=NULL WHERE owner=? AND worker_id=? AND user_id IS NULL AND invite_hash=? AND invite_expires_at>?').bind(user.userId,now,row.owner,row.worker_id,hash,now).run();
  if(!r.meta.changes)throw new Error('Invalid or already used invitation.');return Response.json({linked:true,workerId:row.worker_id});
 }
 const a=await learnerAccess(c.workerId);
 if(c.action==='sync'){
  const statements:D1PreparedStatement[]=[];
  if(c.bundle){const data=validateImport(c.bundle);if(data.worker.id!==c.workerId)throw new Error('Forbidden: wrong learner in upload.');
   for(const attempt of data.attempts)statements.push(db().prepare('INSERT INTO attempts(owner,id,worker_id,worker_name,payload,digest,imported_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(owner,id) DO NOTHING').bind(a.owner,attempt.id,c.workerId,a.name,JSON.stringify(attempt),await digest(attempt),now));}
  if(new Set(c.procedures.map(r=>validateProcedure(r,c.workerId).id)).size!==c.procedures.length)throw new Error('Invalid duplicate procedure.');
  for(const raw of c.procedures){const record=validateProcedure(raw,c.workerId),old=await db().prepare('SELECT payload FROM procedure_evidence WHERE owner=? AND id=?').bind(a.owner,record.id).first<{payload:string}>();
   if(old)assertProcedureSuccessor(validateProcedure(JSON.parse(old.payload),c.workerId),record);
   statements.push(db().prepare('INSERT INTO procedure_evidence(owner,id,worker_id,payload,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(owner,id) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at').bind(a.owner,record.id,c.workerId,JSON.stringify(record),record.updatedAt));
  }
  const prior=await db().prepare('SELECT revision,payload FROM learning_snapshots WHERE owner=? AND worker_id=? AND device_id=?').bind(a.owner,c.workerId,c.deviceId).first<{revision:number;payload:string}>(),payload=JSON.stringify(c.learning);
  if(prior&&c.revision<prior.revision)throw new Error('Record conflict: an older learning snapshot cannot replace newer progress.');
  if(prior&&c.revision===prior.revision&&prior.payload!==payload)throw new Error('Record conflict: revision reused with different progress.');
  statements.push(db().prepare('INSERT INTO learning_snapshots VALUES(?,?,?,?,?,?) ON CONFLICT(owner,worker_id,device_id) DO UPDATE SET revision=excluded.revision,payload=excluded.payload,updated_at=excluded.updated_at').bind(a.owner,c.workerId,c.deviceId,c.revision,payload,now));
  await db().batch(statements);return Response.json({synced:true,revision:c.revision});
 }
 if(c.action==='clarify'){
  const q=await db().prepare("SELECT q.id,q.attempt_id FROM certification_requests q JOIN attempts p ON p.owner=q.owner AND p.id=q.attempt_id WHERE q.id=? AND q.owner=? AND p.worker_id=? AND q.status='needs-information'").bind(c.requestId,a.owner,c.workerId).first<{id:string;attempt_id:string}>();if(!q)throw new Error('No request awaiting information.');
  const evidence=await eligibleAssessment(a.owner,q.attempt_id),course=await courseEvidence(a.owner,c.workerId,evidence.attempt.moduleId);
  await db().batch([db().prepare("UPDATE certification_requests SET status='pending',learning_digest=?,workflow_version=2,request_note=?,reviewed_by=NULL,reviewed_at=NULL WHERE id=? AND owner=? AND status='needs-information'").bind(course.digest,c.note,c.requestId,a.owner),db().prepare("INSERT INTO certification_events VALUES(?,?,?,?,?,?,?)").bind(crypto.randomUUID(),c.requestId,a.owner,user.userId,'clarification',c.note,now)]);return Response.json({submitted:true});
 }
 const evidence=await eligibleAssessment(a.owner,c.attemptId);if(evidence.attempt.workerId!==c.workerId)throw new Error('Forbidden assessment.');
 const existing=await db().prepare('SELECT id FROM certification_requests WHERE owner=? AND attempt_id=?').bind(a.owner,c.attemptId).first();if(existing)return Response.json({request:existing,alreadySubmitted:true});
 const course=await courseEvidence(a.owner,c.workerId,evidence.attempt.moduleId);
 const id=crypto.randomUUID();await db().batch([db().prepare("INSERT INTO certification_requests(id,owner,attempt_id,evidence_digest,expires_at,requested_by,requested_at,request_note,status,workflow_version,learning_digest) VALUES(?,?,?,?,?,?,?,?,'pending',2,?)").bind(id,a.owner,c.attemptId,evidence.digest,now+180*86400000,user.userId,now,c.note,course.digest),db().prepare('INSERT INTO certification_events VALUES(?,?,?,?,?,?,?)').bind(crypto.randomUUID(),id,a.owner,user.userId,'submitted',c.note,now)]);
 return Response.json({request:{id},submitted:true},{status:202});
}catch(e){return failure(e)}}
