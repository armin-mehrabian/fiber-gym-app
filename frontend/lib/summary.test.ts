import { describe, expect, it } from "vitest";
import { personalBestText, summaryTitle, weekChangeText } from "./summary";

describe("summary lines", () => {
  it("writes the title", () => {
    expect(summaryTitle("Pull")).toBe("Pull · Done");
  });

  it("writes the week change", () => {
    expect(weekChangeText(41, 58)).toBe("Week 41% → 58%");
    expect(weekChangeText(0, 0)).toBe("Week 0% → 0%");
  });

  it("writes a personal best in the user's units", () => {
    const best = { exerciseId: "bench_press", name: "Bench press", weightKg: 82.5, reps: 6 };
    expect(personalBestText(best, "KG")).toBe("New best · Bench press · 82.5 × 6");
    expect(personalBestText({ ...best, weightKg: 20.41 }, "LB")).toBe("New best · Bench press · 45 × 6");
  });

  it("shows 0 for a bodyweight best", () => {
    const best = { exerciseId: "pull_up", name: "Pull-up", weightKg: 0, reps: 12 };
    expect(personalBestText(best, "KG")).toBe("New best · Pull-up · 0 × 12");
  });
});
