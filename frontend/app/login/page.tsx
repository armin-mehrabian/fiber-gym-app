import type { Metadata } from "next";
import BodyFigure from "@/components/figure/BodyFigure";
import type { RegionValues } from "@/components/figure/BodyFigure";
import { guardPage } from "@/lib/guard";
import AuthForm from "./AuthForm";

export const metadata: Metadata = { title: "Sign in · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

// Decorative coverage for the welcome figure.
const WELCOME_FIGURE: RegionValues = {
  quads_vasti: 0.9,
  quads_rectus: 0.8,
  chest_mid: 1,
  chest_upper: 0.4,
  delt_front: 0.7,
  abs_upper: 0.5,
  biceps_short: 0.6,
  calves: 0.3,
  obliques: 0.2,
  forearms: 0.3,
};

export default async function LoginPage() {
  // Already logged in? Go straight to onboarding or home.
  await guardPage("/login");

  return (
    <div className="flex min-h-full flex-col gap-4">
      {/* Varsity double-rule stripe. */}
      <div className="h-2.5 shrink-0 border-y-[3px] border-acc" />
      <div className="flex justify-center">
        <BodyFigure view="front" values={WELCOME_FIGURE} size="hero" height={220} />
      </div>
      <h1 className="text-center font-display text-[4rem] leading-none text-acc">Fiber</h1>
      <AuthForm />
    </div>
  );
}
