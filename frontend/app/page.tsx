import { getActiveSession, getHistory, getRecommendation, getRegions, getSettings, getWeekCoverage } from "@/lib/api";
import { formatWeekOf } from "@/lib/dates";
import { guardPage } from "@/lib/guard";
import { buildDayStrip, dateInZone } from "@/lib/week";
import DayStrip from "./DayStrip";
import NextUp from "./NextUp";
import StartWorkoutButton from "./StartWorkoutButton";
import WeekFigure from "./WeekFigure";
import WeekSessions from "./WeekSessions";

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function HomePage() {
  await guardPage("/");
  const [coverage, catalog, settings, history, active] = await Promise.all([
    getWeekCoverage(),
    getRegions(),
    getSettings(), // for the user's timezone
    getHistory(1), // the current week's workouts, with names and set counts for the list
    getActiveSession(),
  ]);

  // The next workout only matters when none is in progress (then Home shows RESUME alone).
  const recommendation = active ? null : await getRecommendation();

  // "Today" on the user's own calendar, not the server's.
  const today = dateInZone(new Date(), settings.timezone);
  // Matching on weekStart makes sure the list shows the same week as the figure.
  const weekSessions = history.find((week) => week.weekStart === coverage.weekStart)?.sessions ?? [];

  return (
    <div className="grid gap-6">
      <header className="flex items-end justify-between gap-3">
        <h1 className="font-display text-[1.4rem] leading-tight">{formatWeekOf(coverage.weekStart)}</h1>
        <p className="grid justify-items-end">
          <span className="font-display text-[1.6rem] leading-none text-acc">
            <span data-testid="week-percent">{coverage.weekPercent}</span>%
          </span>
          <span className="text-[0.7rem] tracking-[0.12em] text-mut">Covered</span>
        </p>
      </header>
      <DayStrip days={buildDayStrip(coverage.days, today)} />
      <WeekFigure coverage={coverage.regions} catalog={catalog} />
      {recommendation && <NextUp recommendation={recommendation} />}
      <StartWorkoutButton hasActive={active !== null} />
      <WeekSessions sessions={weekSessions} timeZone={settings.timezone} />
    </div>
  );
}

