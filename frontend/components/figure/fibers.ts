// Fiber lines: thin strokes inside each region that show which way the muscle pulls.
// The lines are drawn generously (past the edges) and the figure clips them to the region.
import { FIBER_SPEC, SHAPES, type FiberSpec, type RegionId, type View } from "./shapes";

export type Box = { x0: number; y0: number; x1: number; y1: number };

// Bounding box of a path. Our paths only use absolute M, L, Q and Z commands,
// so every number comes in an (x, y) pair: control points included, which is fine for a rough box.
export function bbox(d: string): Box {
  const numbers = (d.match(/-?\d+(\.\d+)?/g) ?? []).map(Number);
  const xs: number[] = [];
  const ys: number[] = [];
  for (let i = 0; i < numbers.length; i += 2) {
    xs.push(numbers[i]);
    ys.push(numbers[i + 1]);
  }
  return { x0: Math.min(...xs), y0: Math.min(...ys), x1: Math.max(...xs), y1: Math.max(...ys) };
}

// One decimal is plenty at this drawing size and keeps the markup small.
const round = (n: number) => n.toFixed(1);
const line = (ax: number, ay: number, bx: number, by: number) =>
  `M${round(ax)} ${round(ay)}L${round(bx)} ${round(by)}`;

// Builds one path made of many straight lines, roughly `gap` units apart, covering the region's box.
export function fiberLines(d: string, spec: FiberSpec, gap: number): string {
  const box = bbox(d);
  const cx = (box.x0 + box.x1) / 2;
  const cy = (box.y0 + box.y1) / 2;
  let out = "";

  if ("fan" in spec) {
    // Lines start at the fan point and sweep across every angle that reaches the box.
    const [ox, oy] = spec.fan;
    const corners = [
      [box.x0, box.y0],
      [box.x1, box.y0],
      [box.x0, box.y1],
      [box.x1, box.y1],
    ];
    let angles = corners.map(([x, y]) => Math.atan2(y - oy, x - ox));
    // atan2 jumps from +180° to -180°. If the box straddles that seam, shift to 0..360° so the sweep is continuous.
    if (Math.max(...angles) - Math.min(...angles) > Math.PI) {
      angles = angles.map((a) => (a < 0 ? a + 2 * Math.PI : a));
    }
    // Long enough to pass the farthest corner.
    const length = Math.max(...corners.map(([x, y]) => Math.hypot(x - ox, y - oy))) + 4;
    // Angle step that gives about `gap` units between lines at the region's centre.
    const step = gap / Math.max(20, Math.hypot(cx - ox, cy - oy));
    for (let a = Math.min(...angles); a <= Math.max(...angles); a += step) {
      out += line(ox, oy, ox + length * Math.cos(a), oy + length * Math.sin(a));
    }
  } else {
    // Parallel lines: (ux, uy) runs along a line, and we step sideways across the box.
    const a = (spec.angle * Math.PI) / 180;
    const ux = Math.cos(a);
    const uy = Math.sin(a);
    const half = Math.hypot(box.x1 - box.x0, box.y1 - box.y0) / 2 + 4;
    for (let s = -half; s <= half; s += gap) {
      const px = cx - uy * s;
      const py = cy + ux * s;
      out += line(px - ux * half, py - uy * half, px + ux * half, py + uy * half);
    }
  }
  return out;
}

const FIBER_GAP = 5;

// Computed once when the module loads: the shapes never change, so neither do their fibers.
function buildFiberPaths(view: View): Partial<Record<RegionId, string>> {
  const paths: Partial<Record<RegionId, string>> = {};
  for (const { id, d } of SHAPES[view]) {
    const spec = FIBER_SPEC[view][id];
    if (spec) paths[id] = fiberLines(d, spec, FIBER_GAP);
  }
  return paths;
}

export const FIBER_PATHS: Record<View, Partial<Record<RegionId, string>>> = {
  front: buildFiberPaths("front"),
  back: buildFiberPaths("back"),
};
