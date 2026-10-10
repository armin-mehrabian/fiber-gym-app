import type { Metadata } from "next";
import { getEquipment, getSettings } from "@/lib/api";
import { guardPage } from "@/lib/guard";
import SettingsForm from "./SettingsForm";

export const metadata: Metadata = { title: "Settings · Fiber" };

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function SettingsPage() {
  await guardPage("/");
  const [settings, equipment] = await Promise.all([getSettings(), getEquipment()]);

  return <SettingsForm initialSettings={settings} equipment={equipment} />;
}
