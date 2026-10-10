// The "‹" in the top-left corner of sub-screens.
import Link from "next/link";

type BackLinkProps = {
  href: string;
  label: string; // for screen readers, e.g. "Back to workout"
  testId?: string;
};

export default function BackLink({ href, label, testId }: BackLinkProps) {
  return (
    <Link
      href={href}
      aria-label={label}
      data-testid={testId}
      className="grid size-11 shrink-0 place-items-center text-2xl"
    >
      ‹
    </Link>
  );
}
