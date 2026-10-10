// The ONLY place in the frontend that talks to the backend.
//
// Two kinds of calls live here:
// - Server helpers (getMe, getSettings, …) run inside server components. They call the backend
//   directly and pass on the visitor's cookies, so the backend knows who is asking.
// - Browser helpers (login, saveSettings, …) run in client components. They call the relative
//   "/api/..." path (proxied to the backend by next.config.ts), so the browser sends the cookies itself.
import type {
  ApiError,
  ApiResult,
  Equipment,
  Health,
  Region,
  Settings,
  SettingsUpdate,
  Target,
  User,
} from "@/lib/types";

// A server has no page origin to resolve "/api" against, so server code calls the backend directly.
// The browser uses relative "/api/..." paths, which next.config.ts rewrites to the backend.
function apiUrl(path: string): string {
  const isServer = typeof window === "undefined";
  const base = isServer ? (process.env.BACKEND_URL ?? "http://localhost:8080") : "";
  return `${base}${path}`;
}

// ---------------------------------------------------------------- server helpers

// Returns null when the backend can't be reached, so pages still render instead of crashing.
export async function getHealth(): Promise<Health | null> {
  try {
    const response = await fetch(apiUrl("/api/health"), {
      cache: "no-store",
      // Give up after 3s so a hanging backend shows OFFLINE instead of loading forever.
      signal: AbortSignal.timeout(3000),
    });
    // 503 is still a real Health reply ("degraded" / "down"); any other error status is not.
    if (!response.ok && response.status !== 503) {
      return null;
    }
    return (await response.json()) as Health;
  } catch {
    // Network error, timeout, or a reply that isn't JSON.
    return null;
  }
}

// GET from the backend on behalf of the visitor: their cookies (the session) go along.
async function serverGet(path: string): Promise<Response> {
  // Loaded here, not at the top: next/headers only exists on the server, and this file is
  // also bundled for the browser (for the helpers further down).
  const { cookies } = await import("next/headers");
  const cookieStore = await cookies();
  return fetch(apiUrl(path), {
    headers: { cookie: cookieStore.toString() },
    cache: "no-store",
  });
}

// For pages that can't work without the data: anything but 2xx is a real error.
async function serverGetJson<T>(path: string): Promise<T> {
  const response = await serverGet(path);
  if (!response.ok) {
    throw new Error(`GET ${path} failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

// The logged-in user, or null if the visitor isn't logged in (401).
export async function getMe(): Promise<User | null> {
  const response = await serverGet("/api/me");
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`GET /api/me failed with status ${response.status}`);
  }
  return (await response.json()) as User;
}

export function getSettings(): Promise<Settings> {
  return serverGetJson<Settings>("/api/settings");
}

export function getTargets(): Promise<Target[]> {
  return serverGetJson<Target[]>("/api/targets");
}

export function getRegions(): Promise<Region[]> {
  return serverGetJson<Region[]>("/api/regions");
}

// null when the list can't be loaded (e.g. an older backend without this endpoint).
// The screens then show a short note instead of the equipment tiles.
export async function getEquipment(): Promise<Equipment[] | null> {
  try {
    return await serverGetJson<Equipment[]>("/api/equipment");
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------- browser helpers

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

// Reads one cookie from a "a=1; b=2" string (the format of document.cookie).
export function readCookie(cookieString: string, name: string): string | null {
  for (const part of cookieString.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) {
      return decodeURIComponent(rest.join("="));
    }
  }
  return null;
}

// Turns any failed reply into an ApiError. The body is whatever the server sent (maybe not JSON at all).
export function toApiError(status: number, body: unknown): ApiError {
  const reply = typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  const fields: Record<string, string> = {};
  if (typeof reply.fields === "object" && reply.fields !== null) {
    for (const [field, message] of Object.entries(reply.fields)) {
      if (typeof message === "string") {
        fields[field] = message;
      }
    }
  }
  let error = typeof reply.error === "string" ? reply.error : `Something went wrong (${status})`;
  if (status === 0) {
    error = "Can't reach the server";
  }
  return { status, error, fields };
}

// Makes sure the XSRF-TOKEN cookie exists. The backend sets it on this call.
export async function ensureCsrf(): Promise<void> {
  await fetch("/api/auth/csrf", { cache: "no-store" });
}

// Parses a reply body as JSON. Empty bodies (204) and non-JSON bodies give null.
async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

// One browser request that changes data. Every POST/PUT/PATCH/DELETE must echo the
// XSRF-TOKEN cookie in the X-XSRF-TOKEN header; another site can't read our cookie, so it can't forge this.
async function send<T>(method: "POST" | "PUT" | "PATCH" | "DELETE", path: string, body?: unknown): Promise<ApiResult<T>> {
  try {
    if (!readCookie(document.cookie, CSRF_COOKIE)) {
      await ensureCsrf();
    }
    const response = await fetch(path, {
      method,
      headers: {
        "Content-Type": "application/json",
        [CSRF_HEADER]: readCookie(document.cookie, CSRF_COOKIE) ?? "",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await readBody(response);
    if (!response.ok) {
      return { ok: false, error: toApiError(response.status, data) };
    }
    return { ok: true, data: data as T };
  } catch {
    // The request never got an answer (offline, backend down).
    return { ok: false, error: toApiError(0, null) };
  }
}

export function signup(email: string, password: string, timezone: string): Promise<ApiResult<User>> {
  return send<User>("POST", "/api/auth/signup", { email, password, timezone });
}

export function login(email: string, password: string): Promise<ApiResult<User>> {
  return send<User>("POST", "/api/auth/login", { email, password });
}

// A 401 means the session had already ended: for the user that's the same as logged out.
export async function logout(): Promise<ApiResult<null>> {
  const result = await send<null>("POST", "/api/auth/logout");
  if (!result.ok && result.error.status === 401) {
    return { ok: true, data: null };
  }
  return result;
}

export function saveSettings(update: SettingsUpdate): Promise<ApiResult<Settings>> {
  return send<Settings>("PUT", "/api/settings", update);
}

// Sends only the changed rows; the reply is the full list.
export function saveTargets(changes: Target[]): Promise<ApiResult<Target[]>> {
  return send<Target[]>("PUT", "/api/targets", changes);
}
