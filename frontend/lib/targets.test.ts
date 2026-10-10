import { describe, expect, it } from "vitest";
import { changedTargets, groupRegions, toTargetValues } from "./targets";
import type { Region } from "./types";

describe("toTargetValues", () => {
  it("maps region id to weekly sets", () => {
    expect(
      toTargetValues([
        { regionId: "chest_upper", weeklySets: 6 },
        { regionId: "calves", weeklySets: 4 },
      ]),
    ).toEqual({ chest_upper: 6, calves: 4 });
  });
});

describe("changedTargets", () => {
  const saved = { chest_upper: 6, chest_mid: 6, calves: 4 };

  it("returns nothing when nothing changed", () => {
    expect(changedTargets(saved, { ...saved })).toEqual([]);
  });

  it("returns only the edited rows, with their new values", () => {
    const edited = { chest_upper: 8, chest_mid: 6, calves: 0 };
    expect(changedTargets(saved, edited)).toEqual([
      { regionId: "chest_upper", weeklySets: 8 },
      { regionId: "calves", weeklySets: 0 },
    ]);
  });

  it("treats a value changed and changed back as unchanged", () => {
    expect(changedTargets(saved, { ...saved, calves: 4 })).toEqual([]);
  });
});

describe("groupRegions", () => {
  const region = (id: string, group: string, order: number): Region => ({
    id,
    name: id,
    group,
    view: "front",
    order,
  });

  it("groups by muscle group in the order the regions arrive", () => {
    const regions = [
      region("chest_upper", "chest", 10),
      region("chest_mid", "chest", 11),
      region("delt_front", "shoulders", 20),
      region("calves", "legs", 70),
    ];
    expect(groupRegions(regions).map(({ group, regions: list }) => [group, list.map((r) => r.id)])).toEqual([
      ["chest", ["chest_upper", "chest_mid"]],
      ["shoulders", ["delt_front"]],
      ["legs", ["calves"]],
    ]);
  });

  it("returns no groups for no regions", () => {
    expect(groupRegions([])).toEqual([]);
  });
});
