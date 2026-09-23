import {getAppUser} from './auth';
import {db} from './server';
export async function learnerAccess(workerId:string){
 const user=await getAppUser();if(!user)throw new Error('Sign in to continue.');
 const link=await db().prepare("SELECT l.owner,l.worker_id,w.name,w.sector,c.name AS centre FROM learner_links l JOIN centre_approvals a ON a.owner=l.owner AND a.status='approved' JOIN workers w ON w.owner=l.owner AND w.id=l.worker_id JOIN training_centres c ON c.owner=l.owner WHERE l.user_id=? AND l.worker_id=?").bind(user.userId,workerId).first<{owner:string;worker_id:string;name:string;sector:string;centre:string}>();
 if(!link)throw new Error('Forbidden: this learner is not linked to your account in an approved centre.');
 return {...link,user};
}
