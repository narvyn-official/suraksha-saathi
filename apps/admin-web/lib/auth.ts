import { betterAuth } from "better-auth/minimal";
import { drizzleAdapter } from "@better-auth/drizzle-adapter";
import { drizzle } from "drizzle-orm/d1";
import { env } from "cloudflare:workers";
import { headers } from "next/headers";
import * as schema from "@/db/auth-schema";
import { sendRecovery, recoveryReady } from "./recovery-mail";
export function auth() {
 const config=env as unknown as Record<string,string>;
 const secret=config.BETTER_AUTH_SECRET, baseURL=config.BETTER_AUTH_URL;
 if(!secret || secret.length<32 || !baseURL) throw new Error("Account service is not configured.");
 return betterAuth({appName:"SurakshaAr",secret,baseURL,
   database:drizzleAdapter(drizzle(env.DB as D1Database),{provider:"sqlite",schema,transaction:false}),
   emailAndPassword:{enabled:true,minPasswordLength:15,maxPasswordLength:128,
     resetPasswordTokenExpiresIn:900,revokeSessionsOnPasswordReset:true,
     onPasswordReset:async ({user}:{user:{id:string}})=>{await (env.DB as D1Database).prepare("DELETE FROM auth_verification WHERE value=? AND identifier LIKE 'reset-password:%'").bind(user.id).run();},
     ...(recoveryReady(config)?{sendResetPassword:async ({user,token}:{user:{email:string};token:string})=>sendRecovery(config,user.email,token)}:{})},
   session:{expiresIn:60*60*24*7,updateAge:60*60*24,cookieCache:{enabled:false}},
   rateLimit:{enabled:true,storage:"database",window:60,max:60,customRules:{"/sign-in/email":{window:60,max:8},"/sign-up/email":{window:60,max:5},"/request-password-reset":{window:60,max:3},"/reset-password":{window:60,max:5}}},
   advanced:{ipAddress:{ipAddressHeaders:["cf-connecting-ip"]}},
 });
}
export async function getAppUser() {
 const result=await auth().api.getSession({headers:await headers()});
 return result ? {userId:result.user.id,email:result.user.email,displayName:result.user.name} : null;
}
