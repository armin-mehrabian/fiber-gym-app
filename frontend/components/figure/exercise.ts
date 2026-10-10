// Turns an exercise's region lists into what a mini BodyFigure needs.
import type { RegionValues } from "./BodyFigure";
import { REGION_IDS, SHAPES, type RegionId, type View } from "./shapes";

function isRegionId(id: string): id is RegionId {
  return (REGION_IDS as readonly string[]).includes(id);
}

// The side of the body a region is drawn on. Regions drawn on both sides (e.g. side delts) use the front.
export function viewOf(regionId: string): View {
  return SHAPES.front.some((shape) => shape.id === regionId) ? "front" : "back";
}

// Main regions fully lit, helping regions half lit. Unknown ids are skipped.
export function exerciseValues(primary: string[], secondary: string[]): RegionValues {
  const values: RegionValues = {};
  for (const id of secondary.filter(isRegionId)) {
    values[id] = 0.5;
  }
  for (const id of primary.filter(isRegionId)) {
    values[id] = 1;
  }
  return values;
}
