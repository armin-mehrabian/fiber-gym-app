// Text for past sets and weight suggestions (log screen, exercise page). The numbers come from
// the backend's progression rule; these helpers only convert units and write them out.
import type { LastTime, PastSet, SessionExercise, Units } from "@/lib/types";
import { WEIGHT_STEP, formatWeight, round2, toDisplayWeight } from "@/lib/units";

// A suggested weight as the user will see and edit it. kg values are shown as they are.
// lb values come from a kg conversion (e.g. 63.73 kg = 140.5 lb), so they are rounded to the
// nearest 5 lb: the step of the weight buttons, and a weight you can actually load.
export function suggestedDisplayWeight(kg: number, units: Units): number {
  if (units === "KG") {
    return kg;
  }
  const step = WEIGHT_STEP.LB;
  return Math.round(toDisplayWeight(kg, units) / step) * step;
}

// One set as "20 × 12", in the user's units (bodyweight shows 0).
export function setText(set: PastSet, units: Units): string {
  return `${formatWeight(toDisplayWeight(set.weightKg, units))} × ${set.reps}`;
}

// "20 × 12 · 12 · 22.5 × 10": the weight is only repeated when it changes, like a gym log.
// Warm-ups get a "W" in front.
export function setsText(sets: (PastSet & { isWarmup?: boolean })[], units: Units): string {
  let previousWeight = "";
  return sets
    .map((set) => {
      const weight = formatWeight(toDisplayWeight(set.weightKg, units));
      const warmup = set.isWarmup ? "W " : "";
      const text = weight === previousWeight ? `${set.reps}` : `${weight} × ${set.reps}`;
      previousWeight = weight;
      return warmup + text;
    })
    .join(" · ");
}

// "Last: 20 × 12 · 12 · 12", or null when there is nothing to show.
export function lastTimeText(lastTime: LastTime | null, units: Units): string | null {
  if (!lastTime || lastTime.sets.length === 0) {
    return null;
  }
  return `Last: ${setsText(lastTime.sets, units)}`;
}

// The heaviest weight of last time, in kg (what the suggestion builds on).
function topWeightKg(lastTime: LastTime | null): number | null {
  if (!lastTime || lastTime.sets.length === 0) {
    return null;
  }
  return Math.max(...lastTime.sets.map((set) => set.weightKg));
}

// One short line that says why today's numbers are what they are, or null without a reason.
export function suggestionText(exercise: SessionExercise, units: Units): string | null {
  const unit = units.toLowerCase();
  const top = exercise.repRange[1];
  switch (exercise.suggestionReason) {
    case "INCREASE": {
      const suggested = exercise.suggestedWeightKg;
      const last = topWeightKg(exercise.lastTime);
      if (suggested === null || last === null) {
        return `More weight · all sets hit ${top}`;
      }
      // Compared in display units, so the step matches the weight the set row starts at.
      const step = round2(suggestedDisplayWeight(suggested, units) - toDisplayWeight(last, units));
      return `+${formatWeight(step)} ${unit} · all sets hit ${top}`;
    }
    case "REPEAT":
      return "Same weight · aim for one more rep";
    case "MORE_REPS":
      return "Bodyweight · one more rep";
    case "NO_HISTORY":
      return "First time · pick a weight";
    default:
      return null;
  }
}
