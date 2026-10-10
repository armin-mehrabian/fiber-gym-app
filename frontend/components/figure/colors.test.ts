import { describe, expect, it } from "vitest";
import { FIGURE_COLORS, fillFor } from "./colors";

describe("fillFor", () => {
  it("uses the untrained colour at 0", () => {
    expect(fillFor(0)).toBe(FIGURE_COLORS.untrained);
  });

  it("starts the scale at the first stop just above 0", () => {
    expect(fillFor(0.0001)).toBe("rgb(67,80,42)"); // #43502a
  });

  it("hits the middle stops exactly", () => {
    expect(fillFor(1 / 3)).toBe("rgb(165,132,31)"); // #a5841f
    expect(fillFor(2 / 3)).toBe("rgb(243,182,43)"); // #f3b62b
  });

  it("ends at the top colour at 1 and stays there above 1", () => {
    expect(fillFor(1)).toBe("rgb(255,230,160)"); // #ffe6a0
    expect(fillFor(1.5)).toBe("rgb(255,230,160)");
  });
});
