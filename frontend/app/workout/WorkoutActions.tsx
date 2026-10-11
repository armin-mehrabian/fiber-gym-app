"use client"; // finish and discard are requests from the browser, with an in-page confirm

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import { discardSession, finishSession } from "@/lib/api";
import type { ApiError } from "@/lib/types";
import ConfirmBar from "./ConfirmBar";

type WorkoutActionsProps = {
  sessionId: string;
  hasCompletedSet: boolean;
};

// Which question is on screen, if any.
type Confirm = "finish" | "discard" | null;

export default function WorkoutActions({ sessionId, hasCompletedSet }: WorkoutActionsProps) {
  const router = useRouter();
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function finish() {
    setBusy(true);
    setError(null);
    const result = await finishSession(sessionId);
    if (!result.ok) {
      setBusy(false);
      setError(result.error);
      return;
    }
    router.push(`/summary/${sessionId}`);
  }

  async function discard() {
    setBusy(true);
    setError(null);
    const result = await discardSession(sessionId);
    if (!result.ok) {
      setBusy(false);
      setError(result.error);
      return;
    }
    router.push("/");
  }

  // Finishing with nothing ticked off is probably a slip, so only then ask first.
  function handleFinish() {
    if (hasCompletedSet) {
      finish();
    } else {
      setConfirm("finish");
    }
  }

  return (
    <div className="grid gap-3">
      <Link
        href="/workout/add"
        data-testid="add-exercise"
        className="flex min-h-[52px] items-center justify-center border-[1.5px] border-edge font-bold"
      >
        + Add exercise
      </Link>
      <ErrorLine error={error} />
      {confirm === "finish" && (
        <ConfirmBar question="No sets done. Finish?" onYes={finish} onNo={() => setConfirm(null)} busy={busy} />
      )}
      {confirm === "discard" && (
        <ConfirmBar question="Discard workout?" onYes={discard} onNo={() => setConfirm(null)} busy={busy} />
      )}
      {confirm === null && (
        <>
          <PennantButton onClick={handleFinish} disabled={busy} testId="finish-workout">
            Finish
          </PennantButton>
          <button
            type="button"
            onClick={() => setConfirm("discard")}
            disabled={busy}
            data-testid="discard-workout"
            className="min-h-12 text-[0.85rem] font-bold tracking-[0.1em] text-mut"
          >
            Discard
          </button>
        </>
      )}
    </div>
  );
}
