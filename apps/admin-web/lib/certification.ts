import {db} from './server';
import {curriculum,validateImport} from './grading';
/** Always recompute evidence. The imported result field is never the authority. */
export async function eligibleAssessment(owner:string,id:string){
 const row=await db().prepare("SELECT a.payload,a.digest,a.worker_id,w.name,w.sector FROM attempts a JOIN workers w ON w.owner=a.owner AND w.id=a.worker_id WHERE a.owner=? AND a.id=?").bind(owner,id).first<{payload:string;digest:string;worker_id:string;name:string;sector:string}>();
 if(!row)throw new Error('No registered assessment found.');
 const raw=JSON.parse(row.payload);
 const a=validateImport({schemaVersion:1,worker:{id:row.worker_id,name:row.name,sector:row.sector},attempts:[raw]}).attempts[0];
 if(a.kind!=='assessment'||!a.result.passed||a.contentVersion!==curriculum.version)throw new Error('Only passed assessments for the current curriculum can be submitted.');
 const latest=await db().prepare("SELECT id FROM attempts WHERE owner=? AND worker_id=? AND json_extract(payload,'$.moduleId')=? AND json_extract(payload,'$.kind')='assessment' ORDER BY json_extract(payload,'$.endedAt') DESC,id DESC LIMIT 1").bind(owner,row.worker_id,a.moduleId).first<{id:string}>();
 if(latest?.id!==id)throw new Error('Invalid evidence: only the latest assessment can be submitted; complete a new assessment after a failure.');
 return {attempt:a,digest:row.digest,workerName:row.name,workerSector:row.sector};
}
