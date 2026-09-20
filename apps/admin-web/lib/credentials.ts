import trust from "./trusted-issuer.json" with { type: "json" };
export { trust };
import { validity, credentialStatus } from "./validity";
const enc = new TextEncoder();
function b64(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}
function decode(s: string) {
  return Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/")), (c) =>
    c.charCodeAt(0),
  );
}
export async function sign(payload: object, jwk: JsonWebKey) {
  const header = b64(
    enc.encode(JSON.stringify({ alg: "ES256", typ: "JWT", kid: trust.kid })),
  );
  const body = b64(enc.encode(JSON.stringify(payload)));
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    enc.encode(`${header}.${body}`),
  );
  return `${header}.${body}.${b64(new Uint8Array(signature))}`;
}
export async function verify(raw: unknown, now = Date.now()) {
  if (typeof raw !== "string" || raw.length > 6000)
    throw new Error("Invalid credential text.");
  const token = raw.trim().replace(/^SURAKSHA:CREDENTIAL:/, "");
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid signed credential.");
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(decode(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(decode(parts[1])));
  } catch {
    throw new Error("Invalid credential encoding.");
  }
  if (header.alg !== "ES256" || header.kid !== trust.kid)
    throw new Error("Unknown credential issuer.");
  const key = await crypto.subtle.importKey(
    "jwk",
    trust.jwk,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  if (
    !(await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" },
      key,
      decode(parts[2]),
      enc.encode(`${parts[0]}.${parts[1]}`),
    ))
  )
    throw new Error("Invalid credential signature.");
  if (
    payload.iss !== trust.issuer ||
    payload.kind !== "pilot-simulation" ||
    typeof payload.id !== "string" ||
    !["fire", "gas", "machinery", "ppe", "emergency"].includes(
      payload.moduleId,
    ) ||
    payload.practical !== "not-assessed" ||
    !Number.isSafeInteger(payload.iat)
  )
    throw new Error("Invalid credential scope.");
  return { token, payload, ...validity(payload, now) };
}

/** Metadata for trusted issuer records already stored in this workspace. This is not public-token verification. */
export function credentialView<T extends { token: string; revoked_at: number | null }>(row: T, now = Date.now()) {
  const payload = JSON.parse(new TextDecoder().decode(decode(row.token.split(".")[1])));
  const dates = validity(payload, now);
  return { ...row, governanceVersion: payload.governanceVersion ?? null, centreName: payload.centreName ?? null, approvedBy: payload.approvedBy ?? null, workerName: payload.workerName ?? null, moduleId: payload.moduleId, workerRef: payload.workerRef, score: payload.score, expiresAt: payload.expiresAt ?? null, ...dates, status: credentialStatus(dates, row.revoked_at) };
}
