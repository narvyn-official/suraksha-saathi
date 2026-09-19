/** Operator-provisioned mail gateway; no provider credentials or delivery are assumed. */
export type MailConfig = Record<string, string | undefined>;
export function recoveryDelivery(config: MailConfig) {
  const base = new URL(config.BETTER_AUTH_URL ?? "");
  const endpoint = new URL(config.RECOVERY_MAIL_URL ?? "");
  const local = (url: URL) => url.protocol === "http:" && ["localhost", "127.0.0.1"].includes(url.hostname);
  if ((base.protocol !== "https:" && !local(base)) ||
      (endpoint.protocol !== "https:" && !(local(base) && local(endpoint))) ||
      endpoint.username || endpoint.password || endpoint.search || endpoint.hash ||
      !config.RECOVERY_MAIL_KEY || config.RECOVERY_MAIL_KEY.length < 32)
    throw new Error("Recovery delivery is not configured.");
  return { base, endpoint, key: config.RECOVERY_MAIL_KEY };
}
export function recoveryReady(config: MailConfig) {
  try { recoveryDelivery(config); return true; } catch { return false; }
}
export async function sendRecovery(config: MailConfig, email: string, token: string, send: typeof fetch = fetch) {
  const { base, endpoint, key } = recoveryDelivery(config);
  // Fragment tokens never enter HTTP access logs or Referer headers.
  const link = new URL("/reset-password", base); link.hash = new URLSearchParams({ token }).toString();
  const response = await send(endpoint, {
    method: "POST", redirect: "manual", signal: AbortSignal.timeout(10_000),
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
    body: JSON.stringify({ to: email, subject: "Reset your SurakshaAr password",
      text: `Open this link to choose a new password. It expires in 15 minutes and can be used once.\n\n${link.href}\n\nIf you did not request this, ignore this email. Your password has not changed.` }),
  });
  if (!response.ok) throw new Error("Recovery delivery failed.");
}
