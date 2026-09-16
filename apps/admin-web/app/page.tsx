import { getChatGPTUser } from "@/app/chatgpt-auth";
import Dashboard from "@/components/training/Dashboard";
import Login from "@/components/training/Login";
export const dynamic = "force-dynamic";
export default async function Page() {
  const user = await getChatGPTUser();
  return user ? <Dashboard /> : <Login />;
}
