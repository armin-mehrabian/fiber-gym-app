// An in-page "are you sure?" with YES / NO, shown in place of the buttons it guards.
// Simpler than a pop-up dialog and keeps both answers in thumb reach.
type ConfirmBarProps = {
  question: string;
  onYes: () => void;
  onNo: () => void;
  busy: boolean; // while the YES request runs, both answers are locked
};

export default function ConfirmBar({ question, onYes, onNo, busy }: ConfirmBarProps) {
  return (
    <div role="alert" className="grid gap-3 border-y-[3px] border-double border-acc py-4">
      <p className="text-center font-display text-[1.1rem] text-acc">{question}</p>
      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={onNo}
          disabled={busy}
          data-testid="confirm-no"
          className="min-h-[52px] border-[1.5px] border-edge font-bold disabled:opacity-40"
        >
          No
        </button>
        <button
          type="button"
          onClick={onYes}
          disabled={busy}
          data-testid="confirm-yes"
          className="min-h-[52px] bg-acc font-bold text-on-acc disabled:opacity-40"
        >
          Yes
        </button>
      </div>
    </div>
  );
}
