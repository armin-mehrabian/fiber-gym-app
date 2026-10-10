// Types that mirror the backend API contract (JSON, camelCase).

// GET /api/health
export type Health = {
  status: "ok" | "degraded";
  database: "up" | "down";
};
