// One exercise of the workout in progress: its plan ("3 × 10 · 60 kg") and, until it's done,
// ✓ DONE / CHANGE underneath. Tapping the name opens its set log (to edit single sets);
// the swap button (only while no set is logged) opens its alternatives.
import Link from "next/link";
import ExerciseFigure from "@/components/figure/ExerciseFigure";
import type { SessionExercise, Units } from "@/lib/types";
import { isExerciseDone, planText, plannedValues } from "@/lib/workout";
import RowActions from "./RowActions";

type ExerciseRowProps = {
  sessionId: string;
  exercise: SessionExercise;
  units: Units;
  bodyweight: boolean; // needs no equipment, so with no suggestion it starts at 0 instead of an empty bar
};

export default function ExerciseRow({ sessionId, exercise, units, bodyweight }: ExerciseRowProps) {
  const done = isExerciseDone(exercise);
  const planned = plannedValues(exercise, units, bodyweight);
  // The backend only allows a swap while the exercise has no sets.
  const canSwap = exercise.sets.length === 0;

  return (
    <div className="grid gap-2 border-b border-line pb-3">
      <div className="grid min-h-[76px] grid-cols-[1fr_44px] items-center gap-1">
        <Link
          href={`/workout/${exercise.position}`}
          data-testid={`exercise-row-${exercise.position}`}
          data-done={String(done)}
          className="grid min-h-[76px] grid-cols-[2em_28px_1fr] items-center gap-2.5"
        >
          <span className="font-display text-[1.15rem] text-acc">{String(exercise.position).padStart(2, "0")}</span>
          <ExerciseFigure primary={exercise.primary} secondary={exercise.secondary} height={64} />
          <span className="grid min-w-0 gap-0.5">
            <b className="text-[1.02rem] leading-tight font-bold">{exercise.name}</b>
            <span className="text-[0.85rem] tabular-nums text-mut">{planText(planned, units)}</span>
          </span>
        </Link>
        {canSwap && (
          <Link
            href={`/workout/${exercise.position}/swap`}
            aria-label={`Swap ${exercise.name}`}
            data-testid={`swap-${exercise.position}`}
            className="grid size-11 place-items-center text-mut"
          >
            <SwapIcon />
          </Link>
        )}
        {done && (
          <span role="img" aria-label="Done" className="grid size-11 place-items-center font-display text-[1.4rem] text-acc">
            ✓
          </span>
        )}
      </div>
      {/* Keyed by the exercise: Next.js keeps visited pages alive, and an exercise that moves into
          this position (after a remove) should never inherit an open panel. */}
      {!done && (
        <RowActions
          key={exercise.exerciseId}
          sessionId={sessionId}
          position={exercise.position}
          name={exercise.name}
          planned={planned}
          units={units}
        />
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
