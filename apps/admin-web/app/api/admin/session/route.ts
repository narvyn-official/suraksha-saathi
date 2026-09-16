import { getChatGPTUser } from "@/app/chatgpt-auth";
import { access, db, failure, json, audit } from "@/lib/server";
export async function GET() {
  try {
    const user = await getChatGPTUser();if (!user) throw new Error("Sign in to continue.");
    const choices = await db().prepare("SELECT m.owner,m.role,m.user_id,c.name FROM team_members m LEFT JOIN training_centres c ON c.owner=m.owner WHERE m.active=1 AND (m.user_id=? OR (m.user_id IS NULL AND m.email=?))").bind(user.userId,user.email.toLowerCase()).all();
    let current;try { current = await access(); } catch { current = null; }
    const centre = current ? await db().prepare("SELECT name,site FROM training_centres WHERE owner=?").bind(current.owner).first() : null;
    return Response.json({user, current: current ? {owner:current.owner, role:current.role, name:centre?.name ?? "My training centre", site:centre?.site ?? ""} : null, workspaces:[{owner:user.userId,role:"admin",name:"My training centre",personal:true},...choices.results.filter(r=>r.owner!==user.userId)]}, {headers:{"Cache-Control":"no-store"}});
  } catch(e) {return failure(e);}
}
export async function POST(request: Request) {
  try {
    const user = await getChatGPTUser();if (!user) throw new Error("Sign in to continue.");
    const input=await json(request);if(typeof input.owner!=="string" || input.owner.length>300) throw new Error("Invalid workspace.");
    if(input.owner!==user.userId) {
      const row=await db().prepare("SELECT role,user_id FROM team_members WHERE owner=? AND active=1 AND (user_id=? OR (user_id IS NULL AND email=?))").bind(input.owner,user.userId,user.email.toLowerCase()).first<{role:"admin"|"trainer"|"viewer";user_id:string|null}>();
      if(!row)throw new Error("Forbidden workspace.");
      if(!row.user_id) await db().batch([db().prepare("UPDATE team_members SET user_id=?,updated_at=? WHERE owner=? AND email=? AND active=1 AND user_id IS NULL").bind(user.userId,Date.now(),input.owner,user.email.toLowerCase()),audit({owner:input.owner,role:row.role,user},"team.accept",user.userId)]);
    }
    return Response.json({selected:true},{headers:{"Cache-Control":"no-store","Set-Cookie":`suraksha_workspace=${encodeURIComponent(input.owner)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000${new URL(request.url).protocol==='https:'?'; Secure':''}`}});
  } catch(e) {return failure(e);}
}
