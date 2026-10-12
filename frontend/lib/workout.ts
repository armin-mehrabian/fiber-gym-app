// Pure helpers for the workout and set-logging screens (no React, easy to test).
import { suggestedDisplayWeight } from "@/lib/progress";
import { firstSetWeight, formatWeight, toDisplayWeight, toKg } from "@/lib/units";
import type { CompleteValues, Session, SessionExercise, SetValues, Units, WorkoutSet } from "@/lib/types";

// Finished working sets of an exercise (warm-ups don't count).
function workingSetsDone(exercise: SessionExercise): number {
  return exercise.sets.filter((set) => set.completed && !set.isWarmup).length;
}

// A workout row is done once its finished working sets reach the target.
export function isExerciseDone(exercise: SessionExercise): boolean {
  return workingSetsDone(exercise) >= exercise.targetSets;
}

// Finishing a workout with nothing ticked off is probably a mistake, so the screen asks first.
export function hasCompletedSet(session: Session): boolean {
  return session.exercises.some((exercise) => exercise.sets.some((set) => set.completed));
}

// What the first column of each set shows: "W" for warm-ups, otherwise the working-set count.
// So 1 warm-up + 3 sets reads W, 1, 2, 3 (like a gym log), not 1, 2, 3, 4.
export function setLabels(sets: WorkoutSet[]): string[] {
  let working = 0;
  return sets.map((set) => {
    if (set.isWarmup) {
      return "W";
    }
    working += 1;
    return String(working);
  });
}

// One set on the log screen: the saved set plus the values on screen, which the steppers
// change locally until ✓ saves them. weight is in the user's units.
export type SetDraft = {
  set: WorkoutSet;
  weight: number;
  reps: number;
};

export function toDraft(set: WorkoutSet, units: Units): SetDraft {
  return { set, weight: toDisplayWeight(set.weightKg, units), reps: set.reps };
}

// True when the values on screen differ from what is saved.
export function isEdited(draft: SetDraft, units: Units): boolean {
  return toKg(draft.weight, units) !== draft.set.weightKg || draft.reps !== draft.set.reps;
}

// What ✓ does: "save" stores the values and marks the set done; "undo" un-marks a done set.
// A done set whose values were edited saves again instead of undoing.
export function checkAction(draft: SetDraft, units: Units): "save" | "undo" {
  return draft.set.completed && !isEdited(draft, units) ? "undo" : "save";
}

// The backend renumbers the remaining sets after a delete; this mirrors that on screen.
export function removeDraft(drafts: SetDraft[], setId: string): SetDraft[] {
  return drafts
    .filter((draft) => draft.set.id !== setId)
    .map((draft, index) => ({ ...draft, set: { ...draft.set, setNumber: index + 1 } }));
}

// Where the first set of an exercise starts, in the user's units: the backend's suggestion when
// there is one, otherwise an empty bar (or bodyweight) with reps at the top of the range.
export function firstSetStart(
  exercise: SessionExercise,
  units: Units,
  bodyweight: boolean,
): { weight: number; reps: number } {
  const weight =
    exercise.suggestedWeightKg !== null
      ? suggestedDisplayWeight(exercise.suggestedWeightKg, units)
      : firstSetWeight(units, bodyweight);
  const reps = exercise.suggestedReps ?? exercise.repRange[1];
  return { weight, reps };
}

// What one tap on ✓ DONE logs, with the weight in the user's units.
export type PlannedValues = {
  weight: number;
  reps: number;
  sets: number;
};

// The row's plan: the same start values as the set log (suggestion, else bodyweight 0 or an
// empty bar, reps at the top of the range) and the exercise's target number of sets.
export function plannedValues(exercise: SessionExercise, units: Units, bodyweight: boolean): PlannedValues {
  return { ...firstSetStart(exercise, units, bodyweight), sets: exercise.targetSets };
}

// The plan as one line: "3 × 10 · 60 kg", or "3 × 12 · Bodyweight" when there's no weight.
export function planText(values: PlannedValues, units: Units): string {
  const load = values.weight === 0 ? "Bodyweight" : `${formatWeight(values.weight)} ${units.toLowerCase()}`;
  return `${values.sets} × ${values.reps} · ${load}`;
}

// The request body for the one-tap log: the same values, with the weight in kg for the API.
export function toCompleteValues(values: PlannedValues, units: Units): CompleteValues {
  return { weightKg: toKg(values.weight, units), reps: values.reps, sets: values.sets };
}

// Values for a new set: a copy of the last row's on-screen values, or `start` for the first set.
// isWarmup is never copied: sets added after a warm-up are usually working sets.
export function nextSetValues(
  drafts: SetDraft[],
  units: Units,
  start: { weight: number; reps: number },
): SetValues {
  const last = drafts[drafts.length - 1];
  const weight = last ? last.weight : start.weight;
  const reps = last ? last.reps : start.reps;
  return { weightKg: toKg(weight, units), reps, isWarmup: false };
}

// A short text that changes whenever the saved sets change. The log screen uses it as a React key,
// so it starts fresh from the server whenever the data behind it changed.
export function setsKey(sets: WorkoutSet[]): string {
  return sets
    .map((set) => `${set.id}:${set.weightKg}:${set.reps}:${set.isWarmup ? "w" : ""}:${set.completed ? "c" : ""}`)
    .join("|");
}
