// Text for the summary shown after FINISH.
import type { PersonalBest, Units } from "@/lib/types";
import { formatWeight, toDisplayWeight } from "@/lib/units";

// "Pull · Done"
export function summaryTitle(name: string): string {
  return `${name} · Done`;
}

// "Lit up: Upper chest · Front delts": the regions this workout finished for the week, in the order
// given (the backend sends region order). " · " separates them because some region names contain a
// comma ("Biceps, long head"). Unknown ids show as they are. With none it's still a
// good day: the regions were already done, so the workout kept them that way.
export function litLine(regionsLit: string[], names: Map<string, string>): string {
  if (regionsLit.length === 0) {
    return "Kept the lights on";
  }
  return `Lit up: ${regionsLit.map((id) => names.get(id) ?? id).join(" · ")}`;
}

// "New best · Bench press · 82.5 × 6", in the user's units (bodyweight shows 0).
export function personalBestText(best: PersonalBest, units: Units): string {
  const weight = formatWeight(toDisplayWeight(best.weightKg, units));
  return `New best · ${best.name} · ${weight} × ${best.reps}`;
}
