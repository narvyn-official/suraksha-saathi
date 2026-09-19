import {test} from 'node:test';
import assert from 'node:assert/strict';
import {permitted,type Role} from '../lib/access';
test('every role is explicitly bounded and unknown roles fail closed',()=>{
 for(const [role,rights] of Object.entries({admin:[true,true,true],trainer:[true,true,false],viewer:[true,false,false],unknown:[false,false,false]}))
  for(const [i,right] of (['read','write','admin'] as const).entries())assert.equal(permitted(role as Role,right),rights[i],`${role}: ${right}`);
});
