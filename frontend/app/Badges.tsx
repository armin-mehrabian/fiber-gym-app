import { badgeHint } from "@/lib/coverage";
import type { Badge } from "@/lib/types";

// One badge per muscle group. Done = every part of the group reached its weekly target: filled gold,
// with a small pop when it appears. Not done = an outline with a "2/3 parts" hint.
export default function Badges({ badges }: { badges: Badge[] }) {
  return (
    <ul aria-label="Badges this week" className="grid grid-cols-3 gap-2">
      {badges.map((badge) => (
        <li
          key={badge.group}
          data-testid={`badge-${badge.group}`}
          data-done={String(badge.done)}
          className={`grid min-h-14 content-center justify-items-center gap-0.5 border-[1.5px] px-1 py-1.5 text-center ${
            badge.done ? "border-acc bg-acc text-on-acc motion-safe:animate-pop" : "border-edge text-mut"
          }`}
        >
          <b className="text-[0.95rem] leading-tight font-bold">{badge.name}</b>
          {badge.done ? (
            <span className="sr-only">done</span>
          ) : (
            <span className="text-[0.7rem] tracking-[0.1em]">{badgeHint(badge)}</span>
          )}
        </li>
      ))}
    </ul>
  );
}
