import SessionRow from "@/components/SessionRow";
import type { HistorySession } from "@/lib/types";

type WeekSessionsProps = {
  sessions: HistorySession[]; // this week's finished workouts, newest first
  timeZone: string;
};

// This week's finished workouts, each opening its detail page.
export default function WeekSessions({ sessions, timeZone }: WeekSessionsProps) {
  return (
    <section className="grid gap-2">
      <h2 className="font-display text-[1.15rem] font-normal">This week</h2>
      {/* Varsity double-rule stripe. */}
      <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
      {sessions.length === 0 ? (
        <p data-testid="week-empty" className="text-sm tracking-[0.12em] text-mut">
          No workouts yet this week
        </p>
      ) : (
        <div className="grid">
          {sessions.map((session) => (
            <SessionRow key={session.id} session={session} timeZone={timeZone} testId={`week-session-${session.id}`} />
          ))}
        </div>
      )}
    </section>
  );
}
