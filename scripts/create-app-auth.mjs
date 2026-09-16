import { readFile, writeFile, chmod } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
const file=new URL('../apps/admin-web/.dev.vars',import.meta.url);
const url=new URL(process.argv[2]??'http://localhost:5173');
if(url.username||url.password||url.search||url.hash||url.pathname!=='/'||!(url.protocol==='https:'||url.protocol==='http:'&&['localhost','127.0.0.1'].includes(url.hostname)))throw new Error('Use an HTTPS origin, or localhost for development.');
let values='';try{values=await readFile(file,'utf8')}catch(e){if(e.code!=='ENOENT')throw e}
if(!/^BETTER_AUTH_SECRET=/m.test(values))values+='\nBETTER_AUTH_SECRET='+randomBytes(48).toString('hex')+'\n';
if(!/^BETTER_AUTH_URL=/m.test(values))values+='BETTER_AUTH_URL='+url.origin+'\n';
await writeFile(file,values,{mode:0o600});await chmod(file,0o600);
console.log('Local account configuration is ready. Existing values were preserved; no secret was printed.');
