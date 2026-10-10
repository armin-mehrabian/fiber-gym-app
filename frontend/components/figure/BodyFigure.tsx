// No "use client" on purpose. This component only uses useId, which works on the server too:
// - Used from a server page (e.g. read-only mini figures), it renders to plain SVG with zero JavaScript.
// - Passing onSelect requires a client parent (functions can't be sent from the server), and then
//   it simply runs as part of that client component. So one file covers both cases.
import { useId } from "react";
import { FIGURE_COLORS } from "./colors";
import FigureHalf, { type FigureIds } from "./FigureHalf";
import { REGION_IDS, SHAPES, type RegionId, type View } from "./shapes";

export type RegionValues = Partial<Record<RegionId, number>>;

export type BodyFigureProps = {
  view: View;
  values: RegionValues; // 0..1 coverage per region; missing = untrained
  plan?: RegionValues; // today's plan: 1 = main region, 0.5 = helping region
  selected?: RegionId;
  onSelect?: (id: RegionId) => void; // called with the tapped region (hero only); the parent decides what to do
  size: "hero" | "mini"; // mini skips the fiber lines
  height?: number; // px
};

// The drawing area that holds head to feet (the drawing is 400 units wide, centred on x = 200).
const VIEW_BOX = { x: 56, y: 8, width: 288, height: 836 };
const DEFAULT_HEIGHT = { hero: 380, mini: 80 };

export default function BodyFigure({ view, values, plan, selected, onSelect, size, height }: BodyFigureProps) {
  const ids = useFigureIds();
  const mini = size === "mini";
  const svgHeight = height ?? DEFAULT_HEIGHT[size];
  const svgWidth = Math.round((svgHeight * VIEW_BOX.width) / VIEW_BOX.height);
  const label = `${view === "front" ? "Front" : "Back"} body figure`;
  // Mini figures are never tappable, even if a parent passes onSelect.
  const selectable = mini ? undefined : onSelect;
  const half = { view, values, plan, selected, onSelect: selectable, mini, ids };

  return (
    <svg
      viewBox={`${VIEW_BOX.x} ${VIEW_BOX.y} ${VIEW_BOX.width} ${VIEW_BOX.height}`}
      width={svgWidth}
      height={svgHeight}
      // A tappable figure is a group of buttons; a static one is a single image.
      role={selectable ? "group" : "img"}
      aria-label={label}
      className="block shrink-0"
    >
      <FigureDefs ids={ids} view={view} withFibers={!mini} />
      <Head shadeUrl={`url(#${ids.shade})`} />
      <FigureHalf {...half} mirrored={false} />
      {/* The right half: the same drawing flipped around x = 200. Hidden from screen readers so each region is announced once. */}
      <g transform="translate(400 0) scale(-1 1)" aria-hidden="true">
        <FigureHalf {...half} mirrored />
      </g>
    </svg>
  );
}

// SVG ids are global to the page, so every figure gets its own prefix (several figures can share a page).
function useFigureIds(): FigureIds {
  // useId can contain characters like ":" that are awkward inside url(#…), so keep only safe ones.
  const prefix = "fig" + useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const clip = Object.fromEntries(REGION_IDS.map((id) => [id, `${prefix}-clip-${id}`])) as Record<RegionId, string>;
  return { shade: `${prefix}-shade`, plan: `${prefix}-plan`, clip };
}

function FigureDefs({ ids, view, withFibers }: { ids: FigureIds; view: View; withFibers: boolean }) {
  return (
    <defs>
      {/* Light top-left, dark edges: gives each muscle a bit of volume. */}
      <radialGradient id={ids.shade} cx=".42" cy=".36" r=".78">
        <stop offset="0" stopColor="#fff" stopOpacity=".2" />
        <stop offset=".5" stopColor="#fff" stopOpacity="0" />
        <stop offset="1" stopColor="#000" stopOpacity=".45" />
      </radialGradient>
      {/* Diagonal stripes for today's plan. */}
      <pattern id={ids.plan} width="13" height="13" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
        <rect width="5" height="13" fill={FIGURE_COLORS.plan} />
      </pattern>
      {/* One clip per region, so its fiber lines stay inside its outline. */}
      {withFibers &&
        SHAPES[view].map(({ id, d }) => (
          <clipPath key={id} id={ids.clip[id]}>
            <path d={d} />
          </clipPath>
        ))}
    </defs>
  );
}

// The head sits on the centre line, so it is drawn once (not mirrored).
function Head({ shadeUrl }: { shadeUrl: string }) {
  return (
    <g>
      <ellipse cx="200" cy="66" rx="43" ry="55" fill={FIGURE_COLORS.underlay} />
      <ellipse cx="200" cy="66" rx="38" ry="50" fill={FIGURE_COLORS.base} stroke={FIGURE_COLORS.gap} strokeWidth="2.4" />
      <ellipse cx="200" cy="66" rx="38" ry="50" fill={shadeUrl} />
    </g>
  );
}
