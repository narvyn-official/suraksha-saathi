import { env } from "cloudflare:workers";
import { headers } from "next/headers";
import { permitted, type Role } from "@/lib/access";
import { getAppUser } from "@/lib/auth";
export function db() {
  if (!env.DB) throw new Error("Training storage is unavailable.");
  return env.DB as D1Database;
}
export type Access = { owner: string; role: Role; user: NonNullable<Awaited<ReturnType<typeof getAppUser>>> };
export async function access(permission: "read" | "write" | "admin" = "read"): Promise<Access> {
  const user = await getAppUser();
  if (!user) throw new Error("Sign in to access training records.");
  const cookie = (await headers()).get("cookie") ?? "";
  const value = cookie.split(";").map(v => v.trim()).find(v => v.startsWith("suraksha_workspace="))?.split("=").slice(1).join("=");
  let who = user.userId;
  if (value) { try { who = decodeURIComponent(value); } catch { throw new Error("Forbidden workspace."); } }
  let role: Role = "admin";
  if (who !== user.userId) {
    const member = await db().prepare("SELECT role FROM team_members WHERE owner=? AND user_id=? AND active=1").bind(who, user.userId).first<{role: Role}>();
    if (!member || !["admin", "trainer", "viewer"].includes(member.role)) throw new Error("Forbidden workspace. Select your personal workspace to continue.");
    role = member.role;
  }
  if (!permitted(role, permission)) throw new Error("Forbidden: your role does not allow this action.");
  return {owner: who, role, user};
}
export async function owner(permission: "read" | "write" | "admin" = "read") { return (await access(permission)).owner; }
export function audit(a: Access, action: string, target: string, detail: unknown = {}, onlyAfterChange = false) {
  return db().prepare("INSERT INTO audit_log(id,owner,actor,actor_email,action,target,detail,at) SELECT ?,?,?,?,?,?,?,?" + (onlyAfterChange ? " WHERE changes() > 0" : ""))
    .bind(crypto.randomUUID(), a.owner, a.user.userId, a.user.email, action, target, JSON.stringify(detail), Date.now());
}
export function failure(error: unknown) {
  const message = error instanceof Error ? error.message : "Request failed";
  const allowed =
    /^(Invalid|Unsupported|No |Not |Sign in|Record conflict|Only |Forbidden|Already |Credential |Training |File |Unknown)/.test(
      message,
    );
  return Response.json(
    {
      error: allowed
        ? message
        : "Could not complete the request. Please try again.",
    },
    { status: message.startsWith("Sign in") ? 401 : message.startsWith("Forbidden") ? 403 : 400 },
  );
}
export async function json(request: Request) {
  const origin = request.headers.get("origin");
  if (origin && origin !== new URL(request.url).origin)
    throw new Error("Invalid request origin.");
  const text = await request.text();
  if (text.length > 1000000)
    throw new Error("File is too large. Maximum 1 MB.");
  try {
    return JSON.parse(text);
  } catch {
    throw new Error("Invalid JSON file.");
  }
}
export function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value !== null && typeof value === "object")
    return `{${Object.entries(value)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => `${JSON.stringify(k)}:${canonical(v)}`)
      .join(",")}}`;
  return JSON.stringify(value);
}
export async function digest(value: unknown) {
  const b = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(canonical(value)),
  );
  return Array.from(new Uint8Array(b), (n) =>
    n.toString(16).padStart(2, "0"),
  ).join("");
}
export function signingKey() {
  const k = (env as unknown as Record<string, string>).ISSUER_PRIVATE_JWK;
  if (!k) throw new Error("Credential issuer is not configured.");
  return JSON.parse(k);
}
