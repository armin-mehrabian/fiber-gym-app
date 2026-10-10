// One set on the log screen: [set no. / W] [weight −+] [reps −+] [✓].
import Stepper from "@/components/Stepper";
import type { Units } from "@/lib/types";
import { WEIGHT_STEP, formatWeight, maxWeight } from "@/lib/units";
import { checkAction, type SetDraft } from "@/lib/workout";

type SetRowProps = {
  draft: SetDraft;
  label: string; // "W" for a warm-up, otherwise the working-set number
  units: Units;
  busy: boolean; // any request is running: the saving buttons wait
  saving: boolean; // a request for THIS set is running: its steppers wait too
  onChange: (values: { weight?: number; reps?: number }) => void;
  onCheck: () => void;
  onToggleWarmup: () => void;
};

const MAX_REPS = 100; // the API accepts 0–100

// Shared with the header row on the log screen, so the columns line up.
// Weight gets a bit more room than reps (5:4): its longest value, "2204.6", is about twice as wide as "100".
export const SET_GRID = "grid grid-cols-[44px_minmax(0,5fr)_minmax(0,4fr)_44px] items-center";

export default function SetRow({ draft, label, units, busy, saving, onChange, onCheck, onToggleWarmup }: SetRowProps) {
  const n = draft.set.setNumber;
  const action = checkAction(draft, units);
  // "Done" look only when the saved, completed values are what's on screen.
  const done = action === "undo";

  return (
    <div data-testid={`set-row-${n}`} className={`${SET_GRID} min-h-[60px] border-b border-line ${done ? "text-acc" : ""}`}>
      <button
        type="button"
        onClick={onToggleWarmup}
        disabled={busy}
        aria-label={`Warm-up: set ${n}`}
        aria-pressed={draft.set.isWarmup}
        data-testid={`set-warmup-${n}`}
        className={`grid size-11 place-items-center font-display text-[1.05rem] ${draft.set.isWarmup ? "text-mut" : ""}`}
      >
        {label}
      </button>
      <Stepper
        value={draft.weight}
        min={0}
        max={maxWeight(units)}
        step={WEIGHT_STEP[units]}
        format={formatWeight}
        onChange={(weight) => onChange({ weight })}
        label={`set ${n}`}
        actionWords={{ dec: "Less weight", inc: "More weight" }}
        size="compact"
        disabled={saving}
        testIds={{ dec: `set-weight-dec-${n}`, value: `set-weight-${n}`, inc: `set-weight-inc-${n}` }}
      />
      <Stepper
        value={draft.reps}
        min={0}
        max={MAX_REPS}
        onChange={(reps) => onChange({ reps })}
        label={`set ${n}`}
        actionWords={{ dec: "Fewer reps", inc: "More reps" }}
        size="compact"
        disabled={saving}
        testIds={{ dec: `set-reps-dec-${n}`, value: `set-reps-${n}`, inc: `set-reps-inc-${n}` }}
      />
      <button
        type="button"
        onClick={onCheck}
        disabled={busy}
        aria-label={done ? `Undo set ${n}` : `Save set ${n}`}
        aria-pressed={done}
        data-testid={`set-done-${n}`}
        className={`grid size-11 place-items-center border-[1.5px] text-lg ${
          done ? "border-acc bg-acc text-on-acc" : "border-edge text-mut"
        }`}
      >
        ✓
      </button>
    </div>
  );
}
