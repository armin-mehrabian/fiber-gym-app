"use client"; // edits stay in the browser until the one Save button

import { useState } from "react";
import Link from "next/link";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import SavedNote from "@/components/SavedNote";
import Stepper from "@/components/Stepper";
import { saveTargets } from "@/lib/api";
import {
  MAX_WEEKLY_SETS,
  MIN_WEEKLY_SETS,
  changedTargets,
  groupRegions,
  toTargetValues,
  type TargetValues,
} from "@/lib/targets";
import type { ApiError, Region, Target } from "@/lib/types";
import { useFlash } from "@/lib/useFlash";

type TargetsFormProps = {
  initialTargets: Target[];
  regions: Region[]; // in region order
};

export default function TargetsForm({ initialTargets, regions }: TargetsFormProps) {
  const [saved, setSaved] = useState<TargetValues>(() => toTargetValues(initialTargets));
  const [edited, setEdited] = useState<TargetValues>(saved);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [savedVisible, flashSaved] = useFlash();

  const changes = changedTargets(saved, edited);

  async function handleSave() {
    setBusy(true);
    setError(null);
    const result = await saveTargets(changes);
    setBusy(false);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    // The reply is the full saved list: it becomes the new starting point.
    const values = toTargetValues(result.data);
    setSaved(values);
    setEdited(values);
    flashSaved();
  }

  return (
    <div className="grid gap-4">
      <header className="flex min-h-11 items-center gap-2">
        <Link href="/settings" aria-label="Back to settings" className="grid size-11 shrink-0 place-items-center text-2xl">
          ‹
        </Link>
        <h1 className="flex-1 font-display text-[1.6rem] leading-none">Weekly targets</h1>
        <SavedNote visible={savedVisible} testId="targets-saved" />
      </header>

      {groupRegions(regions).map(({ group, regions: groupRegionList }) => (
        <section key={group}>
          <h2 className="pt-2.5 font-display text-[0.95rem] font-normal text-mut">{group}</h2>
          {groupRegionList.map((region) => (
            <div key={region.id} data-testid={`target-row-${region.id}`} className="border-b border-line">
              <div className="grid min-h-[50px] grid-cols-[1fr_132px] items-center text-[0.95rem]">
                <span>{region.name}</span>
                <Stepper
                  value={edited[region.id]}
                  min={MIN_WEEKLY_SETS}
                  max={MAX_WEEKLY_SETS}
                  onChange={(value) => setEdited({ ...edited, [region.id]: value })}
                  label={region.name}
                  testIds={{
                    dec: `target-dec-${region.id}`,
                    value: `target-value-${region.id}`,
                    inc: `target-inc-${region.id}`,
                  }}
                />
              </div>
              {/* Target errors from the API are keyed by region id. */}
              {error?.fields[region.id] && (
                <p className="pb-2 text-xs tracking-[0.1em] text-acc">{error.fields[region.id]}</p>
              )}
            </div>
          ))}
        </section>
      ))}

      {/* Stays at the bottom of the screen while the list scrolls under it. */}
      <div className="sticky bottom-0 grid gap-2 bg-bg py-3">
        <ErrorLine error={error} shownFields={regions.map((region) => region.id)} />
        <PennantButton onClick={handleSave} disabled={busy || changes.length === 0} testId="targets-save">
          Save
        </PennantButton>
      </div>
    </div>
  );
}
