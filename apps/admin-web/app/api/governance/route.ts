import {getAppUser,freshUser,isOperator} from '@/lib/auth';
import {db,json,failure,audit} from '@/lib/server';
import {z} from 'zod';
const command=z.discriminatedUnion('action',[
 z.object({action:z.literal('request'),name:z.string().trim().min(2).max(100),site:z.string().trim().min(2).max(150)}).strict(),
 z.object({action:z.enum(['approve','reject','suspend']),owner:z.string().min(1).max(300),reason:z.string().trim().min(10).max(500)}).strict()
]);
export async function GET(){try{
 const user=await getAppUser();if(!user)throw new Error('Sign in to continue.');
 const operator=isOperator(user.userId);
 const rows=await db().prepare(`SELECT c.owner,c.name,c.site,a.status,a.requested_at,a.reviewed_at,a.reason FROM centre_approvals a JOIN training_centres c ON c.owner=a.owner ${operator?'':'WHERE c.owner=?'} ORDER BY a.requested_at DESC LIMIT 200`).bind(...(operator?[]:[user.userId])).all();
 return Response.json({operator,centres:rows.results},{headers:{'Cache-Control':'no-store'}});
}catch(e){return failure(e)}}
export async function POST(request:Request){try{
 const user=await freshUser(),input=command.safeParse(await json(request));if(!input.success)throw new Error('Invalid centre application or review.');
 const c=input.data,now=Date.now();
 if(c.action==='request'){
  const prior=await db().prepare('SELECT status FROM centre_approvals WHERE owner=?').bind(user.userId).first<{status:string}>();
  if(prior&&prior.status!=='rejected')throw new Error('Already submitted: an operator must review the existing application.');
  const submitted=await db().batch([
   db().prepare('INSERT INTO centre_approvals(owner,status,requested_at) VALUES(?,?,?) ON CONFLICT(owner) DO UPDATE SET status=excluded.status,requested_at=excluded.requested_at,reviewed_at=NULL,reviewed_by=NULL,reason=\'\' WHERE centre_approvals.status=\'rejected\'').bind(user.userId,'pending',now),
   db().prepare('INSERT INTO training_centres(owner,name,site,updated_at) SELECT ?,?,?,? WHERE changes()=1 ON CONFLICT(owner) DO UPDATE SET name=excluded.name,site=excluded.site,updated_at=excluded.updated_at').bind(user.userId,c.name,c.site,now),
   audit({owner:user.userId,user,role:'admin'},'centre.request',user.userId,{name:c.name,site:c.site},true)]);
  if(!submitted[0].meta.changes)throw new Error('Already submitted: refresh the application status.');
 }else{
  if(!isOperator(user.userId))throw new Error('Forbidden: only configured platform operators can review centres.');
  if(c.owner===user.userId)throw new Error('Forbidden: you cannot approve or review your own centre.');
  const state=c.action==='approve'?'approved':c.action==='reject'?'rejected':'suspended';
  const result=await db().batch([db().prepare(`UPDATE centre_approvals SET status=?,reviewed_at=?,reviewed_by=?,reason=? WHERE owner=? AND ${c.action==='suspend'?"status='approved'":c.action==='reject'?"status='pending'":"status IN ('pending','suspended')"}`).bind(state,now,user.userId,c.reason,c.owner),audit({owner:c.owner,user,role:'admin'},'centre.'+c.action,c.owner,{reason:c.reason},true)]);
  if(!result[0].meta.changes)throw new Error('Invalid centre transition. Refresh the queue.');
 }
 return Response.json({saved:true},{headers:{'Cache-Control':'no-store'}});
}catch(e){return failure(e)}}
