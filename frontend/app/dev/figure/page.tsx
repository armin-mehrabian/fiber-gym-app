// Preview page for the body figure (not linked from the nav). Everything here is example data.
import type { Metadata } from "next";
import BodyFigure from "@/components/figure/BodyFigure";
import { REGION_NAMES } from "@/components/figure/shapes";
import FigurePlayground from "./FigurePlayground";
import { EXAMPLE_EXERCISES, exerciseValues } from "./exampleData";

export const metadata: Metadata = { title: "Figure preview · Fiber" };

export default function FigurePreviewPage() {
  return (
    <div className="grid gap-5">
      <header className="flex items-baseline justify-between">
        <h1 className="font-display text-[1.6rem] text-acc">Figure</h1>
        <span className="text-xs tracking-[0.12em] text-mut">Example data</span>
      </header>

      <FigurePlayground />

      {/* Varsity double-rule stripe as a section divider. */}
      <div className="h-2.5 border-y-[3px] border-acc" />

      {/* Mini figures render on the server: no onSelect, so no JavaScript is needed for them. */}
      <section>
        <h2 className="pb-1 text-xs tracking-[0.12em] text-mut">Exercise figures</h2>
        {EXAMPLE_EXERCISES.map((exercise) => {
          const values = exerciseValues(exercise);
          return (
            <div
              key={exercise.name}
              className="grid min-h-[76px] grid-cols-[1fr_auto] items-center gap-3 border-b border-line py-2"
            >
              <div className="grid gap-0.5">
                <b className="font-bold">{exercise.name}</b>
                <span className="text-xs text-mut">{exercise.main.map((id) => REGION_NAMES[id]).join(" · ")}</span>
              </div>
              <div className="flex">
                <BodyFigure view="front" values={values} size="mini" height={70} />
                <BodyFigure view="back" values={values} size="mini" height={70} />
              </div>
            </div>
          );
        })}
      </section>
    </div>
  );
}
