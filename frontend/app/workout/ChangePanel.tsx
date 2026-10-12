"use client"; // the steppers are screen state until SAVE

import { useState } from "react";
import Stepper from "@/components/Stepper";
import type { Units } from "@/lib/types";
import { WEIGHT_STEP, formatWeight, maxWeight } from "@/lib/units";
import type { PlannedValues } from "@/lib/workout";

type ChangePanelProps = {
  name: string; // the exercise, for screen reader labels
  initial: PlannedValues; // the row's plan, weight in the user's units
  units: Units;
  busy: boolean; // while the save runs, everything waits
  onSave: (values: PlannedValues) => void;
  onCancel: () => void;
};

// Opens under a workout row when today's numbers differ from the plan: pick weight, reps and
// sets, then SAVE logs them all at once (like ✓ DONE, just with these values).
export default function ChangePanel({ name, initial, units, busy, onSave, onCancel }: ChangePanelProps) {
  const [values, setValues] = useState(initial);

  function change(part: Partial<PlannedValues>) {
    setValues((current) => ({ ...current, ...part }));
  }

  return (
    <div className="grid gap-2 border-y-[3px] border-double border-acc py-2">
      <Row label={units.toLowerCase()}>
        <Stepper
          value={values.weight}
          min={0}
          max={maxWeight(units)}
          step={WEIGHT_STEP[units]}
          format={formatWeight}
          onChange={(weight) => change({ weight })}
          label={name}
          actionWords={{ dec: "Less weight", inc: "More weight" }}
          testIds={{ dec: "change-weight-dec", value: "change-weight", inc: "change-weight-inc" }}
          disabled={busy}
        />
      </Row>
      <Row label="Reps">
        <Stepper
          value={values.reps}
          min={1}
          max={100}
          onChange={(reps) => change({ reps })}
          label={name}
          actionWords={{ dec: "Fewer reps", inc: "More reps" }}
          testIds={{ dec: "change-reps-dec", value: "change-reps", inc: "change-reps-inc" }}
          disabled={busy}
        />
      </Row>
      {/* The server accepts 1–10 sets per tap. */}
      <Row label="Sets">
        <Stepper
          value={values.sets}
          min={1}
          max={10}
          onChange={(sets) => change({ sets })}
          label={name}
          actionWords={{ dec: "Fewer sets", inc: "More sets" }}
          testIds={{ dec: "change-sets-dec", value: "change-sets", inc: "change-sets-inc" }}
          disabled={busy}
        />
      </Row>
      <div className="grid grid-cols-2 gap-2 pt-1">
        <button
          type="button"
          onClick={onCancel}
          disabled={busy}
          data-testid="change-cancel"
          className="min-h-[52px] border-[1.5px] border-edge font-bold disabled:opacity-40"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => onSave(values)}
          disabled={busy}
          data-testid="change-save"
          className="min-h-[52px] bg-acc font-bold text-on-acc disabled:opacity-40"
        >
          Save
        </button>
      </div>
    </div>
  );
}

// One line of the panel: a label, then its stepper. All three share the same columns.
function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[4em_1fr] items-center gap-2">
      <span className="text-[0.8rem] tracking-[0.12em] text-mut">{label}</span>
      {children}
    </div>
  );
}
