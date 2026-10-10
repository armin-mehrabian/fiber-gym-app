// One exercise of the workout in progress. Tapping it opens its set log.
import Link from "next/link";
import ExerciseFigure from "@/components/figure/ExerciseFigure";
import type { SessionExercise } from "@/lib/types";
import { workingSetsDone } from "@/lib/workout";

export default function ExerciseRow({ exercise }: { exercise: SessionExercise }) {
  const done = workingSetsDone(exercise);
  const finished = done >= exercise.targetSets;

  return (
    <Link
      href={`/workout/${exercise.position}`}
      data-testid={`exercise-row-${exercise.position}`}
      className="grid min-h-[76px] grid-cols-[2em_28px_1fr_auto] items-center gap-2.5 border-b border-line"
    >
      <span className="font-display text-[1.15rem] text-acc">{String(exercise.position).padStart(2, "0")}</span>
      <ExerciseFigure primary={exercise.primary} secondary={exercise.secondary} height={64} />
      <b className="min-w-0 text-[1.02rem] leading-tight font-bold">{exercise.name}</b>
      {/* Finished sets out of the target, gold once the target is reached. */}
      <span className={`font-display tabular-nums ${finished ? "text-acc" : "text-mut"}`}>
        {done}/{exercise.targetSets}
      </span>
    </Link>
  );
}
