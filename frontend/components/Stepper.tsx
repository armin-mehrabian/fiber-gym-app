// − value + control for a whole number between min and max.
// The buttons simply stop at the limits (disabled), so the value can never leave the range.
type StepperProps = {
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  label: string; // what is being counted, for screen readers (e.g. "Upper chest")
  testIds: { dec: string; value: string; inc: string };
};

export default function Stepper({ value, min, max, onChange, label, testIds }: StepperProps) {
  return (
    <div className="grid grid-cols-[44px_1fr_44px] items-center">
      <button
        type="button"
        onClick={() => onChange(value - 1)}
        disabled={value <= min}
        aria-label={`Fewer sets: ${label}`}
        data-testid={testIds.dec}
        className="grid h-11 place-items-center text-xl text-mut disabled:opacity-30"
      >
        −
      </button>
      <b data-testid={testIds.value} className="text-center font-display text-[1.2rem] font-normal tabular-nums">
        {value}
      </b>
      <button
        type="button"
        onClick={() => onChange(value + 1)}
        disabled={value >= max}
        aria-label={`More sets: ${label}`}
        data-testid={testIds.inc}
        className="grid h-11 place-items-center text-xl text-mut disabled:opacity-30"
      >
        +
      </button>
    </div>
  );
}
