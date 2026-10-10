// A muted, secondary action (e.g. "Remove last set") that asks before it runs.
// While asking, the button is replaced by the in-page YES / NO bar.
import ConfirmBar from "../ConfirmBar";

type QuietActionProps = {
  label: string;
  testId: string;
  question: string;
  confirming: boolean; // the parent decides, so only one question is open at a time
  busy: boolean;
  onAsk: () => void;
  onNo: () => void;
  onYes: () => void;
};

export default function QuietAction({ label, testId, question, confirming, busy, onAsk, onNo, onYes }: QuietActionProps) {
  if (confirming) {
    return <ConfirmBar question={question} onYes={onYes} onNo={onNo} busy={busy} />;
  }
  return (
    <button
      type="button"
      onClick={onAsk}
      disabled={busy}
      data-testid={testId}
      className="min-h-12 text-[0.85rem] font-bold tracking-[0.1em] text-mut disabled:opacity-40"
    >
      {label}
    </button>
  );
}
