import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import FigurePair from "@/components/figure/FigurePair";
import { getSession, getSessionSummary, getSettings } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import { toRegionValues } from "@/lib/plan";
import { personalBestText, summaryTitle, weekChangeText } from "@/lib/summary";

export const metadata: Metadata = { title: "Summary · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type SummaryPageProps = {
  params: Promise<{ id: string }>;
};

// Shown right after FINISH: what the workout hit, how the week moved, and any new bests.
export default async function SummaryPage({ params }: SummaryPageProps) {
  await guardPage("/");
  const { id } = await params;
  const [session, settings] = await Promise.all([getSession(id), getSettings()]);
  if (!session) {
    notFound();
  }
  // A workout still in progress has no summary yet.
  if (session.status === "ACTIVE") {
    redirect("/workout");
  }
  const summary = await getSessionSummary(id);

  return (
    <div className="grid gap-5">
      <h1 data-testid="summary-title" className="pt-2 font-display text-[2.4rem] leading-[0.95] text-acc">
        {summaryTitle(summary.name)}
      </h1>
      <FigurePair values={toRegionValues(summary.regionsHit)} size="hero" height={300} testId="summary-figure" />
      {/* Varsity double-rule stripe. */}
      <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
      <p data-testid="summary-week" className="text-center font-display text-[1.25rem] tabular-nums">
        {weekChangeText(summary.weekPercentBefore, summary.weekPercentAfter)}
      </p>
      {summary.personalBests.length > 0 && (
        <div className="grid">
          {summary.personalBests.map((best) => (
            <p
              key={best.exerciseId}
              data-testid={`summary-pr-${best.exerciseId}`}
              className="flex min-h-[52px] items-center gap-2.5 border-b border-line font-bold text-acc"
            >
              <span aria-hidden="true">★</span>
              {personalBestText(best, settings.units)}
            </p>
          ))}
        </div>
      )}
      {/* A link styled as the gold pennant: DONE just goes Home. */}
      <Link
        href="/"
        data-testid="summary-done"
        className="flex min-h-[60px] w-full items-center justify-center bg-acc pr-[18px] font-display text-xl text-on-acc [clip-path:polygon(0_0,100%_0,calc(100%-18px)_50%,100%_100%,0_100%)]"
      >
        Done
      </Link>
    </div>
  );
}
