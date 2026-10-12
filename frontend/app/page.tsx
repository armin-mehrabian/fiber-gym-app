import Wordmark from "@/components/Wordmark";
import { getActiveSession, getHistory, getRecommendation, getRegions, getSettings, getWeekCoverage } from "@/lib/api";
import { fillsLine } from "@/lib/coverage";
import { guardPage } from "@/lib/guard";
import { toRegionValues } from "@/lib/plan";
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
      <Wordmark size="large" heading />
      <div className="grid gap-2">
        {recommendation && (
          <NextUp recommendation={recommendation} fills={fillsLine(recommendation.planned, catalog)} />
        )}
        <StartWorkoutButton hasActive={active !== null} />
      </div>
      {/* The planned regions pulse on the figure only while NEXT UP is on screen. */}
      <WeekFigure
        coverage={coverage.regions}
        badges={coverage.badges}
        catalog={catalog}
        plan={recommendation ? toRegionValues(recommendation.planned) : undefined}
      />
      <DayStrip days={buildDayStrip(coverage.days, today)} />
      <WeekSessions sessions={weekSessions} timeZone={settings.timezone} />
    </div>
  );
}
