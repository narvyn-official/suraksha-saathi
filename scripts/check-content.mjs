import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const load=p=>JSON.parse(readFileSync(new URL(p,import.meta.url)));
assert.deepEqual(load('../content/curriculum.json'),load('../apps/admin-web/lib/curriculum.json'));
assert.deepEqual(load('../content/trusted-issuer.json'),load('../apps/admin-web/lib/trusted-issuer.json'));
console.log('Android and dashboard curriculum and issuer trust match.');
