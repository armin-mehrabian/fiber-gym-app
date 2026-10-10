import type { Metadata } from "next";
import { Graduate, Saira_Condensed } from "next/font/google";
import BottomNav from "@/components/BottomNav";
import "./globals.css";

// Fonts are downloaded at build time and served by Next.js itself (no external CDN at runtime).
const graduate = Graduate({
  weight: "400",
  subsets: ["latin"],
  variable: "--font-graduate",
});

const sairaCondensed = Saira_Condensed({
  weight: ["400", "500", "600", "700"],
  subsets: ["latin"],
  variable: "--font-saira-condensed",
});

export const metadata: Metadata = {
  title: "Fiber",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${graduate.variable} ${sairaCondensed.variable}`}>
      <body>
        {/* Phone-width column: full screen on a phone, centred on desktop. */}
        <div className="mx-auto flex h-dvh max-w-[430px] flex-col">
          {/* Only the content scrolls, so the nav stays pinned at the bottom. */}
          <main className="flex-1 overflow-y-auto px-5 py-6">{children}</main>
          <BottomNav />
        </div>
      </body>
    </html>
  );
}
