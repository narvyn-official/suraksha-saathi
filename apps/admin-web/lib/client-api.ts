export class RequestError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

/** Requests time out, never cache private data, and don't expose HTML/proxy errors. */
export async function requestJson<T>(path: string, method = "GET", body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      method, cache: "no-store", credentials: "same-origin", redirect: "error",
      signal: AbortSignal.timeout(20_000),
      headers: body === undefined ? { Accept: "application/json" } : { Accept: "application/json", "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch { throw new RequestError("Could not reach your training centre. Check your connection and try again.", 0); }
  let data: unknown;
  try { data = await response.json(); }
  catch { throw new RequestError("The service returned an unreadable response. Please try again.", response.status); }
  if (!data || typeof data !== "object" || (Array.isArray(data) && path!=="/api/auth/list-sessions"))
    throw new RequestError("The service returned an unreadable response. Please try again.", response.status);
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/api/auth/")) {
      window.location.replace("/login?next="+encodeURIComponent(window.location.pathname+window.location.search+window.location.hash));
      throw new RequestError("Your session has ended. Sign in again.", 401);
    }
    const info = data as { error?: unknown; message?: unknown };
    const reason = info.error ?? info.message;
    throw new RequestError(response.status === 429 ? "Too many attempts. Wait a minute before trying again." :
      response.status >= 500 ? "The service is temporarily unavailable. Please try again later." :
      typeof reason === "string" ? reason : "Could not complete the request. Please try again.", response.status);
  }
  return data as T;
}
