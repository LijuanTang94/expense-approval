// Thin fetch wrapper around the Spring Boot API. Attaches the Bearer token from
// localStorage and normalises the backend's {code, message, fields} error envelope.

// Empty by default so requests are same-origin (`/api/...`) — in production the SPA is served by
// the Spring Boot app itself. A localhost default would point a deployed build at the *visitor's*
// machine. The Vite dev server sets VITE_API_URL to reach the API on another port.
const BASE = import.meta.env.VITE_API_URL ?? "";
const TOKEN_KEY = "expense.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;
  code: string;
  fields?: Record<string, string>;
  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

/**
 * Called when the API rejects our token (expired or invalid). Registered by the AuthProvider so
 * this module doesn't have to import React state. Without it an expired token left the app in a
 * half-logged-in state: the shell still rendered the user, but every request failed.
 */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: (() => void) | null) {
  onUnauthorized = fn;
}

export async function api<T>(path: string, opts: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = getToken();

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method: opts.method ?? "GET",
      headers: {
        ...(opts.body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
  } catch {
    // Offline, DNS failure, connection refused — surface as an ApiError like everything else so
    // callers only ever have one error type to handle.
    throw new ApiError(0, "NETWORK_ERROR", "Could not reach the server. Check your connection.");
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  // Not every response is JSON: a proxy 502, an HTML error page, or the SPA's own index.html
  // returned for a mistyped path would make an unguarded JSON.parse throw a SyntaxError that
  // escapes as an unrecognised error type and hides the real status.
  let data: any;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    if (!res.ok) {
      throw new ApiError(res.status, "UNEXPECTED_RESPONSE", res.statusText || `Request failed (${res.status})`);
    }
    throw new ApiError(res.status, "UNEXPECTED_RESPONSE", "Server returned an unexpected response");
  }

  if (!res.ok) {
    if (res.status === 401) {
      // Token expired or invalid: clear it and let the app return to the login screen rather than
      // leaving a stale session that fails every request.
      setToken(null);
      onUnauthorized?.();
    }
    const code = data?.code ?? "ERROR";
    const message = data?.message ?? `Request failed (${res.status})`;
    throw new ApiError(res.status, code, message, data?.fields);
  }
  return data as T;
}
