import { guardPage } from "@/lib/guard";

// This page reads the visitor's cookies before showing anything, so it can't have an instant
// static shell. `instant = false` tells Next.js that's intended.
export const instant = false;

export default async function HistoryPage() {
  await guardPage("/");

  return <h1 className="font-display text-[2.2rem] leading-none">History</h1>;
}
