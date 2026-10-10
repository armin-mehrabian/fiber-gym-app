"use client"; // steppers, ticks and adds all happen in the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import { addSet, deleteSet, removeExercise, updateSet } from "@/lib/api";
import type { ApiError, ApiResult, SessionExercise, Units, WorkoutSet } from "@/lib/types";
import { toKg } from "@/lib/units";
import { checkAction, nextSetValues, removeDraft, setLabels, toDraft, type SetDraft } from "@/lib/workout";
import QuietAction from "./QuietAction";
import SetRow, { SET_GRID } from "./SetRow";

type SetLoggerProps = {
  sessionId: string;
  exercise: SessionExercise;
  units: Units;
  bodyweight: boolean; // no equipment needed: the first set starts at 0
};

// Which "are you sure?" is open, if any. Only one at a time, so there is only one YES on screen.
type Confirm = "last-set" | "exercise" | null;

export default function SetLogger({ sessionId, exercise, units, bodyweight }: SetLoggerProps) {
  const router = useRouter();
  const [drafts, setDrafts] = useState<SetDraft[]>(() => exercise.sets.map((set) => toDraft(set, units)));
  const [busy, setBusy] = useState(false);
  // The set a request is running for: its steppers wait, so the reply can't overwrite a newer edit.
  const [pendingSetId, setPendingSetId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [error, setError] = useState<ApiError | null>(null);

  // Runs one request at a time (so replies can't arrive out of order) and shows its error.
  async function run<T>(request: Promise<ApiResult<T>>, setId: string | null = null): Promise<ApiResult<T>> {
    setBusy(true);
    setPendingSetId(setId);
    setError(null);
    const result = await request;
    setBusy(false);
    setPendingSetId(null);
    if (!result.ok) {
      setError(result.error);
    }
    return result;
  }

  // Puts a reply from the server into its row. keepValues: leave the on-screen values alone
  // (after a warm-up toggle the user may still have unsaved stepper changes).
  function replaceSet(saved: WorkoutSet, keepValues: boolean) {
    setDrafts((current) =>
      current.map((draft) => {
        if (draft.set.id !== saved.id) return draft;
        return keepValues ? { ...draft, set: saved } : toDraft(saved, units);
      }),
    );
  }

  // Stepper taps only change the screen; ✓ saves them.
  function changeDraft(setId: string, values: { weight?: number; reps?: number }) {
    setDrafts((current) => current.map((draft) => (draft.set.id === setId ? { ...draft, ...values } : draft)));
  }

  async function handleCheck(draft: SetDraft) {
    const id = draft.set.id;
    if (checkAction(draft, units) === "undo") {
      const result = await run(updateSet(id, { completed: false }), id);
      if (result.ok) replaceSet(result.data, true);
      return;
    }
    const changes = { weightKg: toKg(draft.weight, units), reps: draft.reps, isWarmup: draft.set.isWarmup, completed: true };
    const result = await run(updateSet(id, changes), id);
    if (result.ok) replaceSet(result.data, false);
  }

  async function handleToggleWarmup(draft: SetDraft) {
    const id = draft.set.id;
    const result = await run(updateSet(id, { isWarmup: !draft.set.isWarmup }), id);
    if (result.ok) replaceSet(result.data, true);
  }

  async function handleAdd() {
    const values = nextSetValues(drafts, units, bodyweight, exercise.repRange);
    const result = await run(addSet(sessionId, exercise.position, values));
    if (result.ok) {
      const created = result.data;
      setDrafts((current) => [...current, toDraft(created, units)]);
    }
  }

  // Rows are in set order, so the last row is the set with the highest number.
  async function handleRemoveLastSet() {
    const last = drafts[drafts.length - 1];
    setConfirm(null);
    const result = await run(deleteSet(last.set.id), last.set.id);
    if (result.ok) setDrafts((current) => removeDraft(current, last.set.id));
  }

  async function handleRemoveExercise() {
    setConfirm(null);
    const result = await run(removeExercise(sessionId, exercise.position));
    if (result.ok) router.push("/workout");
  }

  const labels = setLabels(drafts.map((draft) => draft.set));

  return (
    <div className="grid gap-3">
      <div className={`${SET_GRID} text-[0.72rem] tracking-[0.12em] text-mut`}>
        <span className="text-center">Set</span>
        <span className="text-center">{units}</span>
        <span className="text-center">Reps</span>
      </div>
      <div className="grid">
        {drafts.map((draft, index) => (
          <SetRow
            key={draft.set.id}
            draft={draft}
            label={labels[index]}
            units={units}
            busy={busy}
            saving={pendingSetId === draft.set.id}
            onChange={(values) => changeDraft(draft.set.id, values)}
            onCheck={() => handleCheck(draft)}
            onToggleWarmup={() => handleToggleWarmup(draft)}
          />
        ))}
      </div>
      <button
        type="button"
        onClick={handleAdd}
        disabled={busy}
        data-testid="add-set"
        className="min-h-[52px] border-[1.5px] border-edge font-bold disabled:opacity-40"
      >
        + Set
      </button>
      {drafts.length > 0 && (
        <QuietAction
          label="Remove last set"
          testId="remove-last-set"
          question="Remove last set?"
          confirming={confirm === "last-set"}
          busy={busy}
          onAsk={() => setConfirm("last-set")}
          onNo={() => setConfirm(null)}
          onYes={handleRemoveLastSet}
        />
      )}
      <ErrorLine error={error} />
      <PennantButton onClick={() => router.push("/workout")} testId="log-done">
        Done
      </PennantButton>
      <QuietAction
        label="Remove exercise"
        testId="remove-exercise"
        question="Remove exercise?"
        confirming={confirm === "exercise"}
        busy={busy}
        onAsk={() => setConfirm("exercise")}
        onNo={() => setConfirm(null)}
        onYes={handleRemoveExercise}
      />
    </div>
  );
}
