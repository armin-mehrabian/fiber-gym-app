import { describe, expect, it } from "vitest";
import { bbox, fiberLines, FIBER_PATHS } from "./fibers";
import { SHAPES } from "./shapes";

// A 20 × 10 rectangle.
const square = "M10 20 L30 20 L30 30 L10 30 Z";

// Each fiber is one "M x y L x y" segment.
const countLines = (d: string) => (d.match(/M/g) ?? []).length;

describe("bbox", () => {
  it("finds the box around a path", () => {
    expect(bbox(square)).toEqual({ x0: 10, y0: 20, x1: 30, y1: 30 });
  });

  it("includes curve control points and negative numbers", () => {
    expect(bbox("M0 0 Q-5 40 10 10 Z")).toEqual({ x0: -5, y0: 0, x1: 10, y1: 40 });
  });
});

describe("fiberLines", () => {
  it("draws parallel lines about `gap` apart", () => {
    const d = fiberLines(square, { angle: 90 }, 5);
    // Half-diagonal ≈ 11.2 + 4 margin, so offsets run from about -15 to +15 in steps of 5: 7 lines.
    expect(countLines(d)).toBe(7);
    expect(d).toMatch(/^(M-?\d+\.\d -?\d+\.\dL-?\d+\.\d -?\d+\.\d)+$/);
  });

  it("starts every fan line at the fan point", () => {
    const d = fiberLines(square, { fan: [0, 0] }, 5);
    const starts = d.match(/M[^L]+/g) ?? [];
    expect(starts.length).toBeGreaterThan(1);
    for (const start of starts) expect(start).toBe("M0.0 0.0");
  });

  it("has fibers for every region in both views", () => {
    for (const view of ["front", "back"] as const) {
      for (const { id } of SHAPES[view]) {
        expect(countLines(FIBER_PATHS[view][id] ?? ""), `${view} ${id}`).toBeGreaterThan(0);
      }
    }
  });
});
