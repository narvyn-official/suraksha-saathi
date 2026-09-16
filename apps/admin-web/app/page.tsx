import { getAppUser } from "@/lib/auth";
import Dashboard from "@/components/training/Dashboard";
import Login from "@/components/training/Login";
export const dynamic = "force-dynamic";
export default async function Page() {
  const user = await getAppUser();
  return user ? <Dashboard /> : <Login />;
}
