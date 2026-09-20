import {test} from 'node:test';
import assert from 'node:assert/strict';
import {permitted,type Role} from '../lib/access';
test('government-pilot role matrix separates training, administration and certification',()=>{
 for(const [role,rights] of Object.entries({admin:[true,true,true,false,true],trainer:[true,true,false,false,false],viewer:[true,false,false,false,false],certifier:[true,false,false,true,true],unknown:[false,false,false,false,false]}))
  for(const [i,right] of (['read','write','admin','certify','revoke'] as const).entries())assert.equal(permitted(role as Role,right),rights[i],`${role}: ${right}`);
});
