"use client"; // every tap saves right away, so this runs in the browser

import { useState } from "react";
import Link from "next/link";
import EquipmentTiles from "@/components/EquipmentTiles";
import ErrorLine from "@/components/ErrorLine";
import SavedNote from "@/components/SavedNote";
import Segmented from "@/components/Segmented";
import Tile from "@/components/Tile";
import { logout, saveSettings } from "@/lib/api";
import { SPLIT_CHOICES, UNIT_CHOICES, toggleId } from "@/lib/choices";
import type { ApiError, Equipment, Settings, SettingsUpdate } from "@/lib/types";
import { useFlash } from "@/lib/useFlash";

type SettingsFormProps = {
  initialSettings: Settings;
  equipment: Equipment[] | null; // null = the list couldn't be loaded
};

type Change = Partial<Pick<Settings, "splitType" | "units" | "equipment">>;

export default function SettingsForm({ initialSettings, equipment }: SettingsFormProps) {
  const [settings, setSettings] = useState(initialSettings);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [savedVisible, flashSaved] = useFlash();

  // The API replaces all settings at once, so each tap sends the full current values with one change.
  async function save(change: Change) {
    const previous = settings;
    const next = { ...settings, ...change };
    setSettings(next); // show the tap at once; undone below if the save fails
    setBusy(true); // one save at a time, so replies can't arrive out of order
    setError(null);
    const result = await saveSettings(toUpdate(next));
    setBusy(false);
    if (!result.ok) {
      setSettings(previous);
      setError(result.error);
      return;
    }
    setSettings(result.data);
    flashSaved();
  }

  async function handleLogout() {
    setBusy(true);
    const result = await logout();
    if (!result.ok) {
      setBusy(false);
      setError(result.error);
      return;
    }
    // A full page load on purpose, not router.replace(): Next.js keeps recently visited pages alive
    // in the background (to restore them on Back), and a reload clears those logged-in pages.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/login");
  }

  return (
    <div className="grid gap-6">
      <header className="flex items-baseline justify-between">
        <h1 className="font-display text-[2.2rem] leading-none">Settings</h1>
        <SavedNote visible={savedVisible} testId="settings-saved" />
      </header>

      <Section label="Split">
        <div className="grid grid-cols-3 gap-2">
          {SPLIT_CHOICES.map((choice) => (
            <Tile
              key={choice.id}
              selected={settings.splitType === choice.id}
              onClick={() => save({ splitType: choice.id })}
              testId={`settings-split-${choice.id}`}
              disabled={busy}
            >
              {choice.name}
            </Tile>
          ))}
        </div>
      </Section>

      <Section label="Equipment">
        <EquipmentTiles
          equipment={equipment}
          selected={settings.equipment}
          onToggle={(id) => save({ equipment: toggleId(settings.equipment, id) })}
          testIdPrefix="settings-equipment-"
          disabled={busy}
        />
      </Section>

      <div className="flex min-h-11 items-center justify-between">
        <span className="text-[0.8rem] tracking-[0.12em] text-mut">Units</span>
        <Segmented
          options={UNIT_CHOICES}
          value={settings.units}
          onChange={(units) => save({ units })}
          testIdPrefix="settings-units-"
          label="Units"
          disabled={busy}
        />
      </div>

      <Link
        href="/settings/targets"
        data-testid="settings-targets-link"
        className="flex min-h-[58px] items-center justify-between border-y border-line font-bold"
      >
        Advanced: weekly targets
        <span aria-hidden="true" className="text-2xl text-mut">
          ›
        </span>
      </Link>

      <ErrorLine error={error} />

      <button
        type="button"
        onClick={handleLogout}
        disabled={busy}
        data-testid="settings-logout"
        className="min-h-[52px] w-full border-[1.5px] border-edge font-bold"
      >
        Log out
      </button>
    </div>
  );
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <section className="grid gap-2.5">
      <h2 className="text-[0.8rem] font-normal tracking-[0.12em] text-mut">{label}</h2>
      {children}
    </section>
  );
}

// The request body for the current settings.
function toUpdate(settings: Settings): SettingsUpdate {
  return {
    // An onboarded user always has a split type; the fallback only satisfies the type checker.
    splitType: settings.splitType ?? "PPL",
    units: settings.units,
    timezone: settings.timezone,
    equipment: settings.equipment,
    // Only read by the API for a CUSTOM split; sending the current days keeps such a split unchanged.
    splitDays: settings.splitDays.map((day) => ({ name: day.name, regions: day.regions })),
  };
}
