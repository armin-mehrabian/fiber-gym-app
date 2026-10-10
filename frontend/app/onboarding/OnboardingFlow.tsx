"use client"; // a three-step wizard: the current step and the picks live in the browser until the end

import { useState } from "react";
import { useRouter } from "next/navigation";
import EquipmentTiles from "@/components/EquipmentTiles";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import Tile from "@/components/Tile";
import { saveSettings } from "@/lib/api";
import { SPLIT_CHOICES, UNIT_CHOICES, toggleId } from "@/lib/choices";
import type { ApiError, Equipment, Settings, SplitType, Units } from "@/lib/types";

const STEPS = 3;
const TITLES = ["Your split", "Equipment", "Units"];

type OnboardingFlowProps = {
  settings: Settings; // the defaults made at signup (units, timezone)
  equipment: Equipment[] | null; // null = the list couldn't be loaded
};

export default function OnboardingFlow({ settings, equipment }: OnboardingFlowProps) {
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [splitType, setSplitType] = useState<SplitType>(settings.splitType ?? "PPL");
  // Everything is ticked at first: most gyms have it all, so removing is fewer taps than adding.
  const [selectedEquipment, setSelectedEquipment] = useState<string[]>(
    equipment ? equipment.map((item) => item.id) : settings.equipment,
  );
  const [units, setUnits] = useState<Units>(settings.units);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  // Nothing is saved until the last step, so going back and forth is free.
  async function finish() {
    setBusy(true);
    setError(null);
    const result = await saveSettings({
      splitType,
      units,
      timezone: settings.timezone, // keep the zone stored at signup
      equipment: selectedEquipment,
    });
    setBusy(false);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    router.replace("/");
  }

  return (
    <div className="flex min-h-full flex-col gap-5">
      <StepHeader step={step} onBack={() => setStep(step - 1)} />
      <h1 className="font-display text-[2.2rem] leading-[0.95]">{TITLES[step - 1]}</h1>

      {step === 1 && (
        <div className="grid gap-2.5">
          {SPLIT_CHOICES.map((choice) => (
            <Tile
              key={choice.id}
              selected={splitType === choice.id}
              onClick={() => setSplitType(choice.id)}
              testId={`split-${choice.id}`}
              sizeClassName="min-h-[70px] px-4 text-left text-[1.15rem]"
            >
              {choice.name}
            </Tile>
          ))}
        </div>
      )}

      {step === 2 && (
        <EquipmentTiles
          equipment={equipment}
          selected={selectedEquipment}
          onToggle={(id) => setSelectedEquipment(toggleId(selectedEquipment, id))}
          testIdPrefix="equipment-"
        />
      )}

      {step === 3 && (
        <div className="grid grid-cols-2 gap-2.5">
          {UNIT_CHOICES.map((choice) => (
            <Tile
              key={choice}
              selected={units === choice}
              onClick={() => setUnits(choice)}
              testId={`units-${choice}`}
              sizeClassName="min-h-[150px]"
            >
              <span className={`font-display text-5xl font-normal ${units === choice ? "text-acc" : ""}`}>{choice}</span>
            </Tile>
          ))}
        </div>
      )}

      {/* Pushes the button to the bottom of the screen. */}
      <div className="flex-1" />
      <ErrorLine error={error} />
      <PennantButton
        onClick={step < STEPS ? () => setStep(step + 1) : finish}
        disabled={busy}
        testId="onboarding-next"
      >
        {step < STEPS ? "Next" : "Start training"}
      </PennantButton>
    </div>
  );
}

// Back arrow, three progress bars and the step number.
function StepHeader({ step, onBack }: { step: number; onBack: () => void }) {
  return (
    <div className="flex min-h-11 items-center gap-2.5">
      {/* On the first step there's nowhere to go back to, so an empty box keeps the bars in place. */}
      {step > 1 ? (
        <button
          type="button"
          onClick={onBack}
          aria-label="Back"
          data-testid="onboarding-back"
          className="grid size-11 shrink-0 place-items-center text-2xl"
        >
          ‹
        </button>
      ) : (
        <span className="size-11 shrink-0" />
      )}
      <div className="grid flex-1 grid-cols-3 gap-1.5" aria-hidden="true">
        {Array.from({ length: STEPS }, (_, index) => (
          <i key={index} className={`h-1 ${index < step ? "bg-acc" : "bg-edge"}`} />
        ))}
      </div>
      <span className="w-11 shrink-0 text-center font-display text-sm text-mut" aria-label={`Step ${step} of ${STEPS}`}>
        <span data-testid="onboarding-step">{step}</span>/{STEPS}
      </span>
    </div>
  );
}
