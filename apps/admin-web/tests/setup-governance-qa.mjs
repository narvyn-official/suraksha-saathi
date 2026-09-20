// Local-only synthetic accounts. Run before restarting the local server, then governance.integration.ts.
import {account} from './auth-client.mjs';
import{readFileSync,writeFileSync,mkdirSync,existsSync}from'node:fs';
if(existsSync('.sites-runtime/governance-qa.json'))throw new Error('QA accounts already exist; reuse them with governance.integration.ts or clean up first.');
const users={};for(const role of ['operator','owner','certifier','trainer'])users[role]=await account();
const path='.dev.vars',before=readFileSync(path,'utf8'),previous=before.match(/^PLATFORM_OPERATOR_IDS=(.*)$/m)?.[1]??'';
const ids=[...previous.split(',').map(x=>x.trim()).filter(Boolean),users.operator.userId];
writeFileSync(path,before.replace(/^PLATFORM_OPERATOR_IDS=.*\n?/m,'').trimEnd()+'\nPLATFORM_OPERATOR_IDS='+ids.join(',')+'\n');
mkdirSync('.sites-runtime',{recursive:true});writeFileSync('.sites-runtime/governance-qa.json',JSON.stringify({users,operatorAdded:users.operator.userId}),{mode:0o600});
console.log('Four synthetic accounts ready. Restart the local server to load the QA operator ID. Credentials stay in ignored local storage.');
