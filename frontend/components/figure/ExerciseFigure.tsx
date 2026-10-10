// A small, static body figure for one exercise, shown from the side of its main region.
import BodyFigure from "./BodyFigure";
import { exerciseValues, viewOf } from "./exercise";

type ExerciseFigureProps = {
  primary: string[]; // region ids, main region first
  secondary: string[];
  height: number; // px
};

export default function ExerciseFigure({ primary, secondary, height }: ExerciseFigureProps) {
  const view = primary.length > 0 ? viewOf(primary[0]) : "front";
  return <BodyFigure view={view} values={exerciseValues(primary, secondary)} size="mini" height={height} />;
}
