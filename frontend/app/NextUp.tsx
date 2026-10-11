"use client"; // START is a POST from the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import FigurePair from "@/components/figure/FigurePair";
import { startRecommended } from "@/lib/api";
import { toRegionValues } from "@/lib/plan";
import type { ApiError, Recommendation } from "@/lib/types";

// The gold pennant on Home: the next split day, the regions it would hit, and START.
export default function NextUp({ recommendation }: { recommendation: Recommendation }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const covered = recommendation.exercises.length === 0;

  async function handleStart() {
    setBusy(true);
    setError(null);
    const exerciseIds = recommendation.exercises.map((exercise) => exercise.exerciseId);
    const result = await startRecommended(recommendation.splitDayId, exerciseIds);
    // Busy only covers the request itself. Next.js keeps Home alive in the background, so a flag
    // left on during the navigation would still be on when you come back (e.g. after discarding).
    // A second tap meanwhile is harmless: it gets a 409 and opens the same workout.
    setBusy(false);
    // 409 = a workout is already running (e.g. started in another tab): open that one instead.
    if (!result.ok && result.error.status !== 409) {
      setError(result.error);
      return;
    }
    router.push("/workout");
  }

  return (
    <section className="grid gap-2">
      <div
        data-testid="next-up"
        className="grid grid-cols-[1fr_auto] items-center gap-3 bg-acc py-3.5 pr-[30px] pl-[18px] text-on-acc [clip-path:polygon(0_0,100%_0,calc(100%-18px)_50%,100%_100%,0_100%)]"
      >
        <div className="grid min-w-0 justify-items-start gap-2">
          <span className="text-[0.8rem] font-bold tracking-[0.14em]">Next up</span>
          <b data-testid="next-up-name" className="font-display text-[2.1rem] leading-[0.9] font-normal break-words">
            {recommendation.name}
          </b>
          {covered ? (
            <span data-testid="next-up-covered" className="text-[0.9rem] font-bold tracking-[0.1em]">
              Week covered
            </span>
          ) : (
            <button
              type="button"
              onClick={handleStart}
              disabled={busy}
              data-testid="start-recommended"
              className="min-h-11 bg-on-acc px-6 font-display text-[1.05rem] text-acc disabled:opacity-40"
            >
              Start
            </button>
          )}
        </div>
        <FigurePair values={toRegionValues(recommendation.planned)} size="mini" height={96} />
      </div>
      <ErrorLine error={error} />
    </section>
  );
}
