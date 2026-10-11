import type { Metadata } from "next";
import { redirect } from "next/navigation";
import BackLink from "@/components/BackLink";
import { viewOf } from "@/components/figure/exercise";
import { getActiveSession, getWeekCoverage } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import { planFromExercises, toRegionValues } from "@/lib/plan";
import { hasCompletedSet } from "@/lib/workout";
import ExerciseRow from "./ExerciseRow";
import Timer from "./Timer";
import WorkoutActions from "./WorkoutActions";
import WorkoutFigure from "./WorkoutFigure";

export const metadata: Metadata = { title: "Workout · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function WorkoutPage() {
  await guardPage("/");
  const [session, coverage] = await Promise.all([getActiveSession(), getWeekCoverage()]);
  // Nothing in progress (finished, discarded, or never started): Home offers START.
  if (!session) {
    redirect("/");
  }
  const week = toRegionValues(Object.fromEntries(coverage.regions.map((region) => [region.regionId, region.coverage])));
  const firstMainRegion = session.exercises[0]?.primary[0];

  return (
    <div className="grid gap-5">
      <header className="flex min-h-11 items-center gap-2">
        <BackLink href="/" label="Home" />
        <h1 data-testid="workout-name" className="min-w-0 flex-1 truncate font-display text-[1.6rem] leading-none">
          {session.name}
        </h1>
        <Timer startedAt={session.startedAt} />
      </header>

      <WorkoutFigure
        week={week}
        plan={planFromExercises(session.exercises)}
        firstView={firstMainRegion ? viewOf(firstMainRegion) : "front"}
      />

      {/* Varsity double-rule stripe. */}
      <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />

      {session.exercises.length > 0 && (
        <div className="grid">
          {session.exercises.map((exercise) => (
            <ExerciseRow key={exercise.position} exercise={exercise} />
          ))}
        </div>
      )}

      {/* Keyed by the workout: Next.js keeps visited pages alive, and a new workout
          should never inherit an open question or a "busy" state from the last one. */}
      <WorkoutActions key={session.id} sessionId={session.id} hasCompletedSet={hasCompletedSet(session)} />
    </div>
  );
}
