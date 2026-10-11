import { describe, expect, it } from "vitest";
import type { SessionExercise } from "./types";
import { lastTimeText, setText, setsText, suggestedDisplayWeight, suggestionText } from "./progress";

const exercise = (changes: Partial<SessionExercise>): SessionExercise => ({
  position: 1,
  exerciseId: "cable_curl",
  name: "Cable curl",
  primary: ["biceps_short"],
  secondary: [],
  targetSets: 3,
  repRange: [10, 12],
  suggestedWeightKg: null,
  suggestedReps: null,
  suggestionReason: null,
  lastTime: null,
  sets: [],
  ...changes,
});

const lastTime = (weights: number[], reps: number[]) => ({
  date: "2026-10-01",
  sets: weights.map((weightKg, i) => ({ weightKg, reps: reps[i] })),
});

describe("suggestedDisplayWeight", () => {
  it("keeps kg as they are", () => {
    expect(suggestedDisplayWeight(63.73, "KG")).toBe(63.73);
  });

  it("rounds lb to the nearest 5 lb", () => {
    expect(suggestedDisplayWeight(63.73, "LB")).toBe(140); // 140.5 lb
    expect(suggestedDisplayWeight(61.23, "LB")).toBe(135); // 134.99 lb
    expect(suggestedDisplayWeight(0, "LB")).toBe(0);
  });
});

describe("setText", () => {
  it("writes one set in the user's units", () => {
    expect(setText({ weightKg: 57.5, reps: 8 }, "KG")).toBe("57.5 × 8");
    expect(setText({ weightKg: 20.41, reps: 10 }, "LB")).toBe("45 × 10");
    expect(setText({ weightKg: 0, reps: 12 }, "KG")).toBe("0 × 12");
  });
});

describe("setsText", () => {
  it("writes the weight only when it changes", () => {
    const { sets } = lastTime([20, 20, 22.5], [12, 12, 10]);
    expect(setsText(sets, "KG")).toBe("20 × 12 · 12 · 22.5 × 10");
  });

  it("shows warm-ups with a W", () => {
    const sets = [
      { weightKg: 20, reps: 10, isWarmup: true },
      { weightKg: 40, reps: 8, isWarmup: false },
      { weightKg: 40, reps: 8, isWarmup: false },
    ];
    expect(setsText(sets, "KG")).toBe("W 20 × 10 · 40 × 8 · 8");
  });

  it("converts to lb", () => {
    expect(setsText([{ weightKg: 20.41, reps: 10 }], "LB")).toBe("45 × 10");
  });
});

describe("lastTimeText", () => {
  it("is null without a last time or without sets", () => {
    expect(lastTimeText(null, "KG")).toBeNull();
    expect(lastTimeText({ date: "2026-10-01", sets: [] }, "KG")).toBeNull();
  });

  it("lists last time's sets", () => {
    expect(lastTimeText(lastTime([20, 20, 20], [12, 12, 12]), "KG")).toBe("Last: 20 × 12 · 12 · 12");
  });
});

describe("suggestionText", () => {
  it("shows the step up when every set hit the top of the range", () => {
    const ex = exercise({
      suggestionReason: "INCREASE",
      suggestedWeightKg: 22.5,
      lastTime: lastTime([20, 20, 20], [12, 12, 12]),
    });
    expect(suggestionText(ex, "KG")).toBe("+2.5 kg · all sets hit 12");
  });

  it("shows the step up in lb as the rounded weight the set row starts at", () => {
    // 61.23 kg = 135 lb last time; 63.73 kg = 140.5 lb, shown as 140 lb.
    const ex = exercise({
      suggestionReason: "INCREASE",
      suggestedWeightKg: 63.73,
      lastTime: lastTime([61.23], [12]),
    });
    expect(suggestionText(ex, "LB")).toBe("+5 lb · all sets hit 12");
  });

  it("still says why when last time is missing", () => {
    const ex = exercise({ suggestionReason: "INCREASE", suggestedWeightKg: 22.5 });
    expect(suggestionText(ex, "KG")).toBe("More weight · all sets hit 12");
  });

  it("has one line for each other reason", () => {
    expect(suggestionText(exercise({ suggestionReason: "REPEAT" }), "KG")).toBe("Same weight · aim for one more rep");
    expect(suggestionText(exercise({ suggestionReason: "MORE_REPS" }), "KG")).toBe("Bodyweight · one more rep");
    expect(suggestionText(exercise({ suggestionReason: "NO_HISTORY" }), "KG")).toBe("First time · pick a weight");
  });

  it("is null without a reason", () => {
    expect(suggestionText(exercise({}), "KG")).toBeNull();
  });
});
