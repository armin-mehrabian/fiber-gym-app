import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { bbox } from "./fibers";
import { REGION_IDS, REGION_NAMES, SHAPES, type RegionId, type View } from "./shapes";

// The seed file the backend serves at GET /api/regions (repo root: data/regions.json).
type CatalogRegion = { id: string; name: string; view: "front" | "back" | "both" };
const catalog: CatalogRegion[] = JSON.parse(
  readFileSync(new URL("../../../data/regions.json", import.meta.url), "utf8"),
);

const idsIn = (view: "front" | "back") => SHAPES[view].map((shape) => shape.id as string);

describe("figure regions match the catalog", () => {
  it("has exactly the catalog's region ids", () => {
    expect([...REGION_IDS].sort()).toEqual(catalog.map((r) => r.id).sort());
  });

  it("uses the catalog's display names", () => {
    for (const region of catalog) {
      expect(REGION_NAMES[region.id as keyof typeof REGION_NAMES]).toBe(region.name);
    }
  });

  it("draws each region in the view(s) the catalog says", () => {
    for (const region of catalog) {
      const onFront = idsIn("front").includes(region.id);
      const onBack = idsIn("back").includes(region.id);
      expect({ id: region.id, onFront, onBack }).toEqual({
        id: region.id,
        onFront: region.view !== "back",
        onBack: region.view !== "front",
      });
    }
  });

  it("draws each region at most once per view", () => {
    for (const view of ["front", "back"] as const) {
      expect(new Set(idsIn(view)).size).toBe(idsIn(view).length);
    }
  });
});

// Box around one region's outline (left half, so the centre line x = 200 is to the right).
const boxOf = (view: View, id: RegionId) => bbox(SHAPES[view].find((shape) => shape.id === id)?.d ?? "");

describe("split muscles sit on the right side", () => {
  it("puts the lateral triceps head on the outside of the long head", () => {
    expect(boxOf("back", "triceps_lateral").x0).toBeLessThan(boxOf("back", "triceps_long").x0);
  });

  it("puts the rectus femoris between the outer and inner vasti", () => {
    const rectus = boxOf("front", "quads_rectus");
    const vasti = boxOf("front", "quads_vasti");
    expect(rectus.x0).toBeGreaterThan(vasti.x0);
    expect(rectus.x1).toBeLessThan(vasti.x1);
  });
});
