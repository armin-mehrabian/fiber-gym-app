"use client"; // typing and tapping happen in the browser

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import { REGION_NAMES, type RegionId } from "@/components/figure/shapes";
import { addExercise } from "@/lib/api";
import type { ApiError, Exercise } from "@/lib/types";

type ExercisePickerProps = {
  sessionId: string;
  query: string; // the search the list below was loaded for (from the URL)
  exercises: Exercise[];
};

// Wait this long after the last keystroke before searching, so typing "bench" is one search, not five.
const SEARCH_DELAY_MS = 250;

// The main region's name, e.g. "Mid chest" (or nothing if the id is unknown).
function mainRegionName(exercise: Exercise): string {
  return REGION_NAMES[exercise.primary[0] as RegionId] ?? "";
}

export default function ExercisePicker({ sessionId, query, exercises }: ExercisePickerProps) {
  const router = useRouter();
  const [text, setText] = useState(query);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  // The search lives in the URL (?q=), and the server loads the matching list.
  // Here we only update the URL once typing pauses.
  useEffect(() => {
    if (text.trim() === query) {
      return;
    }
    const timer = setTimeout(() => {
      const q = text.trim();
      router.replace(q ? `/workout/add?q=${encodeURIComponent(q)}` : "/workout/add");
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [text, query, router]);

  async function handlePick(exerciseId: string) {
    setBusy(true);
    setError(null);
    const result = await addExercise(sessionId, exerciseId);
    if (!result.ok) {
      setBusy(false);
      setError(result.error);
      return;
    }
    router.push("/workout");
  }

  return (
    <div className="grid gap-4">
      <input
        type="search"
        value={text}
        onChange={(event) => setText(event.target.value)}
        placeholder="Search"
        aria-label="Search exercises"
        data-testid="exercise-search"
        className="min-h-14 w-full border-b-[1.5px] border-edge bg-transparent px-0.5 uppercase tracking-[0.06em] placeholder:text-mut focus:border-acc focus:outline-none"
      />
      <ErrorLine error={error} />
      {exercises.length === 0 && <p className="text-sm tracking-[0.12em] text-mut">No matches</p>}
      <div className="grid">
        {exercises.map((exercise) => (
          <button
            key={exercise.id}
            type="button"
            onClick={() => handlePick(exercise.id)}
            disabled={busy}
            data-testid={`exercise-option-${exercise.id}`}
            className="grid min-h-[58px] w-full grid-cols-[1fr_auto] items-center gap-2.5 border-b border-line text-left disabled:opacity-40"
          >
            <b className="text-[1.02rem] font-bold">{exercise.name}</b>
            <span className="text-[0.8rem] text-mut">{mainRegionName(exercise)}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
