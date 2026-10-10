"use client"; // needs the current path to highlight the active tab

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/", label: "Home", testId: "nav-home" },
  { href: "/history", label: "History", testId: "nav-history" },
  { href: "/settings", label: "Settings", testId: "nav-settings" },
];

// Login and onboarding are full-screen steps before the app itself, so they get no tabs.
// The nav already knows the current path, so hiding it here is simpler than a second layout.
const HIDDEN_ON = ["/login", "/onboarding"];

// A workout in progress gets the whole screen too, on every /workout page.
function isHidden(pathname: string): boolean {
  return HIDDEN_ON.includes(pathname) || pathname === "/workout" || pathname.startsWith("/workout/");
}

// "/" would match every path with startsWith, so Home only matches exactly.
function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname.startsWith(href);
}

export default function BottomNav() {
  const pathname = usePathname();
  if (isHidden(pathname)) {
    return null;
  }

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
