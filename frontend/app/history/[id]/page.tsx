import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import BackLink from "@/components/BackLink";
import { getSession, getSettings } from "@/lib/api";
import { formatSessionDate } from "@/lib/dates";
import { guardPage } from "@/lib/guard";
import { formatWeight, toDisplayWeight } from "@/lib/units";
import type { Units, WorkoutSet } from "@/lib/types";

export const metadata: Metadata = { title: "Workout · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type SessionDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function SessionDetailPage({ params }: SessionDetailPageProps) {
  await guardPage("/");
  const { id } = await params;
  const [session, settings] = await Promise.all([getSession(id), getSettings()]);
  if (!session) {
    notFound();
  }
  // A workout still in progress is edited on the workout screen, not shown here.
  if (session.status === "ACTIVE") {
    redirect("/workout");
  }

  return (
    <div className="grid gap-5">
      <header className="flex min-h-11 items-center gap-2">
        <BackLink href="/history" label="Back to history" />
        <span className="flex-1 text-[0.85rem] text-mut">{formatSessionDate(session.startedAt, settings.timezone)}</span>
        <span className="text-[0.85rem] text-mut">{settings.units}</span>
      </header>
      <h1 className="font-display text-[2.2rem] leading-none">{session.name}</h1>
      {/* Varsity double-rule stripe. */}
      <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
      <div className="grid">
        {session.exercises.map((exercise) => (
          <section
            key={exercise.position}
            data-testid={`session-detail-exercise-${exercise.position}`}
            className="grid gap-2 border-b border-line py-3"
          >
            <b className="text-[1.02rem] font-bold">{exercise.name}</b>
            <div className="flex flex-wrap gap-x-4 gap-y-1">
              {exercise.sets.map((set) => (
                <SetText key={set.id} set={set} position={exercise.position} units={settings.units} />
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

// "60 × 8", with a W in front of warm-ups. Sets that were never ticked off are dimmed and struck through.
function SetText({ set, position, units }: { set: WorkoutSet; position: number; units: Units }) {
  const weight = formatWeight(toDisplayWeight(set.weightKg, units));
  return (
    <span
      data-testid={`session-detail-set-${position}-${set.setNumber}`}
      className={`tabular-nums ${set.completed ? "" : "text-mut line-through"}`}
    >
      {set.isWarmup && <span className="text-mut">W </span>}
      {weight} × {set.reps}
    </span>
  );
}
