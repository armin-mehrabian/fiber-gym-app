import { contributionLine, setsOfTarget } from "@/lib/coverage";
import type { RegionCoverage } from "@/lib/types";

type RegionInfoProps = {
  week?: RegionCoverage; // the tapped region's week; undefined = nothing tapped
  name: string;
};

// What's behind the tapped region: sets against the target, and which exercises gave them.
// The outer box is always there (aria-live), so screen readers announce each new selection.
export default function RegionInfo({ week, name }: RegionInfoProps) {
  return (
    <div aria-live="polite" className="border-y border-line py-2.5">
      {week ? (
        <div data-testid="region-info" className="grid grid-cols-[1fr_auto] items-baseline gap-x-2.5 gap-y-1">
          <b data-testid="region-info-name" className="font-bold">
            {name}
          </b>
          <span data-testid="region-info-sets" className="font-display text-acc">
            {setsOfTarget(week)}
          </span>
          {week.contributions.length === 0 && (
            <p className="col-span-2 text-[0.8rem] text-mut">Not trained yet this week</p>
          )}
          {week.contributions.map((contribution, index) => (
            <p
              key={`${contribution.exerciseName}-${contribution.day}`}
              data-testid={`region-info-contribution-${index}`}
              className="col-span-2 text-[0.8rem] text-mut"
            >
              {contributionLine(contribution)}
            </p>
          ))}
          {week.recovering && (
            <p data-testid="region-info-recovering" className="col-span-2 text-[0.8rem] font-semibold">
              Recovering · trained hard in the last 48 h
            </p>
          )}
        </div>
      ) : (
        <p className="text-center text-xs tracking-[0.12em] text-mut">Tap a muscle</p>
      )}
    </div>
  );
}
