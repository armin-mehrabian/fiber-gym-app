import type { Metadata } from "next";
import { redirect } from "next/navigation";
import BackLink from "@/components/BackLink";
import { getActiveSession, getAlternatives } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import { alternativesNotInWorkout } from "@/lib/plan";
import SwapPicker from "./SwapPicker";

export const metadata: Metadata = { title: "Swap · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type SwapPageProps = {
  params: Promise<{ position: string }>;
};

export default async function SwapPage({ params }: SwapPageProps) {
  await guardPage("/");
  const { position } = await params;

  const session = await getActiveSession();
  if (!session) {
    redirect("/");
  }
  const exercise = session.exercises.find((item) => String(item.position) === position);
  // A stale link, or sets were logged since (the backend only swaps exercises without sets).
  if (!exercise || exercise.sets.length > 0) {
    redirect("/workout");
  }
  const options = await getAlternatives(exercise.exerciseId, session.splitDayId);
  const alternatives = alternativesNotInWorkout(options, session.exercises);

  return (
    <div className="grid gap-4">
      <header className="flex min-h-11 items-center gap-2">
        <BackLink href="/workout" label="Back to workout" />
        <div className="grid min-w-0 gap-0.5">
          <h1 className="font-display text-[1.4rem] leading-none">Swap</h1>
          <span className="truncate text-[0.8rem] text-mut">{exercise.name}</span>
        </div>
      </header>
      {/* Keyed by the exercise: Next.js keeps visited pages alive, and after a swap the picker
          should start fresh instead of keeping the old preview. */}
      <SwapPicker
        key={`${session.id}-${exercise.exerciseId}`}
        sessionId={session.id}
        position={exercise.position}
        exercises={session.exercises}
        alternatives={alternatives}
      />
    </div>
  );
}
