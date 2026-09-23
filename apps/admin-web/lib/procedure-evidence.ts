import {z} from 'zod';
import catalog from './scenario-catalog.json';
const event=z.discriminatedUnion('type',[
 z.object({type:z.literal('action'),sequence:z.number().int(),step:z.string(),action:z.string(),presentation:z.enum(['camera','screen','description']),correct:z.boolean(),time:z.number().int().safe(),spatial:z.unknown().optional()}).strict(),
 z.object({type:z.literal('advance'),sequence:z.number().int(),step:z.string(),time:z.number().int().safe()}).strict()
]);
const schema=z.object({schemaVersion:z.literal(1),catalogVersion:z.union([z.literal(1),z.literal(2)]),id:z.string().uuid(),workerId:z.string().uuid(),module:z.enum(['fire','gas','machinery','ppe','emergency']),guided:z.boolean(),createdAt:z.number().int().nonnegative().safe(),updatedAt:z.number().int().nonnegative().safe(),index:z.number().int(),finished:z.boolean(),feedback:z.boolean(),lastCorrect:z.boolean(),flags:z.record(z.string(),z.boolean()),events:z.array(event).max(512),criticalFailures:z.array(z.string()).max(100),completedAt:z.number().int().optional(),result:z.object({complete:z.boolean(),stopped:z.boolean(),criticalFailures:z.array(z.string()),practical:z.literal('not-assessed'),certifiable:z.literal(false)}).strict().optional()}).strict();
/** Replays decisions; spatial samples remain self-reported and never prove physical competence. */
export function validateProcedure(raw:unknown,worker:string){
 const r=schema.parse(raw);if(r.workerId!==worker)throw new Error('Invalid procedure learner.');
 const steps=(catalog[String(r.catalogVersion) as keyof typeof catalog] as Record<string,string[]>)[r.module];if(!steps)throw new Error('Unsupported procedure module.');
 let index=0,feedback=false,correct=false,finished=false,stopped=false,last=r.createdAt;const flags:Record<string,boolean>={},failures:string[]=[];
 r.events.forEach((e,i)=>{
  if(finished||e.sequence!==i+1||e.time<last||e.step!==steps[index])throw new Error('Invalid procedure event order.');last=e.time;
  if(e.type==='action'){
   if(feedback||![e.step,e.step+'-unsafe'].includes(e.action))throw new Error('Invalid procedure action.');correct=e.action===e.step;
   if(e.correct!==correct)throw new Error('Invalid procedure outcome.');feedback=true;
   if(correct)flags[e.step]=true;else {if(!failures.includes(e.step))failures.push(e.step);if(!r.guided){finished=true;stopped=true}}
  }else{if(!feedback)throw new Error('Invalid procedure advance.');feedback=false;if(correct){if(index===steps.length-1)finished=true;else index++}}
 });
 if(index!==r.index||feedback!==r.feedback||correct!==r.lastCorrect||finished!==r.finished||last!==r.updatedAt||JSON.stringify(Object.entries(flags).sort())!==JSON.stringify(Object.entries(r.flags).sort())||JSON.stringify(failures)!==JSON.stringify(r.criticalFailures))throw new Error('Invalid procedure derived state.');
 if(finished){if(!r.result||r.completedAt!==last||r.result.stopped!==stopped||r.result.complete===stopped||JSON.stringify(r.result.criticalFailures)!==JSON.stringify(failures))throw new Error('Invalid procedure result.')}else if(r.result||r.completedAt!==undefined)throw new Error('Invalid premature result.');
 return r;
}
export function assertProcedureSuccessor(old:ReturnType<typeof validateProcedure>,next:ReturnType<typeof validateProcedure>){
 if(old.id!==next.id||old.workerId!==next.workerId||old.module!==next.module||old.guided!==next.guided||old.createdAt!==next.createdAt||old.catalogVersion!==next.catalogVersion||next.events.length<old.events.length||old.events.some((e,i)=>JSON.stringify(e)!==JSON.stringify(next.events[i])))throw new Error('Record conflict: procedure history cannot be rewritten.');
}
