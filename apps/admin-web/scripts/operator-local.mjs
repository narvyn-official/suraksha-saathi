// Provision an existing, explicitly selected LOCAL pilot operator. Never approves a centre.
import {readFileSync,writeFileSync,readdirSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
const [action,id,...extra]=process.argv.slice(2);
if(!['add','remove'].includes(action)||!id||!/^[-a-zA-Z0-9_]{1,128}$/.test(id)||extra.length)throw new Error('Usage (admin-web directory): node scripts/operator-local.mjs add|remove EXACT_AUTH_USER_ID');
const path='.dev.vars',before=readFileSync(path,'utf8');
if(action==='add'){
 const dir='.wrangler/state/v3/d1/miniflare-D1DatabaseObject';
 const files=readdirSync(dir).filter(f=>f.endsWith('.sqlite')&&f!=='metadata.sqlite');
 if(files.length!==1)throw new Error('Expected one local database; check the local migration configuration.');
 const db=new DatabaseSync(`${dir}/${files[0]}`,{readOnly:true});
 try{if(!db.prepare('SELECT id FROM auth_user WHERE id=?').get(id))throw new Error('User ID does not exist in the local database. Create the operator account first.')}finally{db.close()}
}
const current=(before.match(/^PLATFORM_OPERATOR_IDS=(.*)$/m)?.[1]??'').trim().replace(/^["']|["']$/g,'').split(',').map(v=>v.trim()).filter(Boolean);
const ids=action==='add'?[...new Set([...current,id])]:current.filter(v=>v!==id);
writeFileSync(path,before.replace(/^PLATFORM_OPERATOR_IDS=.*\n?/m,'').trimEnd()+'\nPLATFORM_OPERATOR_IDS='+ids.join(',')+'\n',{mode:0o600});
console.log(`Local operator ${action==='add'?'added':'removed'}. Restart the service. Centre and certifier approval remain separate.`);
