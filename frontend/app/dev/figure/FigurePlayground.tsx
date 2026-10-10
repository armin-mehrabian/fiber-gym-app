"use client"; // holds the view, plan toggle and selected region as state

import { useState } from "react";
import BodyFigure from "@/components/figure/BodyFigure";
import { REGION_NAMES, type RegionId, type View } from "@/components/figure/shapes";
import { EXAMPLE_PLAN, EXAMPLE_VALUES, EXAMPLE_WEEK } from "./exampleData";

const VIEWS: { id: View; label: string }[] = [
  { id: "front", label: "Front" },
  { id: "back", label: "Back" },
];

const controlClass = "min-h-11 min-w-[62px] px-3 text-sm font-bold tracking-[0.12em]";

export default function FigurePlayground() {
  const [view, setView] = useState<View>("front");
  const [showPlan, setShowPlan] = useState(false);
  const [selected, setSelected] = useState<RegionId | undefined>();

  // Tapping the selected region again clears the selection.
  function toggleSelected(id: RegionId) {
    setSelected((current) => (current === id ? undefined : id));
  }

  return (
    <section className="grid justify-items-center gap-3">
      <div className="flex w-full items-center justify-between gap-3">
        <div role="group" aria-label="Body view" className="inline-flex gap-[2px] border-[1.5px] border-edge p-[3px]">
          {VIEWS.map((option) => (
            <button
              key={option.id}
              type="button"
              aria-pressed={view === option.id}
              onClick={() => setView(option.id)}
              className={`${controlClass} ${view === option.id ? "bg-acc text-on-acc" : "text-mut"}`}
            >
              {option.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          aria-pressed={showPlan}
          onClick={() => setShowPlan(!showPlan)}
          className={`${controlClass} border-[1.5px] ${showPlan ? "border-acc bg-acc text-on-acc" : "border-edge text-mut"}`}
        >
          Today&apos;s plan
        </button>
      </div>

      <BodyFigure
        view={view}
        values={EXAMPLE_VALUES}
        plan={showPlan ? EXAMPLE_PLAN : undefined}
        selected={selected}
        onSelect={toggleSelected}
        size="hero"
        height={400}
      />

      <SelectedInfo id={selected} />
    </section>
  );
}

// Name, sets vs target and the example sets behind it. aria-live so screen readers hear the change.
function SelectedInfo({ id }: { id?: RegionId }) {
  return (
    <div aria-live="polite" className="w-full border-y border-line py-2.5">
      {id ? (
        <div className="grid grid-cols-[1fr_auto] gap-x-2.5 gap-y-0.5">
          <b className="font-bold">{REGION_NAMES[id]}</b>
          <span className="font-display text-acc">
            {EXAMPLE_WEEK[id].sets}/{EXAMPLE_WEEK[id].target}
          </span>
          <span className="col-span-2 text-xs text-mut">
            {EXAMPLE_WEEK[id].from.length > 0 ? EXAMPLE_WEEK[id].from.join(" · ") : "Not hit this week"}
          </span>
        </div>
      ) : (
        <p className="text-center text-xs tracking-[0.12em] text-mut">Tap a muscle</p>
      )}
    </div>
  );
}
