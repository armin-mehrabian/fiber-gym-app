// Text and picks for the weekly dashboard. The numbers themselves come from the backend;
// these helpers only choose what to show and how to write it.
import type { View } from "@/components/figure/shapes";
import type { Contribution, Region, RegionCoverage } from "@/lib/types";

// Effective sets with at most one decimal and no trailing ".0": 4.5 → "4.5", 3 → "3".
export function formatSets(value: number): string {
  return String(Math.round(value * 10) / 10);
}

// The info panel's sets line: "4.5 of 6".
export function setsOfTarget(region: RegionCoverage): string {
  return `${formatSets(region.effective)} of ${region.target}`;
}

// The gaps board's short form: "4.5/6".
export function setsSlashTarget(region: RegionCoverage): string {
  return `${formatSets(region.effective)}/${region.target}`;
}

// One line under the info panel: "Face pull · Tue · 3".
export function contributionLine(contribution: Contribution): string {
  return `${contribution.exerciseName} · ${contribution.day} · ${formatSets(contribution.sets)}`;
}

// The regions that need work most on the side of the body being shown.
// Regions with a target of 0 are skipped (the user doesn't want them), and regions drawn on
// both sides (e.g. side delts) count for both views. Ties keep the backend's region order.
export function pickGaps(
  coverage: RegionCoverage[],
  catalog: Region[],
  view: View,
  count = 5,
): RegionCoverage[] {
  const viewOf = new Map(catalog.map((region) => [region.id, region.view]));
  return coverage
    .filter((region) => region.target > 0)
    .filter((region) => {
      const regionView = viewOf.get(region.regionId);
      return regionView === view || regionView === "both";
    })
    .sort((a, b) => a.coverage - b.coverage)
    .slice(0, count);
}
