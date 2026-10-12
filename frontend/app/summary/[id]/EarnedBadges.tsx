type EarnedBadgesProps = {
  groups: string[]; // muscle groups whose badge this workout completed, e.g. ["chest"]
};

// The summary's celebration: each newly earned badge in gold, with a short glow bursting
// around it (skipped for people who ask for reduced motion).
export default function EarnedBadges({ groups }: EarnedBadgesProps) {
  return (
    <section className="grid justify-items-center gap-3">
      <h2 className="text-[0.8rem] font-normal tracking-[0.12em] text-mut">
        {groups.length === 1 ? "Badge earned" : "Badges earned"}
      </h2>
      <ul className="flex flex-wrap justify-center gap-4">
        {groups.map((group) => (
          <li
            key={group}
            data-testid={`summary-badge-${group}`}
            className="grid min-h-14 min-w-28 place-items-center bg-acc px-4 font-display text-[1.1rem] text-on-acc motion-safe:animate-glow-burst"
          >
            {/* The group id ("chest") is already the group's name; the page shows it in capitals. */}
            {group}
          </li>
        ))}
      </ul>
    </section>
  );
}
