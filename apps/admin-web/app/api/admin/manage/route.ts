import { z } from "zod";
import { access, audit, db, failure, json } from "@/lib/server";
import { curriculum } from "@/lib/grading";
import { assignmentStatus } from "@/lib/access";
const id = z.string().uuid();
const command = z.discriminatedUnion("action", [
  z.object({action:z.literal("settings"),name:z.string().trim().min(2).max(100),site:z.string().trim().max(150)}).strict(),
  z.object({action:z.literal("member"),email:z.string().trim().email().max(254),role:z.enum(["admin","trainer","viewer"]),active:z.boolean()}).strict(),
  z.object({action:z.literal("worker"),id:id.optional(),androidId:id.optional(),name:z.string().trim().min(1).max(80),sector:z.enum(["Unspecified","Mining","Steel","Mica","Other"])}).strict(),
  z.object({action:z.literal("assign"),workerIds:z.array(id).min(1).max(100),moduleId:z.string().max(32),dueAt:z.number().int().positive().max(8640000000000000),note:z.string().trim().max(500)}).strict(),
  z.object({action:z.literal("cancel"),id,reason:z.string().trim().min(5).max(500)}).strict(),
]);
export async function GET() {
  try {
    const a=await access(), who=a.owner;
    const [workers,assignments,logs,team,counts] = await Promise.all([
      db().prepare("SELECT * FROM workers WHERE owner=? ORDER BY updated_at DESC,id LIMIT 1000").bind(who).all(),
      db().prepare(`SELECT t.*,w.name AS worker_name,w.sector,(SELECT p.payload FROM attempts p WHERE p.owner=t.owner AND p.worker_id=t.worker_id AND json_extract(p.payload,'$.moduleId')=t.module_id AND json_extract(p.payload,'$.kind')='assessment' AND json_extract(p.payload,'$.contentVersion')=t.content_version AND json_extract(p.payload,'$.startedAt')>=t.created_at ORDER BY json_extract(p.payload,'$.endedAt') DESC,p.id DESC LIMIT 1) AS latest_payload FROM training_assignments t LEFT JOIN workers w ON w.owner=t.owner AND w.id=t.worker_id WHERE t.owner=? ORDER BY t.created_at DESC,t.id DESC LIMIT 1000`).bind(who).all<{latest_payload:string|null;due_at:number;cancelled_at:number|null}>(),
      db().prepare("SELECT id,actor_email,action,target,detail,at FROM audit_log WHERE owner=? ORDER BY at DESC,id DESC LIMIT 200").bind(who).all(),
      a.role==='admin'?db().prepare("SELECT email,role,active,user_id,updated_at FROM team_members WHERE owner=? ORDER BY email LIMIT 200").bind(who).all():Promise.resolve({results:[]}),
      db().prepare("SELECT (SELECT COUNT(*) FROM workers WHERE owner=?) AS workers,(SELECT COUNT(*) FROM training_assignments WHERE owner=?) AS assignments,(SELECT COUNT(*) FROM audit_log WHERE owner=?) AS audit").bind(who,who,who).first(),
    ]);
    const now=Date.now();
    return Response.json({workers:workers.results,assignments:assignments.results.map(({latest_payload,...r})=>{const latest=latest_payload?JSON.parse(latest_payload):null;const completed_at=latest?.result?.passed?latest.endedAt:null;return {...r,completed_at,latestAttemptId:latest?.id ?? null,status:assignmentStatus({...r,completed_at},now)};}),audit:logs.results.map(r=>({...r,detail:JSON.parse(String(r.detail))})),team:team.results,counts,now},{headers:{"Cache-Control":"no-store"}});
  } catch(e){return failure(e);}
}
export async function POST(request:Request) {
  try {
    const parsed=command.safeParse(await json(request));if(!parsed.success)throw new Error("Invalid form. Check required fields and limits.");
    const c=parsed.data,a=await access(c.action==='settings'||c.action==='member'?'admin':'write'),now=Date.now(),statements:D1PreparedStatement[]=[];
    let target='';
    if(c.action==='settings') {
      target=a.owner;statements.push(db().prepare("INSERT INTO training_centres(owner,name,site,updated_at) VALUES(?,?,?,?) ON CONFLICT(owner) DO UPDATE SET name=excluded.name,site=excluded.site,updated_at=excluded.updated_at").bind(a.owner,c.name,c.site,now));
    } else if(c.action==='member') {
      const email=c.email.toLowerCase();if(email===a.user.email.toLowerCase())throw new Error("Invalid member change: you cannot change your own access.");
      const prior=await db().prepare("SELECT user_id FROM team_members WHERE owner=? AND email=?").bind(a.owner,email).first<{user_id:string|null}>();
      if(prior?.user_id===a.owner)throw new Error("Forbidden: the workspace owner's access cannot be changed.");
      target=email;statements.push(db().prepare("INSERT INTO team_members(owner,email,role,active,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(owner,email) DO UPDATE SET role=excluded.role,active=excluded.active,updated_at=excluded.updated_at").bind(a.owner,email,c.role,c.active?1:0,now));
    } else if(c.action==='worker') {
      if(c.id && c.androidId)throw new Error("Invalid worker ID change. Saved identity cannot be rewritten.");
      target=c.id??c.androidId??crypto.randomUUID();
      if(c.androidId && await db().prepare("SELECT id FROM workers WHERE owner=? AND id=?").bind(a.owner,c.androidId).first())throw new Error("Already registered: edit the existing worker.");
      if(c.id && !(await db().prepare("SELECT id FROM workers WHERE owner=? AND id=?").bind(a.owner,c.id).first()))throw new Error("No worker found in this workspace.");
      statements.push(db().prepare("INSERT INTO workers(owner,id,name,sector,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(owner,id) DO UPDATE SET name=excluded.name,sector=excluded.sector,updated_at=excluded.updated_at").bind(a.owner,target,c.name,c.sector,now));
    } else if(c.action==='assign') {
      if(c.dueAt<=now||c.dueAt>now+366*86400000*5)throw new Error("Invalid due date. Choose a future date within five years.");
      if(!curriculum.modules.some(m=>m.id===c.moduleId))throw new Error("Invalid training module.");
      if(new Set(c.workerIds).size!==c.workerIds.length)throw new Error("Invalid duplicate worker selection.");
      for(const worker of c.workerIds) {
        if(!(await db().prepare("SELECT id FROM workers WHERE owner=? AND id=?").bind(a.owner,worker).first()))throw new Error("No worker found in this workspace.");
        const task=crypto.randomUUID();target=task;statements.push(db().prepare("INSERT INTO training_assignments(owner,id,worker_id,module_id,content_version,due_at,created_at,created_by,note) VALUES(?,?,?,?,?,?,?,?,?)").bind(a.owner,task,worker,c.moduleId,curriculum.version,c.dueAt,now,a.user.userId,c.note));
        statements.push(audit(a,"assignment.create",task,{workerId:worker,moduleId:c.moduleId,dueAt:c.dueAt}));
      }
    } else {
      const exists=await db().prepare("SELECT id FROM training_assignments WHERE owner=? AND id=? AND cancelled_at IS NULL").bind(a.owner,c.id).first();if(!exists)throw new Error("No active assignment found.");
      target=c.id;statements.push(db().prepare("UPDATE training_assignments SET cancelled_at=? WHERE owner=? AND id=? AND cancelled_at IS NULL").bind(now,a.owner,c.id));
    }
    if(c.action!=='assign') statements.push(audit(a,`admin.${c.action}`,target,c.action==='cancel'?{reason:c.reason}:c.action==='member'?{role:c.role,active:c.active}:{}));
    await db().batch(statements);
    return Response.json({saved:true,id:target},{headers:{"Cache-Control":"no-store"}});
  }catch(e){return failure(e);}
}
