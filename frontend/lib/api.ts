// The ONLY place in the frontend that talks to the backend.
import type { Health } from "@/lib/types";

// A server has no page origin to resolve "/api" against, so server code calls the backend directly.
// The browser uses relative "/api/..." paths, which next.config.ts rewrites to the backend.
function apiUrl(path: string): string {
  const isServer = typeof window === "undefined";
  const base = isServer ? (process.env.BACKEND_URL ?? "http://localhost:8080") : "";
  return `${base}${path}`;
}

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
