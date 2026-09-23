import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {returnPath} from '../lib/navigation';
import {validateProcedure,assertProcedureSuccessor} from '../lib/procedure-evidence';
import {reviewRubric} from '../lib/journey';
import catalog from '../lib/scenario-catalog.json';
export function procedure(workerId:string,module:keyof typeof catalog['2']='fire',guided=false){
 let at=Date.now()-10000;const createdAt=at,events:Record<string,unknown>[]=[],flags:Record<string,boolean>={};
 for(const step of catalog['2'][module]){events.push({type:'action',sequence:events.length+1,step,action:step,presentation:'screen',correct:true,time:++at});flags[step]=true;events.push({type:'advance',sequence:events.length+1,step,time:++at})}
 return {schemaVersion:1,catalogVersion:2,id:randomUUID(),workerId,module,guided,createdAt,updatedAt:at,index:catalog['2'][module].length-1,finished:true,feedback:false,lastCorrect:true,flags,events,criticalFailures:[],completedAt:at,result:{complete:true,stopped:false,criticalFailures:[],practical:'not-assessed',certifiable:false}};
}
test('return destinations preserve local intent and reject redirect escapes',()=>{assert.equal(returnPath('/learn#invite=test'),'/learn#invite=test');for(const p of ['https://attacker.test','//attacker.test','/\\attacker.test','/login?next=x','/\nattack'])assert.equal(returnPath(p),'/')});
test('all five scenario histories replay and remain non-certifying',()=>{const id=randomUUID();for(const m of Object.keys(catalog['2']) as (keyof typeof catalog['2'])[]){const r=validateProcedure(procedure(id,m),id);assert.equal(r.result?.certifiable,false);assert(r.events.length>=16)}});
test('reject forged outcomes, event skips, wrong learner and rewritten history',()=>{const id=randomUUID(),r=procedure(id),valid=validateProcedure(r,id);assert.throws(()=>validateProcedure(r,randomUUID()));for(const edit of [(x:typeof r)=>{x.events[0].correct=false},(x:typeof r)=>{x.events.splice(2,1)},(x:typeof r)=>{x.index=0}]){const x=structuredClone(r);edit(x);assert.throws(()=>validateProcedure(x,id))}assert.throws(()=>assertProcedureSuccessor(valid,{...valid,guided:true}));assert.doesNotThrow(()=>assertProcedureSuccessor(valid,valid))});
test('approval rubric cannot accept an unchecked review',()=>{assert.equal(reviewRubric.safeParse({evidenceReviewed:false,scopeConfirmed:true,latestAssessment:true,identityBasis:'not-verified',practical:'not-assessed'}).success,false)});
