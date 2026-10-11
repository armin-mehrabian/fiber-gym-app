// Today's plan for the body figure: which regions a set of exercises works.
// 1 = a main (primary) region of any exercise, 0.5 = only a helping (secondary) region.
// The backend sends the same numbers for a recommendation; this local copy lets the workout
// and swap screens draw the plan instantly from exercise data they already have.
import type { RegionValues } from "@/components/figure/BodyFigure";
import { exerciseValues, isRegionId } from "@/components/figure/exercise";

// The only exercise fields the plan needs.
export type RegionLists = {
  primary: string[];
  secondary: string[];
};

// API maps use plain strings as keys; the figure only knows its own region ids, so unknown ones are dropped.
export function toRegionValues(record: Record<string, number>): RegionValues {
  const values: RegionValues = {};
  for (const [id, value] of Object.entries(record)) {
    if (isRegionId(id)) {
      values[id] = value;
    }
  }
  return values;
}

// Each exercise's own values (1 main, 0.5 helping), combined: a region keeps its highest value,
// so a main region of one exercise beats a helping region of another.
export function planFromExercises(exercises: RegionLists[]): RegionValues {
  const plan: RegionValues = {};
  for (const exercise of exercises) {
    for (const [id, value] of Object.entries(exerciseValues(exercise.primary, exercise.secondary))) {
      if (isRegionId(id)) {
        plan[id] = Math.max(plan[id] ?? 0, value);
      }
    }
  }
  return plan;
}

// The plan with the exercise at `position` replaced by `replacement` (null = no change yet).
export function swapPreviewPlan<T extends RegionLists & { position: number }>(
  exercises: T[],
  position: number,
  replacement: RegionLists | null,
): RegionValues {
  const swapped = exercises.map((exercise) =>
    exercise.position === position && replacement ? replacement : exercise,
  );
  return planFromExercises(swapped);
}

// Swap options without exercises the workout already has (at any position), so a swap never
// puts the same exercise in twice.
export function alternativesNotInWorkout<T extends { exerciseId: string }>(
  alternatives: T[],
  workout: { exerciseId: string }[],
): T[] {
  const inWorkout = new Set(workout.map((exercise) => exercise.exerciseId));
  return alternatives.filter((alternative) => !inWorkout.has(alternative.exerciseId));
}
