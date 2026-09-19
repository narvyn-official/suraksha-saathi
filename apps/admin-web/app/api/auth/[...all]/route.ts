import { auth } from "@/lib/auth";
export async function GET(request:Request) { return auth().handler(request); }
import { env } from "cloudflare:workers";
import { recoveryReady } from "@/lib/recovery-mail";
import { readJson } from "@/lib/http";
export async function POST(request:Request) {
 try {
 const body=await readJson(request,32_000);
 const headers=new Headers(request.headers);headers.delete("content-length");
 request=new Request(request.url,{method:"POST",headers,body:JSON.stringify(body)});
 if(new URL(request.url).pathname==="/api/auth/request-password-reset"&&!recoveryReady(env as unknown as Record<string,string>))
   return Response.json({error:"Password recovery is unavailable. Contact your training centre administrator."},{status:503,headers:{"Cache-Control":"no-store"}});
 const response=await auth().handler(request);
 if(response.ok && ["/api/auth/sign-in/email","/api/auth/sign-up/email","/api/auth/sign-out"].includes(new URL(request.url).pathname)) {
   // A different account must not inherit the last account's centre selection.
   const headers=new Headers(response.headers);
   headers.append("Set-Cookie",`suraksha_workspace=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${new URL(request.url).protocol==='https:'?'; Secure':''}`);
   return new Response(response.body,{status:response.status,statusText:response.statusText,headers});
 }
 return response;
 } catch {
   return Response.json({error:"Could not complete the account request. Check the connection or contact your training centre."},{status:400,headers:{"Cache-Control":"no-store"}});
 }
}
