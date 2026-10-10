// Made-up sample data for the figure preview page. Not real training data.
import type { RegionValues } from "@/components/figure/BodyFigure";
import { REGION_IDS, type RegionId } from "@/components/figure/shapes";

type ExampleRegion = { sets: number; target: number; from: string[] };

// One example week: hard sets done, the weekly target, and where the sets came from.
export const EXAMPLE_WEEK: Record<RegionId, ExampleRegion> = {
  chest_upper: { sets: 3, target: 8, from: ["Incline DB press · Mon"] },
  chest_mid: { sets: 8, target: 8, from: ["Bench press · Mon", "Cable fly · Mon"] },
  chest_lower: { sets: 6, target: 8, from: ["Dips · Mon", "Bench press · Mon"] },
  delt_front: { sets: 6, target: 6, from: ["Overhead press · Mon", "Bench press · Mon"] },
  delt_side: { sets: 2, target: 6, from: ["Lateral raise · Mon"] },
  delt_rear: { sets: 0, target: 6, from: [] },
  traps_upper: { sets: 1, target: 6, from: ["Overhead press · Mon"] },
  traps_mid: { sets: 0, target: 6, from: [] },
  lats_upper: { sets: 0, target: 8, from: [] },
  lats_lower: { sets: 0, target: 8, from: [] },
  lower_back: { sets: 2, target: 6, from: ["Romanian deadlift · Wed"] },
  biceps_long: { sets: 0, target: 6, from: [] },
  biceps_short: { sets: 0.5, target: 6, from: ["Dips · Mon"] },
  triceps_long: { sets: 5, target: 8, from: ["Dips · Mon", "Rope pushdown · Mon"] },
  triceps_lateral: { sets: 5, target: 8, from: ["Dips · Mon", "Rope pushdown · Mon"] },
  forearms: { sets: 1, target: 6, from: ["Romanian deadlift · Wed"] },
  abs_upper: { sets: 2, target: 6, from: ["Cable crunch · Wed"] },
  abs_lower: { sets: 1, target: 6, from: ["Cable crunch · Wed"] },
  obliques: { sets: 0, target: 6, from: [] },
  quads_rectus: { sets: 8, target: 8, from: ["Back squat · Wed", "Leg press · Wed", "Leg extension · Wed"] },
  quads_vasti: { sets: 8, target: 8, from: ["Back squat · Wed", "Leg press · Wed", "Leg extension · Wed"] },
  adductors: { sets: 3, target: 6, from: ["Back squat · Wed"] },
  glutes: { sets: 5, target: 8, from: ["Back squat · Wed", "Romanian deadlift · Wed"] },
  glute_med: { sets: 2, target: 6, from: ["Back squat · Wed"] },
  hamstrings: { sets: 4, target: 8, from: ["Romanian deadlift · Wed"] },
  calves: { sets: 2, target: 6, from: ["Calf raise · Wed"] },
};

// How full each region is this week (sets / target, capped at 1).
export const EXAMPLE_VALUES: RegionValues = Object.fromEntries(
  REGION_IDS.map((id) => [id, Math.min(1, EXAMPLE_WEEK[id].sets / EXAMPLE_WEEK[id].target)]),
);

// Today's plan for an example Pull day: 1 = main region, 0.5 = helping region.
export const EXAMPLE_PLAN: RegionValues = {
  lats_upper: 1,
  lats_lower: 1,
  traps_mid: 1,
  delt_rear: 1,
  biceps_long: 1,
  biceps_short: 0.5,
  forearms: 0.5,
};

type ExampleExercise = { name: string; main: RegionId[]; helping: RegionId[] };

export const EXAMPLE_EXERCISES: ExampleExercise[] = [
  { name: "Single-arm cable row", main: ["lats_lower"], helping: ["traps_mid", "biceps_long", "delt_rear"] },
  { name: "Incline barbell press", main: ["chest_upper"], helping: ["delt_front", "triceps_lateral"] },
];

// An exercise drawn on the figure: main regions full, helping regions half.
export function exerciseValues(exercise: ExampleExercise): RegionValues {
  const values: RegionValues = {};
  for (const id of exercise.helping) values[id] = 0.5;
  for (const id of exercise.main) values[id] = 1;
  return values;
}
