// The day strip on the dashboard: one box per day, Monday to Sunday.
import type { CoverageDay } from "@/lib/types";

// The calendar date ("YYYY-MM-DD") of a moment for someone in the given timezone.
// Late on Sunday evening in Vancouver it's already Monday in UTC, so "today" must use the user's zone.
export function dateInZone(moment: Date, timeZone: string): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(moment);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")}`;
}

const LETTERS = ["M", "T", "W", "T", "F", "S", "S"];
const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

export type StripDay = {
  date: string; // "YYYY-MM-DD"
  letter: string; // "M" … "S"
  dayNumber: number; // day of the month
  label: string; // for screen readers, e.g. "Monday 5, trained"
  trained: boolean; // at least one finished workout started that day
  today: boolean;
};

// The API sends the 7 days Monday first, so the position gives the weekday.
// The date is read straight from the text (not via Date), so no timezone can shift it.
export function buildDayStrip(days: CoverageDay[], today: string): StripDay[] {
  return days.map((day, index) => {
    const dayNumber = Number(day.date.slice(8, 10));
    const trained = day.sessions > 0;
    return {
      date: day.date,
      letter: LETTERS[index],
      dayNumber,
      label: `${WEEKDAYS[index]} ${dayNumber}${trained ? ", trained" : ""}`,
      trained,
      today: day.date === today,
    };
  });
}
