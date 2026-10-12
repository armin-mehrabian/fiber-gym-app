"use client"; // ✓ DONE and SAVE are requests from the browser

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import { completeExercise } from "@/lib/api";
import type { ApiError, Units } from "@/lib/types";
import { toCompleteValues, type PlannedValues } from "@/lib/workout";
import ChangePanel from "./ChangePanel";

type RowActionsProps = {
  sessionId: string;
  position: number;
  name: string;
  planned: PlannedValues; // weight in the user's units
  units: Units;
};

// Under a workout row that isn't done yet: one big ✓ DONE that logs the plan in one tap,
// and CHANGE for days when the numbers were different.
export default function RowActions({ sessionId, position, name, planned, units }: RowActionsProps) {
  const router = useRouter();
  const [changing, setChanging] = useState(false);
  const [sending, setSending] = useState(false);
  // The page reload after a save. Until it lands the row still looks open, so the buttons wait too:
  // a second tap would log the sets twice.
  const [refreshing, startRefresh] = useTransition();
  const [error, setError] = useState<ApiError | null>(null);
  const busy = sending || refreshing;

  async function complete(values: PlannedValues) {
    setSending(true);
    setError(null);
    const result = await completeExercise(sessionId, position, toCompleteValues(values, units));
    setSending(false);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    setChanging(false);
    // Reload the page data from the server, so the row shows done and the figure updates.
    startRefresh(() => router.refresh());
  }

  return (
    <div className="grid gap-2">
      {changing ? (
        <ChangePanel
          name={name}
          initial={planned}
          units={units}
          busy={busy}
          onSave={complete}
          onCancel={() => setChanging(false)}
        />
      ) : (
        <div className="grid grid-cols-[1fr_auto] gap-2">
          <button
            type="button"
            onClick={() => complete(planned)}
            disabled={busy}
            aria-label={`Done: ${name}`}
            data-testid={`exercise-done-${position}`}
            className="min-h-[52px] bg-acc font-display text-[1.1rem] text-on-acc disabled:opacity-40"
          >
            ✓ Done
          </button>
          <button
            type="button"
            onClick={() => setChanging(true)}
            disabled={busy}
            aria-label={`Change: ${name}`}
            data-testid={`exercise-change-${position}`}
            className="min-h-[52px] border-[1.5px] border-edge px-5 font-bold text-mut disabled:opacity-40"
          >
            Change
          </button>
        </div>
      )}
      <ErrorLine error={error} />
    </div>
  );
}
