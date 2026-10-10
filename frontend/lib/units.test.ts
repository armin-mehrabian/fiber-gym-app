import { describe, expect, it } from "vitest";
import {
  WEIGHT_STEP,
  firstSetWeight,
  formatWeight,
  kgToLb,
  lbToKg,
  maxWeight,
  stepValue,
  toDisplayWeight,
  toKg,
} from "./units";

describe("kg ↔ lb", () => {
  it("converts with 2 decimals", () => {
    expect(kgToLb(20)).toBe(44.09);
    expect(kgToLb(100)).toBe(220.46);
    expect(lbToKg(45)).toBe(20.41);
    expect(lbToKg(225)).toBe(102.06);
  });

  it("keeps 0 as 0 (bodyweight)", () => {
    expect(kgToLb(0)).toBe(0);
    expect(lbToKg(0)).toBe(0);
  });
});

describe("toDisplayWeight / toKg", () => {
  it("leaves kg alone for KG users", () => {
    expect(toDisplayWeight(22.5, "KG")).toBe(22.5);
    expect(toKg(22.5, "KG")).toBe(22.5);
  });

  it("converts for LB users", () => {
    expect(toDisplayWeight(20.41, "LB")).toBe(45);
    expect(toKg(45, "LB")).toBe(20.41);
  });

  it("rounds kg input to 2 decimals", () => {
    expect(toKg(0.1 + 0.2, "KG")).toBe(0.3);
  });
});

describe("formatWeight", () => {
  it("drops a trailing .0", () => {
    expect(formatWeight(45)).toBe("45");
    expect(formatWeight(20.0)).toBe("20");
    expect(formatWeight(0)).toBe("0");
  });

  it("keeps a real half", () => {
    expect(formatWeight(22.5)).toBe("22.5");
  });

  it("hides the round-trip noise of lb → kg → lb", () => {
    // 55 lb is stored as 24.95 kg, which converts back to 55.01 lb.
    expect(formatWeight(toDisplayWeight(toKg(55, "LB"), "LB"))).toBe("55");
  });
});

describe("steps and limits", () => {
  it("uses 2.5 kg or 5 lb per tap", () => {
    expect(WEIGHT_STEP.KG).toBe(2.5);
    expect(WEIGHT_STEP.LB).toBe(5);
  });

  it("caps at 1000 kg in either unit", () => {
    expect(maxWeight("KG")).toBe(1000);
    expect(maxWeight("LB")).toBe(2204.62);
  });
});

describe("firstSetWeight", () => {
  it("starts at an empty bar", () => {
    expect(firstSetWeight("KG", false)).toBe(20);
    expect(firstSetWeight("LB", false)).toBe(45);
  });

  it("starts at 0 for bodyweight exercises", () => {
    expect(firstSetWeight("KG", true)).toBe(0);
    expect(firstSetWeight("LB", true)).toBe(0);
  });
});

describe("stepValue", () => {
  it("adds and subtracts the step", () => {
    expect(stepValue(20, 2.5, 0, 1000)).toBe(22.5);
    expect(stepValue(45, -5, 0, 2204.62)).toBe(40);
  });

  it("removes float noise", () => {
    expect(stepValue(0.1, 0.2, 0, 10)).toBe(0.3);
  });

  it("stays within min and max", () => {
    expect(stepValue(2.5, -5, 0, 100)).toBe(0);
    expect(stepValue(99, 2.5, 0, 100)).toBe(100);
  });
});
