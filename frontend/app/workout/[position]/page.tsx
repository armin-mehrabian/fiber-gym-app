import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import BackLink from "@/components/BackLink";
import ExerciseFigure from "@/components/figure/ExerciseFigure";
import { getActiveSession, getExercise, getSettings } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import { lastTimeText, suggestionText } from "@/lib/progress";
import { firstSetStart, setsKey } from "@/lib/workout";
import SetLogger from "./SetLogger";

export const metadata: Metadata = { title: "Log sets · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type LogPageProps = {
  params: Promise<{ position: string }>;
};

export default async function LogPage({ params }: LogPageProps) {
  await guardPage("/");
  const { position } = await params;

  const [session, settings] = await Promise.all([getActiveSession(), getSettings()]);
  if (!session) {
    redirect("/");
  }
  const exercise = session.exercises.find((item) => String(item.position) === position);
  // A stale link (e.g. the exercise was removed): back to the list.
  if (!exercise) {
    redirect("/workout");
  }
  // Workout rows don't say which equipment an exercise needs; the catalog does.
  // No equipment = bodyweight, so its first set starts at 0. Unknown: assume a bar.
  // Only a nicer default, so a failed request is not worth an error page.
  const catalogExercise = await getExercise(exercise.exerciseId).catch(() => null);
  const bodyweight = catalogExercise !== null && catalogExercise.equipment.length === 0;
  const [repLow, repHigh] = exercise.repRange;
  const lastTime = lastTimeText(exercise.lastTime, settings.units);
  const suggestion = suggestionText(exercise, settings.units);

  return (
    <div className="grid gap-4">
      <header className="flex min-h-11 items-center gap-2">
        <BackLink href="/workout" label="Back to workout" />
        {/* The position lets the exercise page link straight back here. */}
        <Link
          href={`/exercise/${encodeURIComponent(exercise.exerciseId)}?position=${exercise.position}`}
          aria-label="Exercise details"
          data-testid="exercise-info"
          className="ml-auto grid size-11 place-items-center"
        >
          <span className="grid size-7 place-items-center rounded-full border-[1.5px] border-current font-display text-[0.9rem] normal-case">
            i
          </span>
        </Link>
      </header>
      <div className="grid grid-cols-[34px_1fr] items-center gap-3">
        <ExerciseFigure primary={exercise.primary} secondary={exercise.secondary} height={92} />
        <div className="grid gap-1.5">
          <h1 className="font-display text-[1.55rem] leading-[0.95]">{exercise.name}</h1>
          <span className="text-[0.8rem] tabular-nums text-mut">
            {repLow}–{repHigh} reps
          </span>
          {lastTime && (
            <span data-testid="last-time" className="text-[0.8rem] tabular-nums text-mut">
              {lastTime}
            </span>
          )}
          {suggestion && (
            <span data-testid="suggestion" className="text-[0.8rem] font-semibold tracking-[0.08em] text-acc">
              {suggestion}
            </span>
          )}
        </div>
      </div>
      {/* Next.js keeps visited pages alive. Keyed by the saved sets, so the logger starts fresh
          from the server whenever they changed (or another exercise moved into this position). */}
      <SetLogger
        key={`${exercise.exerciseId}-${setsKey(exercise.sets)}`}
        sessionId={session.id}
        exercise={exercise}
        units={settings.units}
        firstSet={firstSetStart(exercise, settings.units, bodyweight)}
      />
    </div>
  );
}
