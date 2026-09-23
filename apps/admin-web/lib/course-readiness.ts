import {db,digest} from './server';
import {curriculum} from './grading';
import {validateProcedure} from './procedure-evidence';
/** Self-reported learning evidence is a prerequisite, never proof of workplace competence. */
export async function courseEvidence(owner:string,worker:string,module:string){
 const snapshots=await db().prepare('SELECT payload FROM learning_snapshots WHERE owner=? AND worker_id=?').bind(owner,worker).all<{payload:string}>();
 const read=snapshots.results.some(r=>{const s=JSON.parse(r.payload);return s.contentVersion===curriculum.version&&s.lessons.includes(module)});
 const rows=await db().prepare("SELECT payload FROM procedure_evidence WHERE owner=? AND worker_id=? AND json_extract(payload,'$.module')=? AND json_extract(payload,'$.catalogVersion')=2 ORDER BY updated_at DESC,id DESC LIMIT 500").bind(owner,worker,module).all<{payload:string}>();
 const records=rows.results.map(r=>validateProcedure(JSON.parse(r.payload),worker));
 const guided=records.find(r=>r.guided&&r.result?.complete),independent=records.find(r=>!r.guided);
 if(!read||!guided||!independent?.result?.complete)throw new Error('Invalid course readiness: sync completed lessons, a guided rehearsal and the latest passed independent scenario check before requesting certification.');
 const evidence={curriculum:curriculum.version,scenarioVersion:2,guidedId:guided.id,independentId:independent.id,independentMode:[...new Set(independent.events.filter(e=>e.type==='action').map(e=>e.presentation))],practical:'not-assessed'};
 return {evidence,digest:await digest(evidence)};
}
