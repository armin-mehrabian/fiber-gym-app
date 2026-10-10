import type { Metadata } from "next";
import { redirect } from "next/navigation";
import BackLink from "@/components/BackLink";
import { getActiveSession, getMyExercises } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import ExercisePicker from "./ExercisePicker";

export const metadata: Metadata = { title: "Add exercise · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type AddExercisePageProps = {
  searchParams: Promise<{ q?: string | string[] }>;
};

export default async function AddExercisePage({ searchParams }: AddExercisePageProps) {
  await guardPage("/");
  const { q } = await searchParams;
  // ?q=a&q=b gives an array; only the first one counts.
  const query = (Array.isArray(q) ? q[0] : q)?.trim() ?? "";

  const session = await getActiveSession();
  if (!session) {
    redirect("/");
  }
  const exercises = await getMyExercises(query);

  return (
    <div className="grid gap-4">
      <header className="flex min-h-11 items-center gap-2">
        <BackLink href="/workout" label="Back to workout" />
        <h1 className="font-display text-[1.6rem] leading-none">Add exercise</h1>
      </header>
      {/* Keyed by the exercise count: Next.js keeps visited pages alive, and after an exercise
          was added the picker should start fresh instead of staying "busy". */}
      <ExercisePicker
        key={`${session.id}-${session.exercises.length}`}
        sessionId={session.id}
        query={query}
        exercises={exercises}
      />
    </div>
  );
}
