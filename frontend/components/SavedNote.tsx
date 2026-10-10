// The short gold "Saved" confirmation shown after a successful save.
export default function SavedNote({ visible, testId }: { visible: boolean; testId: string }) {
  if (!visible) {
    return null;
  }
  return (
    // role="status" lets screen readers announce it without moving focus.
    <span role="status" data-testid={testId} className="text-sm font-bold tracking-[0.12em] text-acc">
      Saved
    </span>
  );
}
