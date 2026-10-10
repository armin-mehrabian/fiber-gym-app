import type { Metadata } from "next";
import { getEquipment, getSettings } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import OnboardingFlow from "./OnboardingFlow";

export const metadata: Metadata = { title: "Setup · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function OnboardingPage() {
  // Only for logged-in users who haven't finished setup yet.
  await guardPage("/onboarding");
  const [settings, equipment] = await Promise.all([getSettings(), getEquipment()]);

  return <OnboardingFlow settings={settings} equipment={equipment} />;
}
