"use client"; // the Front/Back toggle and the tapped region are screen state

import { useState } from "react";
import Segmented from "@/components/Segmented";
import BodyFigure, { type RegionValues } from "@/components/figure/BodyFigure";
import type { RegionId, View } from "@/components/figure/shapes";
import { pickGaps } from "@/lib/coverage";
import type { Region, RegionCoverage } from "@/lib/types";
import GapsBoard from "./GapsBoard";
import RegionInfo from "./RegionInfo";

const VIEWS: readonly View[] = ["front", "back"];

type WeekFigureProps = {
  coverage: RegionCoverage[]; // this week, from the backend
  catalog: Region[]; // names and which side each region is drawn on
};

// The hero of Home: the body filled by this week's coverage, the gaps beside it,
// and the details of whichever region is tapped.
export default function WeekFigure({ coverage, catalog }: WeekFigureProps) {
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
    <section className="grid gap-3">
      <div className="grid grid-cols-[auto_1fr] items-start gap-3">
        <div className="grid justify-items-center gap-2.5">
          <Segmented options={VIEWS} value={view} onChange={changeView} testIdPrefix="view-" label="Body view" />
          <BodyFigure view={view} values={values} selected={selected} onSelect={toggleSelected} size="hero" />
        </div>
        <GapsBoard gaps={pickGaps(coverage, catalog, view)} names={names} />
      </div>
      <RegionInfo week={selectedWeek} name={selected ? (names.get(selected) ?? selected) : ""} />
    </section>
  );
}
