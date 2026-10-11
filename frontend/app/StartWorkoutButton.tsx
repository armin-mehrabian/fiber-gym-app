"use client"; // starting a workout is a POST from the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import { startSession } from "@/lib/api";
import type { ApiError } from "@/lib/types";

// RESUME WORKOUT opens the workout in progress. Without one, a quiet "Start empty workout"
// sits under the NEXT UP pennant, for days you want to pick every exercise yourself.
export default function StartWorkoutButton({ hasActive }: { hasActive: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function handleStart() {
    setBusy(true);
    setError(null);
    const result = await startSession();
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

  if (hasActive) {
    return (
      <PennantButton onClick={() => router.push("/workout")} testId="resume-workout">
        Resume workout
      </PennantButton>
    );
  }
  return (
    <div className="grid gap-2">
      <button
        type="button"
        onClick={handleStart}
        disabled={busy}
        data-testid="start-workout"
        className="min-h-12 text-[0.85rem] font-bold tracking-[0.1em] text-mut disabled:opacity-40"
      >
        Start empty workout
      </button>
      <ErrorLine error={error} />
    </div>
  );
}
