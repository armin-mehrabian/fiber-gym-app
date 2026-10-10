import { Suspense } from "react";
import BackendStatus from "@/components/BackendStatus";
import { guardPage } from "@/lib/guard";

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function HomePage() {
  await guardPage("/");

  return (
    <div className="grid gap-2">
      <h1 className="font-display text-[1.6rem] text-acc">Fiber</h1>
      {/* The wordmark shows instantly; the status line streams in once the backend answers. */}
      <Suspense fallback={<p className="text-xs tracking-[0.12em] text-mut">Backend · …</p>}>
        <BackendStatus />
      </Suspense>
    </div>
  );
}
