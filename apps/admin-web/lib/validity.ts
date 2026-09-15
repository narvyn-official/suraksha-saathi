export type ExpiryStatus = "expired" | "within-validity" | "not-recorded";
export type Validity = { expiryStatus: ExpiryStatus; validityCheckedAt: number; issuedInFuture: boolean };
const MAX_DATE = 8_640_000_000_000_000;
function timestamp(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0 || value > MAX_DATE)
    throw new Error(`Invalid credential ${field}.`);
  return value;
}
/** Existing pilot iat and custom expiresAt are Unix milliseconds, not a JWT exp claim. */
export function validity(payload: { iat: unknown; expiresAt?: unknown }, now = Date.now()): Validity {
  timestamp(now, "verification time");
  const issued = timestamp(payload.iat, "iat");
  let expires: number | undefined;
  if (Object.hasOwn(payload, "expiresAt")) {
    expires = timestamp(payload.expiresAt, "expiresAt");
    if (expires <= issued) throw new Error("Expiry must follow issuance.");
  }
  return { expiryStatus: expires === undefined ? "not-recorded" : now >= expires ? "expired" : "within-validity",
    validityCheckedAt: now, issuedInFuture: issued > now };
}
export function requestedExpiry(value: unknown, now = Date.now()): number {
  if (value === undefined) throw new Error("Invalid expiry: choose a date approved by the site's training policy.");
  validity({ iat: now, expiresAt: value }, now);
  return value as number;
}
export function credentialStatus(value: Validity, revokedAt: number | null, known = true) {
  if (revokedAt !== null) return "revoked";
  if (value.issuedInFuture) return "clock-check-required";
  if (value.expiryStatus === "expired") return "expired";
  if (!known) return "signature-only";
  return value.expiryStatus === "not-recorded" ? "expiry-not-recorded" : "active";
}
export type CredentialDates = { issued_at: number; expiresAt?: number | null; revoked_at: number | null };
export function recordValidity(record: CredentialDates, now = Date.now()) {
  return validity({ iat: record.issued_at, ...(record.expiresAt == null ? {} : { expiresAt: record.expiresAt }) }, now);
}
export function recordStatus(record: CredentialDates, now = Date.now()) {
  return credentialStatus(recordValidity(record, now), record.revoked_at);
}
export function statusLabel(status: string) {
  return ({ active: "Within recorded validity", revoked: "Revoked", expired: "Expired · reassessment needed",
    "expiry-not-recorded": "No expiry recorded", "clock-check-required": "Check date and time",
    "signature-only": "Signature verified · revocation unknown" } as Record<string, string>)[status] ?? "Status unknown";
}
