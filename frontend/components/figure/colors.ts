// Body figure palette (Varsity). These live in code rather than the Tailwind theme
// because SVG fills are set per region and the coverage colour is computed (see fillFor).
export const FIGURE_COLORS = {
  underlay: "#142c21", // dark silhouette behind everything
  base: "#1b3529", // neutral tissue: neck, knees, hands…
  gap: "#0b1812", // thin lines between muscles
  untrained: "#234032", // region with no coverage yet
  fiber: "rgba(0,0,0,.32)",
  plan: "#f3ead6", // today's plan: stripes + dashed outline
  selected: "#f3ead6", // outline of the tapped region
} as const;

// Coverage scale from 0 to 1: olive → bronze → varsity gold → pale gold.
export const COVERAGE_STOPS = ["#43502a", "#a5841f", "#f3b62b", "#ffe6a0"] as const;

// "#a5841f" → [165, 132, 31]
function hexToRgb(hex: string): number[] {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
}

// Picks a colour on the coverage scale by blending the two nearest stops.
export function scaleColor(value: number): string {
  const v = Math.max(0, Math.min(1, value));
  const segments = COVERAGE_STOPS.length - 1;
  const position = v * segments;
  // The last segment also covers v = 1, so `i + 1` never runs past the end.
  const i = Math.min(segments - 1, Math.floor(position));
  const t = position - i;
  const from = hexToRgb(COVERAGE_STOPS[i]);
  const to = hexToRgb(COVERAGE_STOPS[i + 1]);
  const mixed = from.map((c, k) => Math.round(c + (to[k] - c) * t));
  return `rgb(${mixed.join(",")})`;
}

// Fill for a region: untrained green at 0, otherwise its place on the coverage scale.
export function fillFor(value: number): string {
  return value > 0 ? scaleColor(value) : FIGURE_COLORS.untrained;
}
