"use client"; // error boundaries must be client components

// Shown when a page fails to load, most often because the backend can't be reached.
// retry() fetches the page from the server again (reset() would only re-render what already failed).
export default function ErrorPage({ retry }: { retry: () => void }) {
  return (
    <div className="grid min-h-full place-content-center justify-items-center gap-5 text-center">
      <p className="font-display text-[1.6rem] leading-tight text-acc">Can&apos;t reach the server</p>
      <button
        type="button"
        onClick={() => retry()}
        className="min-h-[52px] border-[1.5px] border-edge px-8 font-bold"
      >
        Try again
      </button>
    </div>
  );
}
