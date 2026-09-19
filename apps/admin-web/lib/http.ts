/** Bound UTF-8 bytes while reading, including requests without Content-Length. */
export async function readJson(request: Request, limit = 1_000_000): Promise<Record<string, unknown>> {
  const origin = request.headers.get("origin");
  if ((origin && origin !== new URL(request.url).origin) || request.headers.get("sec-fetch-site") === "cross-site")
    throw new Error("Invalid request origin.");
  if (request.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json")
    throw new Error("Invalid content type. Send application/json.");
  const length = request.headers.get("content-length");
  if (length !== null && (!/^\d+$/.test(length) || Number(length) > limit))
    throw new Error("File is too large or its length is invalid.");
  if (!request.body) throw new Error("Invalid JSON object.");
  const reader = request.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let size = 0, text = "";
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) throw new Error("File is too large. Maximum 1 MB.");
      text += decoder.decode(value, { stream: true });
    }
    text += decoder.decode();
    const value: unknown = JSON.parse(text);
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid JSON object.");
    return value as Record<string, unknown>;
  } catch (error) {
    await reader.cancel().catch(() => {});
    if (error instanceof Error && /^(File|Invalid)/.test(error.message)) throw error;
    throw new Error("Invalid JSON file.");
  } finally { reader.releaseLock(); }
}
