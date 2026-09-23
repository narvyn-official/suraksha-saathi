import {z} from 'zod';
export const modules=['fire','gas','machinery','ppe','emergency'] as const;
export const learningState=z.object({contentVersion:z.string().max(20),lessons:z.array(z.enum(modules)).max(5)}).strict();
export const reviewRubric=z.object({evidenceReviewed:z.literal(true),scopeConfirmed:z.literal(true),latestAssessment:z.literal(true),identityBasis:z.enum(['centre-roster','in-person','not-verified']),practical:z.enum(['not-assessed','separate-observation']),observationId:z.string().uuid().optional()}).strict();
export function readiness(lesson:boolean,guided:boolean,independent:boolean,passed:boolean){
 return [{id:'learn',label:'Read or listen to lessons',done:lesson},{id:'guided',label:'Guided scenario rehearsal',done:guided},{id:'independent',label:'Independent scenario check',done:independent},{id:'assessment',label:'Current knowledge assessment passed',done:passed}];
}
