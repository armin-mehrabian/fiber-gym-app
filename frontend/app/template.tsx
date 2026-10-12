// Unlike the layout, a template is created fresh on every navigation, so this short fade/slide-in
// plays each time a new screen opens. Skipped for people who ask for reduced motion.
// The wrapper is at least as tall as the screen and grows with long pages (they still scroll in the
// layout). Being a one-column grid lets a page use min-h-full to fill the screen, which some screens
// need to push a button to the bottom or to centre their content.
export default function Template({ children }: { children: React.ReactNode }) {
  return <div className="grid min-h-full grid-cols-1 motion-safe:animate-page-in">{children}</div>;
}
