import { describe, expect, it } from "vitest";
import { alternativesNotInWorkout, planFromExercises, swapPreviewPlan, toRegionValues } from "./plan";

const bench = { position: 1, primary: ["chest_mid"], secondary: ["delt_front", "triceps_lateral"] };
const press = { position: 2, primary: ["delt_front"], secondary: ["triceps_lateral"] };
const row = { primary: ["lats_upper"], secondary: ["biceps_long"] };

describe("toRegionValues", () => {
  it("keeps known regions and drops unknown ids", () => {
    expect(toRegionValues({ chest_mid: 1, delt_front: 0.5, nope: 1 })).toEqual({ chest_mid: 1, delt_front: 0.5 });
  });
});

describe("planFromExercises", () => {
  it("is empty without exercises", () => {
    expect(planFromExercises([])).toEqual({});
  });

  it("marks main regions 1 and helping regions 0.5", () => {
    expect(planFromExercises([bench])).toEqual({ chest_mid: 1, delt_front: 0.5, triceps_lateral: 0.5 });
  });

  it("lets a main region of one exercise win over a helping region of another, in any order", () => {
    const expected = { chest_mid: 1, delt_front: 1, triceps_lateral: 0.5 };
    expect(planFromExercises([bench, press])).toEqual(expected);
    expect(planFromExercises([press, bench])).toEqual(expected);
  });
});

describe("swapPreviewPlan", () => {
  it("is the current plan while nothing is picked", () => {
    expect(swapPreviewPlan([bench, press], 1, null)).toEqual(planFromExercises([bench, press]));
  });

  it("replaces only the exercise at that position", () => {
    expect(swapPreviewPlan([bench, press], 1, row)).toEqual({
      lats_upper: 1,
      biceps_long: 0.5,
      delt_front: 1,
      triceps_lateral: 0.5,
    });
  });
});

describe("alternativesNotInWorkout", () => {
  const workout = [{ exerciseId: "bench_press" }, { exerciseId: "ohp" }];

  it("drops options already in the workout and keeps the order of the rest", () => {
    const options = [{ exerciseId: "ohp" }, { exerciseId: "dips" }, { exerciseId: "bench_press" }, { exerciseId: "fly" }];
    expect(alternativesNotInWorkout(options, workout)).toEqual([{ exerciseId: "dips" }, { exerciseId: "fly" }]);
  });

  it("is empty when every option is already in the workout", () => {
    expect(alternativesNotInWorkout([{ exerciseId: "ohp" }], workout)).toEqual([]);
  });
});
