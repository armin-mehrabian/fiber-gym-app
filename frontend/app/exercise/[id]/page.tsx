import type { Metadata } from "next";
import { notFound } from "next/navigation";
import BackLink from "@/components/BackLink";
import FigurePair from "@/components/figure/FigurePair";
import { fillFor } from "@/components/figure/colors";
import { exerciseValues } from "@/components/figure/exercise";
import { REGION_NAMES, type RegionId } from "@/components/figure/shapes";
import { getExercise, getExerciseHistory, getSettings } from "@/lib/api";
import { formatDay } from "@/lib/dates";
import { guardPage } from "@/lib/guard";
import { setText, setsText } from "@/lib/progress";

export const metadata: Metadata = { title: "Exercise · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type ExercisePageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ position?: string | string[] }>;
};

// "Mid chest · Front delts" (unknown ids are shown as they are).
function regionNames(ids: string[]): string {
  return ids.map((id) => REGION_NAMES[id as RegionId] ?? id).join(" · ");
}

export default async function ExercisePage({ params, searchParams }: ExercisePageProps) {
  await guardPage("/");
  const { id } = await params;
  const { position } = await searchParams;
  const [exercise, settings] = await Promise.all([getExercise(id), getSettings()]);
  if (!exercise) {
    notFound();
  }
  // Asked only once the exercise is known to exist, so an unknown id shows "not found", not an error.
  const history = await getExerciseHistory(id);
  const units = settings.units;
  // Opened from the log screen (?position=3): go back there. Only digits are accepted, so the
  // link can never point somewhere unexpected.
  const from = Array.isArray(position) ? position[0] : position;
  const backHref = from && /^\d+$/.test(from) ? `/workout/${from}` : "/";
  const [repLow, repHigh] = exercise.repRange;

  return (
    <div className="grid gap-5">
      <header className="flex min-h-11 items-center gap-2">
        <BackLink href={backHref} label="Back" />
      </header>

      {/* 4:5 slot kept free for a demo video later; for now it shows the regions the exercise works. */}
      <div className="grid aspect-[4/5] w-full place-items-center bg-hl">
        <FigurePair
          values={exerciseValues(exercise.primary, exercise.secondary)}
          size="mini"
          height={300}
          testId="exercise-figure"
        />
      </div>

      <h1 data-testid="exercise-name" className="font-display text-[2rem] leading-[0.95]">
        {exercise.name}
      </h1>

      <div className="flex flex-wrap gap-x-3.5 gap-y-2 text-[0.85rem]">
        <RegionTag ids={exercise.primary} level={1} />
        {exercise.secondary.length > 0 && <RegionTag ids={exercise.secondary} level={0.5} muted />}
      </div>

      <p data-testid="exercise-cue" className="text-[1.1rem] leading-snug">
        {exercise.cue}
      </p>

      {/* Last = the first working set of the last workout; all its sets are in the history below. */}
      <div className="grid grid-cols-3 divide-x divide-line border-y border-line">
        <StatColumn
          label="Best"
          value={exercise.best ? setText(exercise.best, units) : null}
          testId="exercise-best"
        />
        <StatColumn
          label="Last"
          value={exercise.last?.sets[0] ? setText(exercise.last.sets[0], units) : null}
          testId="exercise-last"
        />
        <StatColumn label="Reps" value={`${repLow}–${repHigh}`} />
      </div>

      <section className="grid gap-2">
        <h2 className="font-display text-[1.15rem] font-normal">History</h2>
        {/* Varsity double-rule stripe. */}
        <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
        {history.length === 0 ? (
          <p className="text-sm tracking-[0.12em] text-mut">No history yet</p>
        ) : (
          <div className="grid">
            {history.map((entry) => (
              <div
                key={entry.sessionId}
                data-testid={`exercise-history-${entry.sessionId}`}
                className="grid min-h-[58px] grid-cols-[6.5em_1fr] items-center gap-2.5 border-b border-line"
              >
                <span className="text-[0.85rem] text-mut">{formatDay(entry.date)}</span>
                <span className="tabular-nums">{setsText(entry.sets, units)}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

// A dot in the figure's colour for that level, then the region names.
function RegionTag({ ids, level, muted }: { ids: string[]; level: number; muted?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1.5 ${muted ? "text-mut" : ""}`}>
      <i className="inline-block size-2.5 shrink-0 rounded-full" style={{ background: fillFor(level) }} />
      {regionNames(ids)}
    </span>
  );
}

// One centred stat: number above, label below. A missing value shows a dash (without the test id),
// so the three columns keep their places.
function StatColumn({ label, value, testId }: { label: string; value: string | null; testId?: string }) {
  return (
    <div className="grid min-h-[68px] content-center justify-items-center gap-1 px-1 py-2.5 text-center">
      {value === null ? (
        <b className="font-display text-[1.15rem] font-normal text-mut">—</b>
      ) : (
        <b data-testid={testId} className="font-display text-[1.15rem] leading-none font-normal tabular-nums text-acc">
          {value}
        </b>
      )}
      <span className="text-[0.75rem] tracking-[0.12em] text-mut">{label}</span>
    </div>
  );
}
