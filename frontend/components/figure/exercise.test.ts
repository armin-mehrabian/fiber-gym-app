import { describe, expect, it } from "vitest";
import { exerciseValues, viewOf } from "./exercise";

describe("viewOf", () => {
  it("uses the side the region is drawn on", () => {
    expect(viewOf("chest_mid")).toBe("front");
    expect(viewOf("lats_upper")).toBe("back");
  });

  it("uses the front for regions drawn on both sides", () => {
    expect(viewOf("delt_side")).toBe("front");
    expect(viewOf("calves")).toBe("front");
  });
});

describe("exerciseValues", () => {
  it("lights main regions fully and helping regions half", () => {
    expect(exerciseValues(["chest_mid"], ["delt_front", "triceps_lateral"])).toEqual({
      chest_mid: 1,
      delt_front: 0.5,
      triceps_lateral: 0.5,
    });
  });

  it("lets a main region win if it is listed twice, and skips unknown ids", () => {
    expect(exerciseValues(["chest_mid", "nope"], ["chest_mid"])).toEqual({ chest_mid: 1 });
  });
});
