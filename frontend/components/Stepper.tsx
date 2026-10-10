// − value + control for a number between min and max.
// The buttons simply stop at the limits (disabled), so the value can never leave the range.
import { stepValue } from "@/lib/units";

type StepperProps = {
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  label: string; // what is being changed, for screen readers (e.g. "Upper chest")
  testIds: { dec: string; value: string; inc: string };
  step?: number; // per tap; 1 by default
  format?: (value: number) => string; // how the value is shown; plain number by default
  actionWords: { dec: string; inc: string }; // start of the button names, e.g. "Less weight"
  // compact: narrower buttons (still 44px tall), for rows with two steppers side by side on a phone.
  // 36px wide on a 360px phone, 42px from 390px up; the value text is smaller so "2204.6" still fits.
  size?: "regular" | "compact";
  disabled?: boolean; // locks both buttons, e.g. while this value is being saved
};

const SIZES = {
  regular: { grid: "grid-cols-[44px_1fr_44px]", value: "text-[1.2rem]" },
  compact: {
    grid: "grid-cols-[36px_minmax(0,1fr)_36px] min-[390px]:grid-cols-[42px_minmax(0,1fr)_42px]",
    value: "text-[0.875rem]",
  },
};

export default function Stepper({
  value,
  min,
  max,
  onChange,
  label,
  testIds,
  step = 1,
  format = String,
  actionWords,
  size = "regular",
  disabled = false,
}: StepperProps) {
  const sizing = SIZES[size];
  return (
    <div className={`grid items-center ${sizing.grid}`}>
      <button
        type="button"
        onClick={() => onChange(stepValue(value, -step, min, max))}
        disabled={disabled || value <= min}
        aria-label={`${actionWords.dec}: ${label}`}
        data-testid={testIds.dec}
        className="grid h-11 place-items-center text-xl text-mut disabled:opacity-30"
      >
        −
      </button>
      <b
        data-testid={testIds.value}
        className={`text-center font-display font-normal tabular-nums ${sizing.value}`}
      >
        {format(value)}
      </b>
      <button
        type="button"
        onClick={() => onChange(stepValue(value, step, min, max))}
        disabled={disabled || value >= max}
        aria-label={`${actionWords.inc}: ${label}`}
        data-testid={testIds.inc}
        className="grid h-11 place-items-center text-xl text-mut disabled:opacity-30"
      >
        +
      </button>
    </div>
  );
}
