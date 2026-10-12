"use client"; // START is a POST from the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import { startRecommended } from "@/lib/api";
import type { ApiError, Recommendation } from "@/lib/types";

type NextUpProps = {
  recommendation: Recommendation;
  fills: string | null; // "Fills rear delts, lower lats, …": what the day would light up
};

// The gold pennant on Home: the next split day, what it fills, and START.
// The planned regions themselves pulse on the big figure below, so the pennant has no figure.
export default function NextUp({ recommendation, fills }: NextUpProps) {
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
        className="grid justify-items-start gap-2 bg-acc py-3.5 pr-[30px] pl-[18px] text-on-acc [clip-path:polygon(0_0,100%_0,calc(100%-18px)_50%,100%_100%,0_100%)]"
      >
        <span className="text-[0.8rem] font-bold tracking-[0.14em]">Next up</span>
        <b data-testid="next-up-name" className="font-display text-[2.1rem] leading-[0.9] font-normal break-words">
          {recommendation.name}
        </b>
        {covered ? (
          <span data-testid="next-up-covered" className="text-[0.9rem] font-bold tracking-[0.1em]">
            Week covered
          </span>
        ) : (
          <>
            {fills && (
              <span data-testid="next-up-fills" className="text-[0.9rem] leading-snug font-semibold">
                {fills}
              </span>
            )}
            <button
              type="button"
              onClick={handleStart}
              disabled={busy}
              data-testid="start-recommended"
              className="mt-1 min-h-12 bg-on-acc px-8 font-display text-[1.1rem] text-acc disabled:opacity-40"
            >
              Start
            </button>
          </>
        )}
      </div>
      <ErrorLine error={error} />
    </section>
  );
}
