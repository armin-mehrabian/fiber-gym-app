import { describe, expect, it } from "vitest";
import { badgeHint, contributionLine, fillsLine, pickStillDark, regionStateLabel } from "./coverage";
import type { Region, RegionCoverage, RegionState } from "./types";

// A region's week with only the fields a test cares about.
function week(regionId: string, state: RegionState): RegionCoverage {
  return { regionId, effective: 0, target: 6, coverage: 0, state, recovering: false, contributions: [] };
}

function region(id: string, view: Region["view"], order: number, name = id): Region {
  return { id, name, group: "test", view, order };
}

describe("regionStateLabel", () => {
  it("writes each state in words", () => {
    expect(regionStateLabel("DONE")).toBe("Done ✓");
    expect(regionStateLabel("STARTED")).toBe("Started");
    expect(regionStateLabel("NOT_YET")).toBe("Not yet");
  });
});

describe("contributionLine", () => {
  it("names the exercise and day, without the sets", () => {
    expect(contributionLine({ exerciseName: "Face pull", day: "Tue", sets: 3 })).toBe("Face pull · Tue");
    expect(contributionLine({ exerciseName: "Row", day: "Wed", sets: 1.5 })).toBe("Row · Wed");
  });
});

describe("badgeHint", () => {
  it("writes the parts done", () => {
    const badge = { group: "chest", name: "Chest", done: false, regionsDone: 2, regionsTotal: 3 };
    expect(badgeHint(badge)).toBe("2/3 parts");
  });
});

describe("pickStillDark", () => {
  const catalog = [
    region("chest_upper", "front", 1),
    region("delt_side", "both", 2),
    region("lats_upper", "back", 3),
    region("abs_upper", "front", 4),
    region("calves", "both", 5),
  ];

  it("keeps only the current view, plus regions drawn on both sides", () => {
    const coverage = [week("chest_upper", "NOT_YET"), week("delt_side", "NOT_YET"), week("lats_upper", "NOT_YET")];
    expect(pickStillDark(coverage, catalog, "front").map((r) => r.regionId)).toEqual(["chest_upper", "delt_side"]);
    expect(pickStillDark(coverage, catalog, "back").map((r) => r.regionId)).toEqual(["delt_side", "lats_upper"]);
  });

  it("skips started and done regions and keeps region order", () => {
    const coverage = [
      week("chest_upper", "STARTED"),
      week("delt_side", "NOT_YET"),
      week("abs_upper", "DONE"),
      week("calves", "NOT_YET"),
    ];
    expect(pickStillDark(coverage, catalog, "front").map((r) => r.regionId)).toEqual(["delt_side", "calves"]);
  });

  it("is empty when nothing on that side is dark", () => {
    const coverage = [week("chest_upper", "DONE"), week("lats_upper", "NOT_YET")];
    expect(pickStillDark(coverage, catalog, "front")).toEqual([]);
  });

  it("returns at most the requested count", () => {
    const coverage = catalog.map((r) => week(r.id, "NOT_YET"));
    expect(pickStillDark(coverage, catalog, "front", 2).map((r) => r.regionId)).toEqual(["chest_upper", "delt_side"]);
  });
});

describe("fillsLine", () => {
  // Deliberately not in region order, to show the line sorts by it.
  const catalog = [
    region("lats_lower", "back", 33, "Lower lats"),
    region("delt_rear", "back", 22, "Rear delts"),
    region("traps_mid", "back", 31, "Mid/lower traps"),
    region("biceps_long", "front", 40, "Biceps, long head"),
  ];

  it("names up to 3 main regions in region order", () => {
    const planned = { lats_lower: 1, delt_rear: 1, biceps_long: 1, traps_mid: 1 };
    expect(fillsLine(planned, catalog)).toBe("Fills Rear delts · Mid/lower traps · Lower lats");
  });

  it("keeps names that contain a comma readable", () => {
    expect(fillsLine({ biceps_long: 1, lats_lower: 1 }, catalog)).toBe("Fills Lower lats · Biceps, long head");
  });

  it("leaves out helping regions", () => {
    expect(fillsLine({ lats_lower: 1, delt_rear: 0.5 }, catalog)).toBe("Fills Lower lats");
  });

  it("is null without a main region", () => {
    expect(fillsLine({ delt_rear: 0.5 }, catalog)).toBeNull();
    expect(fillsLine({}, catalog)).toBeNull();
  });
});
