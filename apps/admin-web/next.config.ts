import type { NextConfig } from "next";
const nextConfig: NextConfig = {
 async headers(){return [{source:"/:path*",headers:[
   {key:"X-Content-Type-Options",value:"nosniff"},
   {key:"X-Frame-Options",value:"DENY"},
   {key:"Referrer-Policy",value:"no-referrer"},
 ]},... ["/","/login","/forgot-password","/reset-password","/api/:path*"].map(source=>({source,headers:[{key:"Cache-Control",value:"private, no-store"}]}))];},
};
export default nextConfig;
