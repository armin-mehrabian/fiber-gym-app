import type { Metadata } from "next";
import { getRegions, getTargets } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import TargetsForm from "./TargetsForm";

export const metadata: Metadata = { title: "Weekly targets · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function TargetsPage() {
  await guardPage("/");
  const [targets, regions] = await Promise.all([getTargets(), getRegions()]);

  // Only regions that have a target row can be edited (a region added later would have none yet).
  const targetIds = new Set(targets.map((target) => target.regionId));
  const editableRegions = regions.filter((region) => targetIds.has(region.id));

  return <TargetsForm initialTargets={targets} regions={editableRegions} />;
}
