import { auth } from "@/lib/auth";
export async function GET(request:Request) { return auth().handler(request); }
export async function POST(request:Request) {
 const response=await auth().handler(request);
 if(response.ok && ["/api/auth/sign-in/email","/api/auth/sign-up/email","/api/auth/sign-out"].includes(new URL(request.url).pathname)) {
   // A different account must not inherit the last account's centre selection.
   const headers=new Headers(response.headers);
   headers.append("Set-Cookie",`suraksha_workspace=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${new URL(request.url).protocol==='https:'?'; Secure':''}`);
   return new Response(response.body,{status:response.status,statusText:response.statusText,headers});
 }
 return response;
}
