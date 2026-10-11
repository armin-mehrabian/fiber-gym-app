import type { Metadata } from "next";
import SessionRow from "@/components/SessionRow";
import { getHistory, getSettings } from "@/lib/api";
import { formatWeekOf } from "@/lib/dates";
import { guardPage } from "@/lib/guard";

export const metadata: Metadata = { title: "History · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function HistoryPage() {
  await guardPage("/");
  // The timezone is needed to show each workout on the day the user did it.
  const [weeks, settings] = await Promise.all([getHistory(), getSettings()]);

  return (
    <div className="grid gap-6">
      <h1 className="font-display text-[2.2rem] leading-none">History</h1>
      {weeks.length === 0 && (
        <p data-testid="history-empty" className="text-sm tracking-[0.12em] text-mut">
          No workouts yet
        </p>
      )}
      {weeks.map((week) => (
        <section key={week.weekStart} data-testid={`history-week-${week.weekStart}`} className="grid gap-2">
          <h2 className="font-display text-[1.15rem] font-normal">{formatWeekOf(week.weekStart)}</h2>
          {/* Varsity double-rule stripe. */}
          <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
          <div className="grid">
            {week.sessions.map((session) => (
              <SessionRow
                key={session.id}
                session={session}
                timeZone={settings.timezone}
                testId={`history-session-${session.id}`}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
