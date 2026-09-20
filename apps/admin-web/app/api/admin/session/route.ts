import { getAppUser,isOperator } from "@/lib/auth";
import { access, db, failure, json, audit, digest } from "@/lib/server";
export async function GET() {
  try {
    const user = await getAppUser();if (!user) throw new Error("Sign in to continue.");
    const choices = await db().prepare("SELECT m.owner,m.role,m.user_id,c.name FROM team_members m LEFT JOIN training_centres c ON c.owner=m.owner JOIN centre_approvals a ON a.owner=m.owner AND a.status='approved' WHERE m.active=1 AND m.user_id=?").bind(user.userId).all();
    const own=await db().prepare("SELECT status,reason FROM centre_approvals WHERE owner=?").bind(user.userId).first();
    let current;try { current = await access(); } catch { current = null; }
    const centre = current ? await db().prepare("SELECT name,site FROM training_centres WHERE owner=?").bind(current.owner).first() : null;
    return Response.json({user,operator:isOperator(user.userId),application:own??{status:"not-requested",reason:""}, current: current ? {owner:current.owner, role:current.role, name:centre?.name ?? "My training centre", site:centre?.site ?? ""} : null, workspaces:[...(own?.status==='approved'?[{owner:user.userId,role:"admin",name:"My approved centre",personal:true}]:[]),...choices.results.filter(r=>r.owner!==user.userId)]}, {headers:{"Cache-Control":"no-store"}});
  } catch(e) {return failure(e);}
}
export async function POST(request: Request) {
  try {
    const user = await getAppUser();if (!user) throw new Error("Sign in to continue.");
    const input=await json(request);
    let selected=input.owner;
    if(typeof input.invitation==="string" && input.invitation.length<500) {
      const [who,code]=input.invitation.trim().split(".");
      if(!who || !code)throw new Error("Invalid invitation code.");
      const approved=await db().prepare("SELECT owner FROM centre_approvals WHERE owner=? AND status='approved'").bind(who).first();
      if(!approved)throw new Error("Forbidden centre: approval is required.");
      const hash=await digest(code);
      const row=await db().prepare("SELECT role FROM team_members WHERE owner=? AND email=? AND active=1 AND user_id IS NULL AND invite_hash=? AND invite_expires_at>?").bind(who,user.email.toLowerCase(),hash,Date.now()).first<{role:"admin"|"trainer"|"viewer"|"certifier"}>();
      if(!row)throw new Error("Invalid or expired invitation. Ask your administrator for a new code.");
      const result=await db().batch([db().prepare("UPDATE team_members SET user_id=?,invite_hash=NULL,invite_expires_at=NULL,updated_at=? WHERE owner=? AND email=? AND active=1 AND user_id IS NULL AND invite_hash=? AND invite_expires_at>?").bind(user.userId,Date.now(),who,user.email.toLowerCase(),hash,Date.now()),audit({owner:who,role:row.role,user},"team.accept",user.userId,{},true)]);
      if(!result[0].meta.changes)throw new Error("Invalid or expired invitation.");
      selected=who;
    }
    if(typeof selected!=="string" || selected.length>300) throw new Error("Invalid workspace.");
    const centre=await db().prepare("SELECT status FROM centre_approvals WHERE owner=?").bind(selected).first();
    if(centre?.status!=="approved")throw new Error("Forbidden centre: approval is required.");
    if(selected!==user.userId) {
      const row=await db().prepare("SELECT role FROM team_members WHERE owner=? AND active=1 AND user_id=?").bind(selected,user.userId).first();
      if(!row)throw new Error("Forbidden workspace.");
    }
    return Response.json({selected:true},{headers:{"Cache-Control":"no-store","Set-Cookie":`suraksha_workspace=${encodeURIComponent(selected)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000${new URL(request.url).protocol==='https:'?'; Secure':''}`}});
  } catch(e) {return failure(e);}
}
