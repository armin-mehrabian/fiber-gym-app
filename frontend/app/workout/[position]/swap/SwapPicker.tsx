"use client"; // previewing and swapping happen in the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import ExerciseFigure from "@/components/figure/ExerciseFigure";
import FigurePair from "@/components/figure/FigurePair";
import { REGION_NAMES, type RegionId } from "@/components/figure/shapes";
import { swapExercise } from "@/lib/api";
import { swapPreviewPlan } from "@/lib/plan";
import type { Alternative, ApiError, SessionExercise } from "@/lib/types";

type SwapPickerProps = {
  sessionId: string;
  position: number; // the exercise being replaced
  exercises: SessionExercise[]; // the whole workout, for the preview
  alternatives: Alternative[]; // best match first
};

export default function SwapPicker({ sessionId, position, exercises, alternatives }: SwapPickerProps) {
  const router = useRouter();
  const [picked, setPicked] = useState<Alternative | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function handleUse() {
    if (!picked) return;
    setBusy(true);
    setError(null);
    const result = await swapExercise(sessionId, position, picked.exerciseId);
    if (!result.ok) {
      setBusy(false);
      setError(result.error);
      return;
    }
    router.push("/workout");
  }

  // Today's workout only, with the picked alternative in place: the stripes move as you tap.
  const plan = swapPreviewPlan(exercises, position, picked);

  return (
    <div className="grid gap-4">
      <FigurePair values={{}} plan={plan} size="hero" height={250} testId="swap-figure" />
      {alternatives.length === 0 ? (
        <p data-testid="swap-empty" className="text-center text-sm tracking-[0.12em] text-mut">
          No alternatives
        </p>
      ) : (
        <div className="grid">
          {alternatives.map((alternative) => {
            const active = picked?.exerciseId === alternative.exerciseId;
            return (
              <button
                key={alternative.exerciseId}
                type="button"
                onClick={() => setPicked(alternative)}
                disabled={busy}
                aria-pressed={active}
                data-testid={`alt-option-${alternative.exerciseId}`}
                className={`grid min-h-[72px] w-full grid-cols-[28px_1fr] items-center gap-3 border-b border-line px-1.5 text-left ${
                  active ? "bg-hl" : ""
                }`}
              >
                <ExerciseFigure primary={alternative.primary} secondary={alternative.secondary} height={64} />
                <span className="grid gap-0.5">
                  <b className="text-[1.02rem] leading-tight font-bold">{alternative.name}</b>
                  <span className="text-[0.78rem] text-mut">{REGION_NAMES[alternative.primary[0] as RegionId] ?? ""}</span>
                </span>
              </button>
            );
          })}
        </div>
      )}
      <ErrorLine error={error} />
      {alternatives.length > 0 && (
        <PennantButton onClick={handleUse} disabled={busy || !picked} testId="use-alternative">
          Use this
        </PennantButton>
      )}
    </div>
  );
}
