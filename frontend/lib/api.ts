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
  Exercise,
  HistoryWeek,
  Region,
  Session,
  SetChanges,
  SetValues,
  Settings,
  SettingsUpdate,
  Target,
  User,
  WeekCoverage,
  WorkoutSet,
} from "@/lib/types";

// A server has no page origin to resolve "/api" against, so server code calls the backend directly.
// The browser uses relative "/api/..." paths, which next.config.ts rewrites to the backend.
function apiUrl(path: string): string {
  const isServer = typeof window === "undefined";
  const base = isServer ? (process.env.BACKEND_URL ?? "http://localhost:8080") : "";
  return `${base}${path}`;
}

// ---------------------------------------------------------------- server helpers

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

// The workout in progress, or null when there is none (the backend answers 204).
export async function getActiveSession(): Promise<Session | null> {
  const response = await serverGet("/api/sessions/active");
  if (response.status === 204) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`GET /api/sessions/active failed with status ${response.status}`);
  }
  return (await response.json()) as Session;
}

// One of the user's workouts, or null if it doesn't exist (or belongs to someone else: also 404).
// A malformed id gives 400, which means the same thing to the visitor.
export async function getSession(id: string): Promise<Session | null> {
  const path = `/api/sessions/${encodeURIComponent(id)}`;
  const response = await serverGet(path);
  if (response.status === 404 || response.status === 400) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`GET ${path} failed with status ${response.status}`);
  }
  return (await response.json()) as Session;
}

// Finished workouts grouped by week, newest first.
export function getHistory(weeks = 12): Promise<HistoryWeek[]> {
  return serverGetJson<HistoryWeek[]>(`/api/history?weeks=${weeks}`);
}

// This week's coverage per region (the backend picks the week in the user's timezone).
export function getWeekCoverage(): Promise<WeekCoverage> {
  return serverGetJson<WeekCoverage>("/api/coverage/week");
}

// Exercises the user's equipment allows, optionally filtered by name.
export function getMyExercises(query: string): Promise<Exercise[]> {
  const q = query.trim();
  const search = q ? `&q=${encodeURIComponent(q)}` : "";
  return serverGetJson<Exercise[]>(`/api/exercises?mine=true${search}`);
}

// One catalog exercise. Only used for a nicer default weight, so a failure gives null instead of an error page.
export async function getExercise(id: string): Promise<Exercise | null> {
  try {
    return await serverGetJson<Exercise>(`/api/exercises/${encodeURIComponent(id)}`);
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

// ---------------------------------------------------------------- workout (browser)

// Starts an empty workout. 409 if one is already in progress.
export function startSession(): Promise<ApiResult<Session>> {
  return send<Session>("POST", "/api/sessions", {});
}

export function addExercise(sessionId: string, exerciseId: string): Promise<ApiResult<Session>> {
  return send<Session>("POST", `/api/sessions/${sessionId}/exercises`, { exerciseId });
}

// The reply is the whole workout, because the later exercises move up one position.
export function removeExercise(sessionId: string, position: number): Promise<ApiResult<Session>> {
  return send<Session>("DELETE", `/api/sessions/${sessionId}/exercises/${position}`);
}

export function addSet(sessionId: string, position: number, values: SetValues): Promise<ApiResult<WorkoutSet>> {
  return send<WorkoutSet>("POST", `/api/sessions/${sessionId}/exercises/${position}/sets`, values);
}

// Only the fields that are sent change.
export function updateSet(setId: string, changes: SetChanges): Promise<ApiResult<WorkoutSet>> {
  return send<WorkoutSet>("PATCH", `/api/sets/${setId}`, changes);
}

// The backend renumbers the remaining sets of that exercise 1..n.
export function deleteSet(setId: string): Promise<ApiResult<null>> {
  return send<null>("DELETE", `/api/sets/${setId}`);
}

export function finishSession(sessionId: string): Promise<ApiResult<Session>> {
  return send<Session>("POST", `/api/sessions/${sessionId}/finish`);
}

// Deletes the workout and all its sets (only allowed while it's in progress).
export function discardSession(sessionId: string): Promise<ApiResult<null>> {
  return send<null>("DELETE", `/api/sessions/${sessionId}`);
}
