// Text for the summary shown after FINISH.
import type { PersonalBest, Units } from "@/lib/types";
import { formatWeight, toDisplayWeight } from "@/lib/units";

// "Pull · Done"
export function summaryTitle(name: string): string {
  return `${name} · Done`;
}

// "Week 41% → 58%"
export function weekChangeText(before: number, after: number): string {
  return `Week ${before}% → ${after}%`;
}

// "New best · Bench press · 82.5 × 6", in the user's units (bodyweight shows 0).
export function personalBestText(best: PersonalBest, units: Units): string {
  const weight = formatWeight(toDisplayWeight(best.weightKg, units));
  return `New best · ${best.name} · ${weight} × ${best.reps}`;
}
