import { describe, expect, it } from "vitest";
import { stageFor } from "./stage";

describe("stageFor", () => {
  it("sends visitors who aren't logged in to /login", () => {
    expect(stageFor(null)).toBe("/login");
  });

  it("sends new users to /onboarding", () => {
    expect(stageFor({ id: "1", email: "a@b.co", onboarded: false })).toBe("/onboarding");
  });

  it("sends set-up users to the app", () => {
    expect(stageFor({ id: "1", email: "a@b.co", onboarded: true })).toBe("/");
  });
});
