// One exercise of the workout in progress. Tapping it opens its set log; the swap button
// (only while no set is logged) opens its alternatives.
import Link from "next/link";
import ExerciseFigure from "@/components/figure/ExerciseFigure";
import type { SessionExercise } from "@/lib/types";
import { workingSetsDone } from "@/lib/workout";

export default function ExerciseRow({ exercise }: { exercise: SessionExercise }) {
  const done = workingSetsDone(exercise);
  const finished = done >= exercise.targetSets;
  // The backend only allows a swap while the exercise has no sets.
  const canSwap = exercise.sets.length === 0;

  return (
    <div className="grid min-h-[76px] grid-cols-[1fr_44px] items-center gap-1 border-b border-line">
      <Link
        href={`/workout/${exercise.position}`}
        data-testid={`exercise-row-${exercise.position}`}
        className="grid min-h-[76px] grid-cols-[2em_28px_1fr_auto] items-center gap-2.5"
      >
        <span className="font-display text-[1.15rem] text-acc">{String(exercise.position).padStart(2, "0")}</span>
        <ExerciseFigure primary={exercise.primary} secondary={exercise.secondary} height={64} />
        <b className="min-w-0 text-[1.02rem] leading-tight font-bold">{exercise.name}</b>
        {/* Finished sets out of the target, gold once the target is reached. */}
        <span className={`font-display tabular-nums ${finished ? "text-acc" : "text-mut"}`}>
          {done}/{exercise.targetSets}
        </span>
      </Link>
      {/* The empty cell keeps the counts lined up on rows that can't be swapped. */}
      {canSwap ? (
        <Link
          href={`/workout/${exercise.position}/swap`}
          aria-label={`Swap ${exercise.name}`}
          data-testid={`swap-${exercise.position}`}
          className="grid size-11 place-items-center text-mut"
        >
          <SwapIcon />
        </Link>
      ) : (
        <span />
      )}
    </div>
  );
}

// Two arrows pointing opposite ways.
function SwapIcon() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M7 4 3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
