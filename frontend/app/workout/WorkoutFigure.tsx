"use client"; // the Front/Back toggle is screen state

import { useState } from "react";
import Segmented from "@/components/Segmented";
import BodyFigure, { type RegionValues } from "@/components/figure/BodyFigure";
import type { View } from "@/components/figure/shapes";

const VIEWS: readonly View[] = ["front", "back"];

type WorkoutFigureProps = {
  week: RegionValues; // this week's coverage (fill)
  plan: RegionValues; // this workout's regions (stripes)
  firstView: View; // the side of the first exercise's main region, so the plan is visible at once
};

// The hero of the workout screen: what the week already has, and what today adds.
export default function WorkoutFigure({ week, plan, firstView }: WorkoutFigureProps) {
  const [view, setView] = useState<View>(firstView);

  return (
    <section className="grid justify-items-center gap-2.5">
      <Segmented options={VIEWS} value={view} onChange={setView} testIdPrefix="view-" label="Body view" />
      <div data-testid="workout-figure">
        <BodyFigure view={view} values={week} plan={plan} size="hero" height={330} />
      </div>
    </section>
  );
}
