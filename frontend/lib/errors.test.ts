import { describe, expect, it } from "vitest";
import { leftoverFieldMessages } from "./errors";

const error = {
  status: 400,
  error: "Invalid input",
  fields: { email: "must be a valid email address", timezone: "is required" },
};

describe("leftoverFieldMessages", () => {
  it("skips fields that are shown next to their input", () => {
    expect(leftoverFieldMessages(error, ["email", "password"])).toEqual(["timezone: is required"]);
  });

  it("lists every field when no inputs show them", () => {
    expect(leftoverFieldMessages(error, [])).toEqual([
      "email: must be a valid email address",
      "timezone: is required",
    ]);
  });

  it("returns nothing when there are no field messages", () => {
    expect(leftoverFieldMessages({ status: 401, error: "Wrong email or password", fields: {} }, [])).toEqual([]);
  });
});
