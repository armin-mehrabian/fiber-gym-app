// Front and back side by side, so a whole plan or workout is visible without a toggle.
import BodyFigure, { type RegionValues } from "./BodyFigure";

type FigurePairProps = {
  values: RegionValues;
  plan?: RegionValues; // striped overlay (today's plan)
  size: "hero" | "mini";
  height: number; // px, per figure
  testId?: string;
};

export default function FigurePair({ values, plan, size, height, testId }: FigurePairProps) {
  return (
    <div data-testid={testId} className="flex shrink-0 justify-center">
      <BodyFigure view="front" values={values} plan={plan} size={size} height={height} />
      <BodyFigure view="back" values={values} plan={plan} size={size} height={height} />
    </div>
  );
}
