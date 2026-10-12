// Text and picks for Home. The states and badges come from the backend; these helpers only
// choose what to show and how to write it. Home shows words, not numbers.
import type { View } from "@/components/figure/shapes";
import type { Badge, Contribution, Region, RegionCoverage, RegionState } from "@/lib/types";

// The tap info's state line.
const STATE_LABELS: Record<RegionState, string> = {
  DONE: "Done ✓",
  STARTED: "Started",
  NOT_YET: "Not yet",
};

export function regionStateLabel(state: RegionState): string {
  return STATE_LABELS[state];
}

// One line of the tap info: "Incline press · Tue". The sets it gave are left out on purpose.
export function contributionLine(contribution: Contribution): string {
  return `${contribution.exerciseName} · ${contribution.day}`;
}

// The small hint on a badge that isn't done yet: "2/3 parts". The only number on Home.
export function badgeHint(badge: Badge): string {
  return `${badge.regionsDone}/${badge.regionsTotal} parts`;
}

// True when a region is drawn on this side. Regions drawn on both sides (e.g. side delts) count for both.
function isOnSide(region: Region | undefined, view: View): boolean {
  return region?.view === view || region?.view === "both";
}

// The "Still dark" list: regions with nothing yet this week on the side being shown, in region order.
// A target of 0 counts as DONE in the backend, so regions the user doesn't train never show up here.
export function pickStillDark(
  coverage: RegionCoverage[],
  catalog: Region[],
  view: View,
  count = 5,
): RegionCoverage[] {
  const regionById = new Map(catalog.map((region) => [region.id, region]));
  return coverage
    .filter((region) => region.state === "NOT_YET")
    .filter((region) => isOnSide(regionById.get(region.regionId), view))
    .slice(0, count);
}

// The NEXT UP line: "Fills Rear delts · Lower lats · Upper traps". Names up to `count` main (value 1)
// regions of the plan, in region order. The separator is " · " because some region names contain a
// comma ("Biceps, long head"). null when the plan has no main region.
export function fillsLine(planned: Record<string, number>, catalog: Region[], count = 3): string | null {
  const names = [...catalog]
    .sort((a, b) => a.order - b.order)
    .filter((region) => planned[region.id] === 1)
    .slice(0, count)
    .map((region) => region.name);
  return names.length > 0 ? `Fills ${names.join(" · ")}` : null;
}
