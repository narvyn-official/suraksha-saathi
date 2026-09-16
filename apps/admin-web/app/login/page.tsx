import Login from "@/components/training/Login";
import { getAppUser } from "@/lib/auth";
import { redirect } from "next/navigation";
export const dynamic = "force-dynamic";
export default async function Page() { if(await getAppUser())redirect('/'); return <Login/>; }
