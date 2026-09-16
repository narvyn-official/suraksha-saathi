import Login from "@/components/training/Login";
import { getChatGPTUser } from "@/app/chatgpt-auth";
export const dynamic = "force-dynamic";
export default async function Page() { const user = await getChatGPTUser();return user ? <main className="workspace"><h1>You’re signed in</h1><p>{user.displayName}</p><a className="login-button" href="/">Open training centre</a><p><a href="/signout-with-chatgpt?return_to=/login" target="_top">Sign out</a></p></main> : <Login />; }
