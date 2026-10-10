// One half of the body figure, painted in layers from back to front.
// BodyFigure draws it twice: as is (left half) and mirrored (right half).
import type { KeyboardEvent } from "react";
import { FIGURE_COLORS, fillFor } from "./colors";
import { FIBER_PATHS } from "./fibers";
import { BASE, REGION_NAMES, SHAPES, type RegionId, type View } from "./shapes";

export type FigureIds = { shade: string; plan: string; clip: Record<RegionId, string> };

type FigureHalfProps = {
  view: View;
  values: Partial<Record<RegionId, number>>;
  plan?: Partial<Record<RegionId, number>>;
  selected?: RegionId;
  onSelect?: (id: RegionId) => void;
  mini: boolean;
  ids: FigureIds;
  mirrored: boolean; // the mirrored copy is tappable but not announced or focusable twice
};

export default function FigureHalf({ view, values, plan, selected, onSelect, mini, ids, mirrored }: FigureHalfProps) {
  const regions = SHAPES[view];
  const planned = plan ? regions.filter(({ id }) => (plan[id] ?? 0) > 0) : [];
  const selectedShape = regions.find(({ id }) => id === selected);

  return (
    <g>
      {/* 1. Silhouette: every shape with a thick stroke, so the outline looks solid and rounded. */}
      <g fill={FIGURE_COLORS.underlay} stroke={FIGURE_COLORS.underlay} strokeWidth="10" strokeLinejoin="round">
        {BASE[view].map((d) => <path key={d} d={d} />)}
        {regions.map(({ id, d }) => <path key={id} d={d} />)}
      </g>

      {/* 2. Neutral tissue, then the regions coloured by coverage. The dark stroke draws the gaps between muscles. */}
      <g stroke={FIGURE_COLORS.gap} strokeWidth={mini ? 4 : 2.4} strokeLinejoin="round">
        {BASE[view].map((d) => <path key={d} d={d} fill={FIGURE_COLORS.base} />)}
        {regions.map(({ id, d }) => (
          <RegionPath
            key={id}
            id={id}
            d={d}
            fill={fillFor(values[id] ?? 0)}
            selected={id === selected}
            onSelect={onSelect}
            focusable={!mirrored}
          />
        ))}
      </g>

      {/* 3. Shading for volume. Decoration only, so taps go through to the region below. */}
      <g pointerEvents="none">
        {regions.map(({ id, d }) => <path key={id} d={d} fill={`url(#${ids.shade})`} />)}
      </g>

      {/* 4. Fiber lines, each clipped to its own region. Skipped on small figures, where they'd just be noise. */}
      {!mini && (
        <g fill="none" stroke={FIGURE_COLORS.fiber} strokeWidth="1.15" pointerEvents="none">
          {regions.map(({ id }) => (
            <path key={id} d={FIBER_PATHS[view][id]} clipPath={`url(#${ids.clip[id]})`} />
          ))}
        </g>
      )}

      {/* 5. Today's plan: stripes (stronger for main regions) and a marching dashed outline. */}
      {planned.length > 0 && (
        <g pointerEvents="none">
          {planned.map(({ id, d }) => (
            <path
              key={id}
              d={d}
              fill={`url(#${ids.plan})`}
              fillOpacity={(plan?.[id] ?? 0) >= 1 ? 0.9 : 0.45}
              stroke={FIGURE_COLORS.plan}
              strokeWidth={mini ? 5 : 3.2}
              strokeDasharray="10 6"
              strokeLinejoin="round"
              // The animation is skipped for people who ask for reduced motion.
              className="motion-safe:animate-march"
            />
          ))}
        </g>
      )}

      {/* 6. Selected region: a cream outline on top of everything. */}
      {selectedShape && (
        <path
          d={selectedShape.d}
          fill="none"
          stroke={FIGURE_COLORS.selected}
          strokeWidth="5"
          strokeLinejoin="round"
          pointerEvents="none"
        />
      )}
    </g>
  );
}

type RegionPathProps = {
  id: RegionId;
  d: string;
  fill: string;
  selected: boolean;
  onSelect?: (id: RegionId) => void;
  focusable: boolean;
};

// A single region. When the figure is tappable it also acts as a button for keyboard and screen reader users.
function RegionPath({ id, d, fill, selected, onSelect, focusable }: RegionPathProps) {
  const name = <title>{REGION_NAMES[id]}</title>;
  const fillClass = "transition-[fill] duration-300 motion-reduce:transition-none";

  if (!onSelect) {
    return (
      <path d={d} fill={fill} data-region={id} className={fillClass}>
        {name}
      </path>
    );
  }

  function handleKeyDown(event: KeyboardEvent<SVGPathElement>) {
    // Buttons react to Enter and Space; preventDefault stops Space from scrolling the page.
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onSelect?.(id);
    }
  }

  return (
    <path
      d={d}
      fill={fill}
      data-region={id}
      className={`${fillClass} cursor-pointer outline-none hover:brightness-125 focus-visible:stroke-fg focus-visible:stroke-[4]`}
      onClick={() => onSelect(id)}
      role={focusable ? "button" : undefined}
      tabIndex={focusable ? 0 : undefined}
      aria-pressed={focusable ? selected : undefined}
      onKeyDown={focusable ? handleKeyDown : undefined}
    >
      {name}
    </path>
  );
}
