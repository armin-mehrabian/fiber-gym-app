import { connection } from "next/server";
import { getHealth } from "@/lib/api";

// Server component: asks the backend for its health on every request.
export default async function BackendStatus() {
  // Wait for a real request: there's no backend during `next build`, so never check at build time.
  await connection();
  const health = await getHealth();

  const text = health
    ? `Backend · ${health.status} · DB ${health.database}`
    : "Backend · Offline";

  return <p className="text-xs tracking-[0.12em] text-mut">{text}</p>;
}
