// Helpers for the weekly targets screen.
import type { Region, Target } from "@/lib/types";

export const MIN_WEEKLY_SETS = 0;
export const MAX_WEEKLY_SETS = 30;

// regionId → weekly sets, so a value can be looked up and changed by region.
export type TargetValues = Record<string, number>;

export function toTargetValues(targets: Target[]): TargetValues {
  return Object.fromEntries(targets.map((target) => [target.regionId, target.weeklySets]));
}

// Only the rows that differ from what's saved, so one Save sends just the real edits.
export function changedTargets(saved: TargetValues, edited: TargetValues): Target[] {
  return Object.keys(edited)
    .filter((regionId) => edited[regionId] !== saved[regionId])
    .map((regionId) => ({ regionId, weeklySets: edited[regionId] }));
}

export type RegionGroup = { group: string; regions: Region[] };

// Groups regions by muscle group, keeping the order they arrive in (the region order from the API).
export function groupRegions(regions: Region[]): RegionGroup[] {
  const groups: RegionGroup[] = [];
  for (const region of regions) {
    const existing = groups.find((entry) => entry.group === region.group);
    if (existing) {
      existing.regions.push(region);
    } else {
      groups.push({ group: region.group, regions: [region] });
    }
  }
  return groups;
}
