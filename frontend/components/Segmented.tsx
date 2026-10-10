// A small two-or-more-way switch (e.g. KG / LB). The active segment is filled gold.
type SegmentedProps<T extends string> = {
  options: readonly T[];
  value: T;
  onChange: (value: T) => void;
  testIdPrefix: string; // each segment gets `${testIdPrefix}${option}`
  label: string; // read by screen readers, since the options alone don't say what they choose
  disabled?: boolean;
};

export default function Segmented<T extends string>({
  options,
  value,
  onChange,
  testIdPrefix,
  label,
  disabled,
}: SegmentedProps<T>) {
  return (
    <div role="group" aria-label={label} className="inline-flex gap-0.5 border-[1.5px] border-edge p-[3px]">
      {options.map((option) => {
        const active = option === value;
        return (
          <button
            key={option}
            type="button"
            onClick={() => onChange(option)}
            disabled={disabled}
            aria-pressed={active}
            data-testid={`${testIdPrefix}${option}`}
            className={`min-h-11 min-w-[62px] px-3 text-[0.85rem] font-bold ${
              active ? "bg-acc text-on-acc" : "text-mut"
            }`}
          >
            {option}
          </button>
        );
      })}
    </div>
  );
}
