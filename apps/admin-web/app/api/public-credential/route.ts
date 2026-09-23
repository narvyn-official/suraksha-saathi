import {db,failure} from '@/lib/server';
import {verify,credentialView} from '@/lib/credentials';
export async function GET(request:Request){try{
 const id=new URL(request.url).searchParams.get('id');if(!id||!/^[0-9a-f-]{36}$/i.test(id))throw new Error('Invalid credential reference.');
 const row=await db().prepare('SELECT id,token,issued_at,revoked_at FROM credentials WHERE id=?').bind(id).first<{id:string;token:string;issued_at:number;revoked_at:number|null}>();if(!row)throw new Error('No credential found.');
 await verify(row.token);const c=credentialView(row);
 return Response.json({id:c.id,module:c.moduleId,issuer:c.centreName,issuedAt:c.issued_at,expiresAt:c.expiresAt,status:c.status,scope:'Pilot simulation; practical competence not certified'},{headers:{'Cache-Control':'no-store','X-Robots-Tag':'noindex'}});
}catch(e){return failure(e)}}
