// The fixed choices offered on the onboarding and settings screens.
import type { SplitType, Units } from "@/lib/types";

// CUSTOM splits exist in the API, but the screens only offer the three presets for now.
export const SPLIT_CHOICES: { id: Exclude<SplitType, "CUSTOM">; name: string }[] = [
  { id: "PPL", name: "Push / Pull / Legs" },
  { id: "UL", name: "Upper / Lower" },
  { id: "FB", name: "Full body" },
];

export const UNIT_CHOICES: readonly Units[] = ["KG", "LB"];

// Adds the id if it's missing, removes it if it's there (for multi-select tiles).
export function toggleId(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((existing) => existing !== id) : [...ids, id];
}
