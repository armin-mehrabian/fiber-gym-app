import { describe, expect, it } from "vitest";
import { toggleId } from "./choices";

describe("toggleId", () => {
  it("adds a missing id at the end", () => {
    expect(toggleId(["barbell"], "bench")).toEqual(["barbell", "bench"]);
  });

  it("removes an id that is there", () => {
    expect(toggleId(["barbell", "bench"], "barbell")).toEqual(["bench"]);
  });

  it("does not change the list it was given", () => {
    const ids = ["barbell"];
    toggleId(ids, "bench");
    expect(ids).toEqual(["barbell"]);
  });
});
