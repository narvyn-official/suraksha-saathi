// Local-only development stack. No external email or cloud deployment.
import {spawn,spawnSync} from 'node:child_process';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),web=path.join(root,'apps/admin-web');
for(const script of ['create-pilot-issuer.mjs','create-app-auth.mjs']){const file=path.join(web,'.dev.vars');const prior=existsSync(file)?readFileSync(file,'utf8'):'';if(new RegExp('^'+(script.startsWith('create-pilot')?'ISSUER_PRIVATE_JWK':'BETTER_AUTH_SECRET')+'=','m').test(prior))continue;const r=spawnSync(process.execPath,[path.join(root,'scripts',script)],{cwd:root,stdio:'inherit'});if(r.status)process.exit(r.status)}
const vars=path.join(web,'.dev.vars');let content=readFileSync(vars,'utf8');
const configured=content.match(/^BETTER_AUTH_URL=(.*)$/m)?.[1]?.replace(/^['"]|['"]$/g,'').trim();
if(configured&&!['http://localhost:5173','http://127.0.0.1:5173'].includes(configured))throw new Error('Local launcher refuses to change a non-local account service. Use a separate local configuration.');
function set(name,value){const line=new RegExp('^'+name+'=.*$','m');content=line.test(content)?content.replace(line,name+'='+value):content+'\n'+name+'='+value+'\n'}
set('RECOVERY_MAIL_URL','http://127.0.0.1:5188/send');
if(!/^RECOVERY_MAIL_KEY=.{32,}$/m.test(content))set('RECOVERY_MAIL_KEY',randomBytes(32).toString('hex'));
writeFileSync(vars,content,{mode:0o600});
const migrate=spawnSync('npx',['wrangler','d1','migrations','apply','DB','--local','--config','wrangler.local.json','--persist-to','.wrangler/state'],{cwd:web,stdio:'inherit'});if(migrate.status)process.exit(migrate.status);
const children=[spawn(process.execPath,['scripts/dev-inbox.mjs'],{cwd:web,stdio:'inherit'}),spawn('npm',['run','dev'],{cwd:web,stdio:'inherit'})];
console.log('SurakshaAr: http://localhost:5173/learn · Staff: http://localhost:5173 · LOCAL-ONLY inbox: http://127.0.0.1:5188');
console.log('Phone: adb reverse tcp:5173 tcp:5173 (debug build connects to localhost). No external email is sent.');
let stopping=false;function stop(){if(stopping)return;stopping=true;for(const c of children)c.kill('SIGTERM')}
for(const c of children)c.on('exit',()=>stop());process.on('SIGINT',stop);process.on('SIGTERM',stop);
