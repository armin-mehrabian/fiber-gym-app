import { describe, expect, it } from "vitest";
import { contributionLine, formatSets, pickGaps, setsOfTarget, setsSlashTarget } from "./coverage";
import type { Region, RegionCoverage } from "./types";

// A region's week with only the fields a test cares about.
function week(regionId: string, effective: number, target: number): RegionCoverage {
  const coverage = target > 0 ? Math.min(1, effective / target) : 0;
  return { regionId, effective, target, coverage, recovering: false, contributions: [] };
}

function region(id: string, view: Region["view"], order: number): Region {
  return { id, name: id, group: "test", view, order };
}

describe("formatSets", () => {
  it("drops a trailing .0 and keeps one decimal", () => {
    expect(formatSets(3)).toBe("3");
    expect(formatSets(4.5)).toBe("4.5");
    expect(formatSets(0)).toBe("0");
    expect(formatSets(2.9999999)).toBe("3");
  });
});

describe("info panel text", () => {
  it("writes sets against the target", () => {
    expect(setsOfTarget(week("delt_rear", 4.5, 6))).toBe("4.5 of 6");
    expect(setsOfTarget(week("delt_rear", 6, 6))).toBe("6 of 6");
    expect(setsSlashTarget(week("delt_rear", 1.5, 4))).toBe("1.5/4");
  });

  it("writes one contribution per line", () => {
    expect(contributionLine({ exerciseName: "Face pull", day: "Tue", sets: 3 })).toBe("Face pull · Tue · 3");
    expect(contributionLine({ exerciseName: "Row", day: "Wed", sets: 1.5 })).toBe("Row · Wed · 1.5");
  });
});

describe("pickGaps", () => {
  const catalog = [
    region("chest_upper", "front", 1),
    region("delt_side", "both", 2),
    region("lats_upper", "back", 3),
    region("abs_upper", "front", 4),
    region("calves", "both", 5),
  ];

  it("keeps only the current view, plus regions drawn on both sides", () => {
    const coverage = [week("chest_upper", 0, 6), week("delt_side", 0, 6), week("lats_upper", 0, 6)];
    expect(pickGaps(coverage, catalog, "front").map((r) => r.regionId)).toEqual(["chest_upper", "delt_side"]);
    expect(pickGaps(coverage, catalog, "back").map((r) => r.regionId)).toEqual(["delt_side", "lats_upper"]);
  });

  it("sorts by lowest coverage and keeps region order on ties", () => {
    const coverage = [
      week("chest_upper", 3, 6), // 0.5
      week("delt_side", 0, 6), // 0
      week("abs_upper", 0, 4), // 0
      week("calves", 6, 6), // 1
    ];
    expect(pickGaps(coverage, catalog, "front").map((r) => r.regionId)).toEqual([
      "delt_side",
      "abs_upper",
      "chest_upper",
      "calves",
    ]);
  });

  it("skips regions with a target of 0", () => {
    const coverage = [week("chest_upper", 0, 0), week("abs_upper", 1, 4)];
    expect(pickGaps(coverage, catalog, "front").map((r) => r.regionId)).toEqual(["abs_upper"]);
  });

  it("returns at most the requested count without changing the input", () => {
    const coverage = catalog.map((r) => week(r.id, 0, 6));
    expect(pickGaps(coverage, catalog, "front", 2)).toHaveLength(2);
    expect(coverage.map((r) => r.regionId)).toEqual(catalog.map((r) => r.id));
  });
});
