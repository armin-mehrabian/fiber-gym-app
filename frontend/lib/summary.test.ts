import { describe, expect, it } from "vitest";
import { litLine, personalBestText, summaryTitle } from "./summary";

describe("summary lines", () => {
  it("writes the title", () => {
    expect(summaryTitle("Pull")).toBe("Pull · Done");
  });

  it("names the regions that lit up, in the order given", () => {
    const names = new Map([
      ["chest_upper", "Upper chest"],
      ["delt_front", "Front delts"],
    ]);
    expect(litLine(["chest_upper", "delt_front"], names)).toBe("Lit up: Upper chest · Front delts");
    expect(litLine(["unknown_region"], names)).toBe("Lit up: unknown_region");
  });

  it("keeps the lights on when nothing new lit up", () => {
    expect(litLine([], new Map())).toBe("Kept the lights on");
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
