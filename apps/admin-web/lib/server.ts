import { env } from "cloudflare:workers";
import { getChatGPTUser } from "@/app/chatgpt-auth";
export function db() {
  if (!env.DB) throw new Error("Training storage is unavailable.");
  return env.DB as D1Database;
}
export async function owner() {
  const user = await getChatGPTUser();
  if (!user) throw new Error("Sign in to access training records.");
  return user.userId;
}
export function failure(error: unknown) {
  const message = error instanceof Error ? error.message : "Request failed";
  const allowed =
    /^(Invalid|Unsupported|No |Not |Sign in|Record conflict|Only |Already |Credential |Training |File |Unknown)/.test(
      message,
    );
  return Response.json(
    {
      error: allowed
        ? message
        : "Could not complete the request. Please try again.",
    },
    { status: message.startsWith("Sign in") ? 401 : 400 },
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
