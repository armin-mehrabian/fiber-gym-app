import type { RegionCoverage } from "@/lib/types";

type StillDarkProps = {
  regions: RegionCoverage[]; // already picked for the side being shown, in region order
  names: Map<string, string>; // region id → name
};

// The short list under the figure: what hasn't been touched yet this week on this side.
export default function StillDark({ regions, names }: StillDarkProps) {
  return (
    <section className="grid">
      <h2 className="border-b-[3px] border-double border-acc pb-2 font-display text-[0.95rem] font-normal text-acc">
        Still dark
      </h2>
      {regions.length === 0 ? (
        <p data-testid="dark-none" className="pt-3 text-[0.85rem] tracking-[0.12em] text-mut">
          Nothing dark on this side
        </p>
      ) : (
        <ul>
          {regions.map((region) => (
            <li
              key={region.regionId}
              data-testid={`dark-${region.regionId}`}
              className="flex min-h-11 items-center border-b border-line text-[0.95rem] font-semibold"
            >
              {names.get(region.regionId) ?? region.regionId}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
