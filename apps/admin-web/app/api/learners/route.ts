import {z} from 'zod';
import {access,audit,db,digest,failure,json} from '@/lib/server';
const command=z.discriminatedUnion('action',[
 z.object({action:z.literal('invite'),workerId:z.string().uuid(),email:z.string().email().max(254)}).strict(),
 z.object({action:z.literal('unlink'),workerId:z.string().uuid()}).strict(),
 z.object({action:z.literal('observe'),workerId:z.string().uuid(),moduleId:z.enum(['fire','gas','machinery','ppe','emergency']),observedAt:z.number().int().positive(),outcome:z.enum(['demonstrated','needs-practice']),note:z.string().trim().min(20).max(1000),rubric:z.object({identityChecked:z.literal(true),procedureReviewed:z.literal(true),safeDecisions:z.boolean(),sequence:z.boolean(),communication:z.boolean()}).strict()}).strict()
]);
export async function GET(){try{
 const a=await access();const [links,observations,procedures]=await Promise.all([
 db().prepare('SELECT l.worker_id,l.email,l.linked_at,l.invite_expires_at,w.name FROM learner_links l JOIN workers w ON w.owner=l.owner AND w.id=l.worker_id WHERE l.owner=? LIMIT 1000').bind(a.owner).all(),
 db().prepare('SELECT * FROM practical_observations WHERE owner=? ORDER BY created_at DESC LIMIT 200').bind(a.owner).all(),
 db().prepare('SELECT p.*,w.name FROM procedure_evidence p JOIN workers w ON w.owner=p.owner AND w.id=p.worker_id WHERE p.owner=? ORDER BY p.updated_at DESC LIMIT 200').bind(a.owner).all<{payload:string}>()]);
 return Response.json({links:links.results,observations:observations.results,procedures:procedures.results.map(r=>({...r,payload:JSON.parse(r.payload)}))},{headers:{'Cache-Control':'no-store'}});
}catch(e){return failure(e)}}
export async function POST(request:Request){try{
 const c=command.parse(await json(request)),a=await access('write'),now=Date.now();
 if(!await db().prepare('SELECT id FROM workers WHERE owner=? AND id=?').bind(a.owner,c.workerId).first())throw new Error('No learner found. Register the Android learner ID in Workers first.');
 if(c.action==='invite'){
  const prior=await db().prepare('SELECT user_id FROM learner_links WHERE owner=? AND worker_id=?').bind(a.owner,c.workerId).first();if(prior?.user_id)throw new Error('Already linked. Unlink explicitly before inviting another account.');
  const invitation=crypto.randomUUID()+crypto.randomUUID(),hash=await digest(invitation),expires=now+7*86400000;
  const saved=await db().batch([db().prepare('INSERT INTO learner_links(owner,worker_id,email,invite_hash,invite_expires_at) VALUES(?,?,?,?,?) ON CONFLICT(owner,worker_id) DO UPDATE SET email=excluded.email,invite_hash=excluded.invite_hash,invite_expires_at=excluded.invite_expires_at WHERE learner_links.user_id IS NULL').bind(a.owner,c.workerId,c.email.toLowerCase(),hash,expires),audit(a,'learner.invite',c.workerId)]);
  if(!saved[0].meta.changes)throw new Error('Record conflict: learner was linked while creating the invitation. Refresh.');
  return Response.json({invitation,url:new URL('/learn',request.url).origin+'/learn#invite='+encodeURIComponent(invitation),expiresAt:expires},{headers:{'Cache-Control':'no-store'}});
 }
 if(c.action==='unlink'){await db().batch([db().prepare('DELETE FROM learner_links WHERE owner=? AND worker_id=?').bind(a.owner,c.workerId),audit(a,'learner.unlink',c.workerId)]);return Response.json({unlinked:true})}
 if(c.observedAt>now||c.observedAt<now-366*86400000)throw new Error('Invalid observation date.');
 if(c.outcome==='demonstrated'&&(!c.rubric.safeDecisions||!c.rubric.sequence||!c.rubric.communication))throw new Error('Invalid outcome: all observation criteria must be met.');
 const id=crypto.randomUUID();await db().batch([db().prepare('INSERT INTO practical_observations VALUES(?,?,?,?,?,?,?,?,?,?)').bind(id,a.owner,c.workerId,c.moduleId,a.user.userId,c.observedAt,JSON.stringify(c.rubric),c.outcome,c.note,now),audit(a,'learner.observation',id,{workerId:c.workerId,outcome:c.outcome})]);
 return Response.json({id,saved:true});
}catch(e){return failure(e)}}
