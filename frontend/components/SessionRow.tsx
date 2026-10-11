import Link from "next/link";
import { formatSessionDay } from "@/lib/dates";
import type { HistorySession } from "@/lib/types";

type SessionRowProps = {
  session: HistorySession;
  timeZone: string; // the user's, so the workout shows on the day they did it
  testId: string;
};

// One finished workout (day, name, working sets) that opens its detail page.
// Shared by History and the dashboard's "This week" list so both rows line up the same way.
export default function SessionRow({ session, timeZone, testId }: SessionRowProps) {
  return (
    <Link
      href={`/history/${session.id}`}
      data-testid={testId}
      className="grid min-h-[58px] grid-cols-[4.2em_1fr_auto] items-center gap-2.5 border-b border-line"
    >
      <span className="text-[0.85rem] text-mut">{formatSessionDay(session.startedAt, timeZone)}</span>
      <b className="text-[1.05rem] font-bold">{session.name}</b>
      <span className="tabular-nums text-mut">
        {session.workingSets} {session.workingSets === 1 ? "set" : "sets"}
      </span>
    </Link>
  );
}
