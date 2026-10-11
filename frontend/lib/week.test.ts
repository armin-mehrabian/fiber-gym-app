import { describe, expect, it } from "vitest";
import { buildDayStrip, dateInZone } from "./week";

describe("dateInZone", () => {
  // 03:00 UTC on Monday 5 Oct is still Sunday evening in Vancouver.
  const moment = new Date("2026-10-05T03:00:00Z");

  it("uses the user's timezone", () => {
    expect(dateInZone(moment, "UTC")).toBe("2026-10-05");
    expect(dateInZone(moment, "Europe/Berlin")).toBe("2026-10-05");
    expect(dateInZone(moment, "America/Vancouver")).toBe("2026-10-04");
  });
});

describe("buildDayStrip", () => {
  const days = [
    { date: "2026-10-05", sessions: 1 },
    { date: "2026-10-06", sessions: 0 },
    { date: "2026-10-07", sessions: 2 },
    { date: "2026-10-08", sessions: 0 },
    { date: "2026-10-09", sessions: 0 },
    { date: "2026-10-10", sessions: 0 },
    { date: "2026-10-11", sessions: 0 },
  ];

  it("labels Monday to Sunday with the day of the month", () => {
    const strip = buildDayStrip(days, "2026-10-09");
    expect(strip.map((d) => d.letter).join("")).toBe("MTWTFSS");
    expect(strip.map((d) => d.dayNumber)).toEqual([5, 6, 7, 8, 9, 10, 11]);
  });

  it("marks trained days and today", () => {
    const strip = buildDayStrip(days, "2026-10-09");
    expect(strip.filter((d) => d.trained).map((d) => d.date)).toEqual(["2026-10-05", "2026-10-07"]);
    expect(strip.filter((d) => d.today).map((d) => d.date)).toEqual(["2026-10-09"]);
    expect(strip[0].label).toBe("Monday 5, trained");
    expect(strip[1].label).toBe("Tuesday 6");
  });

  it("marks no day as today when today is outside the week", () => {
    expect(buildDayStrip(days, "2026-10-12").some((d) => d.today)).toBe(false);
  });

  it("finds today across a timezone date change", () => {
    // Sunday evening in Vancouver = Monday morning UTC: the strip must highlight Sunday's week day.
    const today = dateInZone(new Date("2026-10-12T03:00:00Z"), "America/Vancouver");
    expect(buildDayStrip(days, today).find((d) => d.today)?.date).toBe("2026-10-11");
  });
});
