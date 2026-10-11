import { setsSlashTarget } from "@/lib/coverage";
import type { RegionCoverage } from "@/lib/types";

type GapsBoardProps = {
  gaps: RegionCoverage[]; // already picked and sorted, weakest first
  names: Map<string, string>; // region id → name
};

// The short ranked list next to the figure: what needs work most this week.
export default function GapsBoard({ gaps, names }: GapsBoardProps) {
  return (
    <div className="grid content-start">
      <h2 className="border-b-[3px] border-double border-acc pb-2 font-display text-[0.95rem] font-normal text-acc">
        Gaps
      </h2>
      {/* Only empty when every region on this side has a target of 0. */}
      {gaps.length === 0 && <p className="pt-3 text-[0.8rem] tracking-[0.12em] text-mut">No gaps</p>}
      <ol>
        {gaps.map((gap) => (
          <li
            key={gap.regionId}
            data-testid={`gap-${gap.regionId}`}
            className="flex min-h-[46px] items-baseline justify-between gap-1.5 border-b border-line pt-3 text-[0.9rem] font-semibold"
          >
            <span>{names.get(gap.regionId) ?? gap.regionId}</span>
            <b className="shrink-0 font-display text-[0.95rem] font-normal text-acc">{setsSlashTarget(gap)}</b>
          </li>
        ))}
      </ol>
    </div>
  );
}
