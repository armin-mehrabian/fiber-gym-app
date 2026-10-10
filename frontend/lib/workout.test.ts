import { describe, expect, it } from "vitest";
import type { Session, SessionExercise, WorkoutSet } from "./types";
import {
  checkAction,
  hasCompletedSet,
  isEdited,
  nextSetValues,
  removeDraft,
  setLabels,
  setsKey,
  toDraft,
  workingSetsDone,
} from "./workout";

const set = (setNumber: number, changes: Partial<WorkoutSet> = {}): WorkoutSet => ({
  id: `s${setNumber}`,
  setNumber,
  weightKg: 60,
  reps: 8,
  isWarmup: false,
  completed: false,
  ...changes,
});

const exercise = (sets: WorkoutSet[]): SessionExercise => ({
  position: 1,
  exerciseId: "bench_press",
  name: "Bench press",
  primary: ["chest_mid"],
  secondary: [],
  targetSets: 3,
  repRange: [6, 10],
  suggestedWeightKg: null,
  sets,
});

const session = (exercises: SessionExercise[]): Session => ({
  id: "abc",
  name: "Push",
  status: "ACTIVE",
  startedAt: "2026-10-05T10:00:00Z",
  finishedAt: null,
  exercises,
});

describe("workingSetsDone", () => {
  it("counts completed sets that aren't warm-ups", () => {
    const sets = [set(1, { isWarmup: true, completed: true }), set(2, { completed: true }), set(3)];
    expect(workingSetsDone(exercise(sets))).toBe(1);
  });
});

describe("hasCompletedSet", () => {
  it("is false for an empty workout or one with only open sets", () => {
    expect(hasCompletedSet(session([]))).toBe(false);
    expect(hasCompletedSet(session([exercise([set(1)])]))).toBe(false);
  });

  it("is true once any set is done, warm-ups included", () => {
    expect(hasCompletedSet(session([exercise([set(1, { isWarmup: true, completed: true })])]))).toBe(true);
  });
});

describe("setLabels", () => {
  it("shows W for warm-ups and counts only working sets", () => {
    const sets = [set(1, { isWarmup: true }), set(2), set(3), set(4, { isWarmup: true }), set(5)];
    expect(setLabels(sets)).toEqual(["W", "1", "2", "W", "3"]);
  });
});

describe("drafts", () => {
  it("shows the weight in the user's units", () => {
    expect(toDraft(set(1, { weightKg: 20.41 }), "LB").weight).toBe(45);
    expect(toDraft(set(1, { weightKg: 22.5 }), "KG").weight).toBe(22.5);
  });

  it("is edited only when the values on screen differ from the saved ones", () => {
    const draft = toDraft(set(1, { weightKg: 24.95 }), "LB"); // shown as 55.01 lb
    expect(isEdited(draft, "LB")).toBe(false);
    expect(isEdited({ ...draft, reps: 9 }, "LB")).toBe(true);
    expect(isEdited({ ...draft, weight: 60.01 }, "LB")).toBe(true);
  });
});

describe("checkAction", () => {
  it("saves an open set", () => {
    expect(checkAction(toDraft(set(1), "KG"), "KG")).toBe("save");
  });

  it("undoes a done set that wasn't edited", () => {
    expect(checkAction(toDraft(set(1, { completed: true }), "KG"), "KG")).toBe("undo");
  });

  it("saves again when a done set was edited", () => {
    const draft = { ...toDraft(set(1, { completed: true }), "KG"), weight: 62.5 };
    expect(checkAction(draft, "KG")).toBe("save");
  });
});

describe("removeDraft", () => {
  it("removes the set and renumbers the rest from 1", () => {
    const drafts = [set(1), set(2), set(3)].map((s) => toDraft(s, "KG"));
    const left = removeDraft(drafts, "s1");
    expect(left.map((d) => [d.set.id, d.set.setNumber])).toEqual([
      ["s2", 1],
      ["s3", 2],
    ]);
  });
});

describe("nextSetValues", () => {
  it("starts with an empty bar and the top of the rep range", () => {
    expect(nextSetValues([], "KG", false, [6, 10])).toEqual({ weightKg: 20, reps: 10, isWarmup: false });
    expect(nextSetValues([], "LB", false, [6, 10])).toEqual({ weightKg: 20.41, reps: 10, isWarmup: false });
  });

  it("starts at 0 for bodyweight exercises", () => {
    expect(nextSetValues([], "KG", true, [8, 15]).weightKg).toBe(0);
  });

  it("copies the last row's values on screen, but not the warm-up flag", () => {
    const last = { ...toDraft(set(1, { isWarmup: true }), "KG"), weight: 62.5, reps: 6 };
    expect(nextSetValues([toDraft(set(1), "KG"), last], "KG", false, [6, 10])).toEqual({
      weightKg: 62.5,
      reps: 6,
      isWarmup: false,
    });
  });
});

describe("setsKey", () => {
  it("changes when a saved value changes", () => {
    const before = setsKey([set(1)]);
    expect(setsKey([set(1)])).toBe(before);
    expect(setsKey([set(1, { completed: true })])).not.toBe(before);
    expect(setsKey([set(1), set(2)])).not.toBe(before);
  });
});
