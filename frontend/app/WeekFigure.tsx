"use client"; // the Front/Back toggle and the tapped region are screen state

import { useState } from "react";
import Segmented from "@/components/Segmented";
import BodyFigure, { type RegionValues } from "@/components/figure/BodyFigure";
import type { RegionId, View } from "@/components/figure/shapes";
import { pickStillDark } from "@/lib/coverage";
import type { Badge, Region, RegionCoverage } from "@/lib/types";
import Badges from "./Badges";
import RegionInfo from "./RegionInfo";
import StillDark from "./StillDark";

const VIEWS: readonly View[] = ["front", "back"];

type WeekFigureProps = {
  coverage: RegionCoverage[]; // this week, from the backend
  badges: Badge[];
  catalog: Region[]; // names and which side each region is drawn on
  plan?: RegionValues; // the next workout's regions (they pulse); left out while a workout is in progress
};

// The hero of Home: the body lit by this week's training, the group badges, the details of
// whichever region is tapped, and what is still dark on the side being shown.
export default function WeekFigure({ coverage, badges, catalog, plan }: WeekFigureProps) {
  const [view, setView] = useState<View>("front");
  const [selected, setSelected] = useState<RegionId | undefined>();

  const values = Object.fromEntries(coverage.map((region) => [region.regionId, region.coverage])) as RegionValues;
  const names = new Map(catalog.map((region) => [region.id, region.name]));
  const selectedWeek = coverage.find((region) => region.regionId === selected);

  // A new side shows different regions, so the old selection would point at nothing visible.
  function changeView(next: View) {
    setView(next);
    setSelected(undefined);
  }

  // Tapping the selected region again closes its info.
  function toggleSelected(id: RegionId) {
    setSelected((current) => (current === id ? undefined : id));
  }

  return (
    <section className="grid gap-5">
      <div className="grid justify-items-center gap-3">
        <Segmented options={VIEWS} value={view} onChange={changeView} testIdPrefix="view-" label="Body view" />
        <BodyFigure
          view={view}
          values={values}
          plan={plan}
          selected={selected}
          onSelect={toggleSelected}
          size="hero"
          height={430}
        />
      </div>
      <Badges badges={badges} />
      <RegionInfo week={selectedWeek} name={selected ? (names.get(selected) ?? selected) : ""} />
      <StillDark regions={pickStillDark(coverage, catalog, view)} names={names} />
    </section>
  );
}
