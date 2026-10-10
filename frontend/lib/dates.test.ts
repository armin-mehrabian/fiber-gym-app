import { describe, expect, it } from "vitest";
import { formatElapsed, formatSessionDate, formatSessionDay, formatWeekOf } from "./dates";

describe("formatElapsed", () => {
  it("shows minutes and seconds under an hour", () => {
    expect(formatElapsed(0)).toBe("0:00");
    expect(formatElapsed(5_000)).toBe("0:05");
    expect(formatElapsed((12 * 60 + 41) * 1000)).toBe("12:41");
  });

  it("adds hours after an hour", () => {
    expect(formatElapsed((3600 + 2 * 60 + 5) * 1000)).toBe("1:02:05");
  });

  it("drops partial seconds", () => {
    expect(formatElapsed(59_999)).toBe("0:59");
  });

  it("never goes negative", () => {
    expect(formatElapsed(-3000)).toBe("0:00");
  });
});

describe("formatWeekOf", () => {
  it("reads the date as written, without timezone shifts", () => {
    expect(formatWeekOf("2026-10-05")).toBe("Week of 5 Oct");
    expect(formatWeekOf("2026-12-28")).toBe("Week of 28 Dec");
    expect(formatWeekOf("2027-01-04")).toBe("Week of 4 Jan");
  });
});

describe("session day", () => {
  // 03:00 UTC on Monday 5 Oct is still Sunday evening in Vancouver.
  const startedAt = "2026-10-05T03:00:00Z";

  it("uses the user's timezone", () => {
    expect(formatSessionDay(startedAt, "Europe/Berlin")).toBe("Mon 5");
    expect(formatSessionDay(startedAt, "America/Vancouver")).toBe("Sun 4");
  });

  it("adds the month for the detail header", () => {
    expect(formatSessionDate(startedAt, "Europe/Berlin")).toBe("Mon 5 Oct");
  });
});
