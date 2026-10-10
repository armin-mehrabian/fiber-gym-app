// Weight helpers. The API always sends and stores kg; LB users see and edit pounds,
// and the screens convert at the edges with these functions.
import type { Units } from "@/lib/types";

const LB_PER_KG = 2.20462;

// The backend keeps weights with 2 decimals, so conversions round to 2 decimals too.
export function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

export function kgToLb(kg: number): number {
  return round2(kg * LB_PER_KG);
}

export function lbToKg(lb: number): number {
  return round2(lb / LB_PER_KG);
}

// kg from the API → the number the user sees and edits.
export function toDisplayWeight(kg: number, units: Units): number {
  return units === "LB" ? kgToLb(kg) : kg;
}

// The number the user sees → kg for the API.
export function toKg(value: number, units: Units): number {
  return units === "LB" ? lbToKg(value) : round2(value);
}

// Shows at most one decimal, without a trailing ".0": 22.5 → "22.5", 45 → "45".
// One decimal is enough for 2.5 kg / 5 lb steps, and it hides the 0.01 that a
// lb → kg → lb round trip can add (55 lb is stored as 24.95 kg, which is 55.01 lb).
export function formatWeight(value: number): string {
  return String(Math.round(value * 10) / 10);
}

// One tap on a weight stepper: the smallest common plate pair.
export const WEIGHT_STEP: Record<Units, number> = { KG: 2.5, LB: 5 };

// The API accepts 0–1000 kg; in pounds that is 0–2204.62.
export function maxWeight(units: Units): number {
  return toDisplayWeight(1000, units);
}

// Where the first set of an exercise starts, in the user's units:
// an empty bar (20 kg / 45 lb), or 0 for bodyweight exercises (no equipment needed).
export function firstSetWeight(units: Units, bodyweight: boolean): number {
  if (bodyweight) {
    return 0;
  }
  return units === "LB" ? 45 : 20;
}

// One stepper tap: add the step, round away float noise (0.1 + 0.2), and stay within min..max.
export function stepValue(value: number, delta: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, round2(value + delta)));
}
