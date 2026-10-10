"use client"; // ticks every second in the browser

import { useSyncExternalStore } from "react";
import { formatElapsed } from "@/lib/dates";

// A once-a-second "clock" that React can subscribe to.
function subscribe(onTick: () => void): () => void {
  const id = setInterval(onTick, 1000);
  return () => clearInterval(id);
}

// Whole seconds, so the value only changes once per second.
function currentSecond(): number {
  return Math.floor(Date.now() / 1000);
}

// The server's clock isn't the visitor's, so the server renders no time at all (null) and the
// browser fills it in right after loading. That avoids a mismatch between the two renders.
function noTimeOnServer(): null {
  return null;
}

// Time since the workout started, e.g. "12:41".
export default function Timer({ startedAt }: { startedAt: string }) {
  const nowSecond = useSyncExternalStore(subscribe, currentSecond, noTimeOnServer);
  const text = nowSecond === null ? "" : formatElapsed(nowSecond * 1000 - Date.parse(startedAt));

  return (
    <span data-testid="workout-timer" className="font-display text-[1.2rem] tabular-nums text-acc">
      {text}
    </span>
  );
}
