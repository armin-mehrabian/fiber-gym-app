// The FIBER name in big gold varsity letters: the app's identity on Login, Home and Summary.
// Graduate letters are wide, so the size follows the screen width (vw) and stops at a maximum;
// that way the word never runs off a 360px phone.
type WordmarkProps = {
  size: "large" | "huge"; // huge = the login screen, where nothing else competes with it
  heading?: boolean; // true when the wordmark is the page's main heading (no other h1 on the page)
};

const SIZES = {
  large: "text-[clamp(3rem,16vw,4.5rem)]",
  huge: "text-[clamp(3.5rem,19vw,5.25rem)]",
};

export default function Wordmark({ size, heading = false }: WordmarkProps) {
  const className = `text-center font-display leading-none text-acc ${SIZES[size]}`;
  return heading ? (
    <h1 data-testid="wordmark" className={className}>
      Fiber
    </h1>
  ) : (
    <p data-testid="wordmark" className={className}>
      Fiber
    </p>
  );
}
