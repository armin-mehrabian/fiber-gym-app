"use client"; // needs the current path to highlight the active tab

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/", label: "Home", testId: "nav-home" },
  { href: "/history", label: "History", testId: "nav-history" },
  { href: "/settings", label: "Settings", testId: "nav-settings" },
];

// "/" would match every path with startsWith, so Home only matches exactly.
function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname.startsWith(href);
}

export default function BottomNav() {
  const pathname = usePathname();

  return (
    <nav className="grid grid-cols-3 border-t-[3px] border-double border-acc">
      {NAV_ITEMS.map((item) => {
        const active = isActive(pathname, item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            data-testid={item.testId}
            aria-current={active ? "page" : undefined}
            className={`grid min-h-[62px] place-items-center text-sm font-bold tracking-[0.12em] ${
              active ? "text-acc" : "text-mut"
            }`}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
