"use client"; // starting a workout is a POST from the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import { startSession } from "@/lib/api";
import type { ApiError } from "@/lib/types";

// START WORKOUT creates a workout and opens it; RESUME WORKOUT just opens the one in progress.
export default function StartWorkoutButton({ hasActive }: { hasActive: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function handleStart() {
    setBusy(true);
    setError(null);
    const result = await startSession();
    // 409 = a workout is already running (e.g. started in another tab): open that one instead.
    if (!result.ok && result.error.status !== 409) {
      setBusy(false);
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
      <PennantButton onClick={handleStart} disabled={busy} testId="start-workout">
        Start workout
      </PennantButton>
      <ErrorLine error={error} />
    </div>
  );
}
