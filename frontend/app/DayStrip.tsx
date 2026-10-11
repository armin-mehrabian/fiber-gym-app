import type { StripDay } from "@/lib/week";

// Gold = trained that day, cream outline = today (both can apply at once).
function boxClass(day: StripDay): string {
  const border = day.today ? "border-fg" : day.trained ? "border-acc" : "border-line";
  const fill = day.trained ? "bg-acc text-on-acc" : day.today ? "text-fg" : "text-mut";
  return `${border} ${fill}`;
}

// Seven boxes, Monday to Sunday, like jerseys on a rack.
export default function DayStrip({ days }: { days: StripDay[] }) {
  return (
    <ol aria-label="Days this week" className="grid grid-cols-7 gap-1">
      {days.map((day) => (
        <li
          key={day.date}
          data-testid={`day-${day.date}`}
          data-trained={String(day.trained)}
          aria-current={day.today ? "date" : undefined}
          className={`grid justify-items-center gap-0.5 border-[1.5px] pt-1.5 pb-2 ${boxClass(day)}`}
        >
          {/* The letter and number are for the eye; screen readers get the full label instead. */}
          <span aria-hidden="true" className="text-[0.7rem] tracking-[0.1em]">
            {day.letter}
          </span>
          <b aria-hidden="true" className="font-display text-[1.15rem] font-normal">
            {day.dayNumber}
          </b>
          <span className="sr-only">{day.label}</span>
        </li>
      ))}
    </ol>
  );
}
