import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import Wordmark from "@/components/Wordmark";
import FigurePair from "@/components/figure/FigurePair";
import { getRegions, getSession, getSessionSummary, getSettings } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import { toRegionValues } from "@/lib/plan";
import { litLine, personalBestText, summaryTitle } from "@/lib/summary";
import EarnedBadges from "./EarnedBadges";

export const metadata: Metadata = { title: "Summary · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

type SummaryPageProps = {
  params: Promise<{ id: string }>;
};

// Shown right after FINISH: what the workout hit, what it lit up, any new badges and new bests.
export default async function SummaryPage({ params }: SummaryPageProps) {
  await guardPage("/");
  const { id } = await params;
  const [session, settings, catalog] = await Promise.all([getSession(id), getSettings(), getRegions()]);
  if (!session) {
    notFound();
  }
  // A workout still in progress has no summary yet.
  if (session.status === "ACTIVE") {
    redirect("/workout");
  }
  const summary = await getSessionSummary(id);
  const names = new Map(catalog.map((region) => [region.id, region.name]));

  return (
    <div className="grid gap-5">
      <Wordmark size="large" />
      <h1 data-testid="summary-title" className="text-center font-display text-[2rem] leading-[0.95]">
        {summaryTitle(summary.name)}
      </h1>
      <FigurePair values={toRegionValues(summary.regionsHit)} size="hero" height={300} testId="summary-figure" />
      {/* Varsity double-rule stripe. */}
      <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
      <p data-testid="summary-lit" className="text-center text-[1.05rem] font-bold">
        {litLine(summary.regionsLit, names)}
      </p>
      {summary.badgesEarned.length > 0 && <EarnedBadges groups={summary.badgesEarned} />}
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
      {/* A link styled as the gold pennant: DONE just goes Home. It presses down like the buttons. */}
      <Link
        href="/"
        data-testid="summary-done"
        className="flex min-h-[60px] w-full items-center justify-center bg-acc pr-[18px] font-display text-xl text-on-acc transition-transform duration-100 [clip-path:polygon(0_0,100%_0,calc(100%-18px)_50%,100%_100%,0_100%)] motion-safe:active:scale-[0.97]"
      >
        Done
      </Link>
    </div>
  );
}
